/*
 * Copyright 2026 Scalar Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.scalar.re.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scalar.re.sdk.builder.ReEventBodyBuilder;
import com.scalar.re.sdk.model.ReEventBody;
import com.scalar.re.sdk.model.RoutingDestination;
import com.scalar.re.sdk.parser.ReEventBodyParser;
import com.scalar.re.sdk.processor.InboxEntry;
import com.scalar.re.sdk.processor.InboxEntryProcessor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InboxEntryProcessorTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void atomicSingleSequence() throws Exception {
        String json = ReEventBodyBuilder.create()
                .addStep(step -> step
                        .stepId(1)
                        .addSequence(seq -> seq
                                .seq(1)
                                .routing("inventory_service.re_inbox")
                                .payload(Map.of("order_id", "12345", "action", "reserve_stock"))
                                .metadata(Map.of("trace_id", "abc-123"))
                        )
                )
                .toJson();

        ReEventBody body = ReEventBodyParser.parse(json);
        List<InboxEntry> entries = InboxEntryProcessor.process(body);

        assertEquals(1, entries.size());
        InboxEntry entry = entries.get(0);
        assertEquals("inventory_service.re_inbox", entry.getRoutingDestination());
        assertEquals(1, entry.getStepId());
        assertEquals(1, entry.getSeq());
        assertFalse(entry.isAckRequired());

        // Verify the re-constructed body
        ReEventBody inboxBody = ReEventBodyParser.parse(entry.getBody());
        assertEquals(1, inboxBody.getSteps().size());
        assertEquals(1, inboxBody.getSteps().get(0).getSequences().size());
    }

    @Test
    void atomicMultiSequenceGroupedByRouting() throws Exception {
        String json = ReEventBodyBuilder.create()
                .addStep(step -> step
                        .stepId(1)
                        .addSequence(seq -> seq
                                .seq(1)
                                .routing("inventory_service.re_inbox")
                                .payload(Map.of("order_id", "12345", "items_sku", "A"))
                        )
                        .addSequence(seq -> seq
                                .seq(2)
                                .routing("inventory_service.re_inbox")
                                .payload(Map.of("order_id", "12345", "items_sku", "B"))
                        )
                        .addSequence(seq -> seq
                                .seq(3)
                                .routing("analytics_service.re_inbox")
                                .payload(Map.of("action", "log_order", "order_id", "12345"))
                        )
                )
                .toJson();

        ReEventBody body = ReEventBodyParser.parse(json);
        List<InboxEntry> entries = InboxEntryProcessor.process(body);

        // Should produce 3 records: seq 1,2 for inventory, seq 3 for analytics
        assertEquals(3, entries.size());

        // inventory_service: step_id=1, seq=1
        InboxEntry inv1 = entries.get(0);
        assertEquals("inventory_service.re_inbox", inv1.getRoutingDestination());
        assertEquals(1, inv1.getStepId());
        assertEquals(1, inv1.getSeq());
        ReEventBody inv1Body = ReEventBodyParser.parse(inv1.getBody());
        assertEquals(1, inv1Body.getSteps().get(0).getSequences().size());

        // inventory_service: step_id=1, seq=2
        InboxEntry inv2 = entries.get(1);
        assertEquals("inventory_service.re_inbox", inv2.getRoutingDestination());
        assertEquals(1, inv2.getStepId());
        assertEquals(2, inv2.getSeq());

        // analytics_service: step_id=1, seq=3
        InboxEntry analyticsEntry = entries.get(2);
        assertEquals("analytics_service.re_inbox", analyticsEntry.getRoutingDestination());
        assertEquals(1, analyticsEntry.getStepId());
        assertEquals(3, analyticsEntry.getSeq());
    }

    @Test
    void multiSequenceSameRouting() throws Exception {
        String json = ReEventBodyBuilder.create()
                .addStep(step -> step
                        .stepId(2)
                        .addSequence(seq -> seq
                                .seq(1)
                                .routing("payment_service.re_inbox")
                                .payload(Map.of("order_id", "12345", "action", "authorize"))
                        )
                        .addSequence(seq -> seq
                                .seq(2)
                                .routing("payment_service.re_inbox")
                                .payload(Map.of("order_id", "12345", "action", "capture"))
                        )
                )
                .toJson();

        ReEventBody body = ReEventBodyParser.parse(json);
        List<InboxEntry> entries = InboxEntryProcessor.process(body);

        // Should produce 2 individual records
        assertEquals(2, entries.size());

        // First: step_id=2, seq=1
        InboxEntry entry1 = entries.get(0);
        assertEquals("payment_service.re_inbox", entry1.getRoutingDestination());
        assertEquals(2, entry1.getStepId());
        assertEquals(1, entry1.getSeq());

        ReEventBody body1 = ReEventBodyParser.parse(entry1.getBody());
        assertEquals(1, body1.getSteps().get(0).getSequences().size());
        assertEquals(1, body1.getSteps().get(0).getSequences().get(0).getSeq());

        // Second: step_id=2, seq=2
        InboxEntry entry2 = entries.get(1);
        assertEquals(2, entry2.getStepId());
        assertEquals(2, entry2.getSeq());
    }

    @Test
    void multiStepProcessing() throws Exception {
        String json = ReEventBodyBuilder.create()
                .addStep(step -> step
                        .stepId(1)
                        .addSequence(seq -> seq
                                .seq(1)
                                .routing("inventory_service.re_inbox")
                                .payload(Map.of("order_id", "12345", "action", "reserve_stock"))
                                .metadata(Map.of("trace_id", "abc-123"))
                        )
                )
                .addStep(step -> step
                        .stepId(2)
                        .addSequence(seq -> seq
                                .seq(1)
                                .routing("payment_service.re_inbox")
                                .payload(Map.of("order_id", "12345", "action", "authorize"))
                                .metadata(Map.of("trace_id", "abc-123"))
                        )
                        .addSequence(seq -> seq
                                .seq(2)
                                .routing("payment_service.re_inbox")
                                .payload(Map.of("order_id", "12345", "action", "capture"))
                                .metadata(Map.of("trace_id", "abc-123"))
                        )
                )
                .toJson();

        ReEventBody body = ReEventBodyParser.parse(json);
        List<InboxEntry> entries = InboxEntryProcessor.process(body);

        // step 1 (atomic): 1 record for inventory (step_id=1, seq=1)
        // step 2 (sequential): 2 records for payment
        assertEquals(3, entries.size());

        // Step 1: atomic -> step_id=1, seq=1
        InboxEntry atomicEntry = entries.get(0);
        assertEquals("inventory_service.re_inbox", atomicEntry.getRoutingDestination());
        assertEquals(1, atomicEntry.getStepId());
        assertEquals(1, atomicEntry.getSeq());

        // Step 2: sequential -> step_id=2, seq=1 and seq=2
        InboxEntry seqEntry1 = entries.get(1);
        assertEquals("payment_service.re_inbox", seqEntry1.getRoutingDestination());
        assertEquals(2, seqEntry1.getStepId());
        assertEquals(1, seqEntry1.getSeq());

        InboxEntry seqEntry2 = entries.get(2);
        assertEquals(2, seqEntry2.getStepId());
        assertEquals(2, seqEntry2.getSeq());
    }

    @Test
    void ackRequiredPropagation() throws Exception {
        String json = ReEventBodyBuilder.create()
                .deliveryType("relay")
                .addStep(step -> step
                        .stepId(1)

                        .addSequence(seq -> seq
                                .seq(1)
                                .routing(
                                        new RoutingDestination("payment_service.re_inbox", true),
                                        new RoutingDestination("analytics_service.re_inbox", false)
                                )
                                .payload(Map.of("order_id", "12345"))
                        )
                )
                .toJson();

        ReEventBody body = ReEventBodyParser.parse(json);
        List<InboxEntry> entries = InboxEntryProcessor.process(body);

        assertEquals(2, entries.size());

        InboxEntry paymentEntry = entries.stream()
                .filter(e -> e.getRoutingDestination().equals("payment_service.re_inbox"))
                .findFirst().orElseThrow();
        assertTrue(paymentEntry.isAckRequired());

        InboxEntry analyticsEntry = entries.stream()
                .filter(e -> e.getRoutingDestination().equals("analytics_service.re_inbox"))
                .findFirst().orElseThrow();
        assertFalse(analyticsEntry.isAckRequired());
    }

    /** A null steps / sequences / routing list yields no entries instead of an NPE (P5, R-02). */
    @Test
    void nullStepsSequencesOrRouting_yieldNoEntries() throws Exception {
        assertTrue(InboxEntryProcessor.process(new ReEventBody(null)).isEmpty());
        List<com.scalar.re.sdk.model.Step> steps = new java.util.ArrayList<>();
        steps.add(new com.scalar.re.sdk.model.Step(1, null));
        steps.add(new com.scalar.re.sdk.model.Step(2, List.of(
                new com.scalar.re.sdk.model.Sequence(1, null, null, null))));
        assertTrue(InboxEntryProcessor.process(new ReEventBody(steps)).isEmpty());
    }
}
