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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scalar.re.sdk.builder.NotifyPayload;
import com.scalar.re.sdk.builder.ReEventBodyBuilder;
import com.scalar.re.sdk.error.ReInputException;
import com.scalar.re.sdk.model.AckStatusEntry;
import com.scalar.re.sdk.model.ReEventBody;
import com.scalar.re.sdk.model.RoutingDestination;
import com.scalar.re.sdk.model.Step;
import com.scalar.re.sdk.parser.ReEventBodyParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ReEventBodyBuilderTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildSimpleSingleMessage() throws Exception {
        JsonNode payload = objectMapper.valueToTree(Map.of("order_id", "12345", "action", "reserve_stock"));
        JsonNode metadata = objectMapper.valueToTree(Map.of("trace_id", "abc-123"));

        String json = ReEventBodyBuilder.create()
                .addStep(step -> step
                        .stepId(1)
                        .addSequence(seq -> seq
                                .seq(1)
                                .routing("inventory_service.re_inbox")
                                .payload(payload)
                                .metadata(metadata)
                        )
                )
                .toJson();

        ReEventBody body = ReEventBodyParser.parse(json);
        assertEquals(1, body.getSteps().size());
        assertEquals(1, body.getSteps().get(0).getSequences().size());

    }

    @Test
    void buildMultiSequenceAtomic() throws Exception {
        String json = ReEventBodyBuilder.create()
                .addStep(step -> step
                        .stepId(1)
                        .addSequence(seq -> seq
                                .seq(1)
                                .routing("inventory_service.re_inbox")
                                .payload(Map.of("order_id", "12345", "items", "A"))
                        )
                        .addSequence(seq -> seq
                                .seq(2)
                                .routing("inventory_service.re_inbox")
                                .payload(Map.of("order_id", "12345", "items", "B"))
                        )
                        .addSequence(seq -> seq
                                .seq(3)
                                .routing("analytics_service.re_inbox")
                                .payload(Map.of("action", "log_order", "order_id", "12345"))
                        )
                )
                .toJson();

        ReEventBody body = ReEventBodyParser.parse(json);
        assertEquals(1, body.getSteps().size());
        assertEquals(3, body.getSteps().get(0).getSequences().size());
    }

    @Test
    void buildMultiStepSaga() throws Exception {
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
        assertEquals(2, body.getSteps().size());
    }

    @Test
    void metadataIsOptional() throws Exception {
        String json = ReEventBodyBuilder.create()
                .addStep(step -> step
                        .stepId(1)

                        .addSequence(seq -> seq
                                .seq(1)
                                .routing("inventory_service.re_inbox")
                                .payload(Map.of("order_id", "12345"))
                        )
                )
                .toJson();

        ReEventBody body = ReEventBodyParser.parse(json);
        assertNull(body.getSteps().get(0).getSequences().get(0).getMetadata());
    }

    @Test
    void deliveryTypeNotInJson() throws Exception {
        String json = ReEventBodyBuilder.create()
                .deliveryType("relay")
                .addStep(step -> step
                        .stepId(1)

                        .addSequence(seq -> seq
                                .seq(1)
                                .routing("inventory_service")
                                .payload(Map.of("order_id", "12345"))
                        )
                )
                .toJson();

        JsonNode node = objectMapper.readTree(json);
        assertFalse(node.has("delivery_type"), "delivery_type should not be in body JSON");
        assertFalse(node.has("event_type"), "event_type should not be in body JSON");
        assertFalse(node.has("source"), "source should not be in body JSON");
        assertTrue(node.has("steps"));
    }

    @Test
    void partialSingleStepAllowed() {
        assertDoesNotThrow(() -> ReEventBodyBuilder.create()
                .deliveryType("partial")
                .addStep(step -> step
                        .stepId(1)

                        .addSequence(seq -> seq
                                .seq(1)
                                .routing("inventory_service")
                                .payload(Map.of("order_id", "12345"))
                        )
                )
                .build());
    }

    @Test
    void partialMultipleStepsRejected() {
        ReInputException ex = assertThrows(ReInputException.class, () ->
                ReEventBodyBuilder.create()
                        .deliveryType("partial")
                        .addStep(step -> step.stepId(1)
                                .addSequence(seq -> seq.seq(1).routing("a").payload(Map.of("k", "v"))))
                        .addStep(step -> step.stepId(2)
                                .addSequence(seq -> seq.seq(1).routing("b").payload(Map.of("k", "v"))))
                        .build());
        assertTrue(ex.getMessage().contains("partial"));
        assertTrue(ex.getMessage().contains("multiple steps"));
    }

    @Test
    void partialDecisionRejected() {
        ReInputException ex = assertThrows(ReInputException.class, () ->
                ReEventBodyBuilder.create()
                        .deliveryType("partial")
                        .addStep(step -> step.stepId(1).decision("ALL_SUCCESS")
                                .addSequence(seq -> seq.seq(1).routing("a").payload(Map.of("k", "v"))))
                        .build());
        assertTrue(ex.getMessage().contains("decision"));
    }

    @Test
    void partialOnFailureRejected() {
        ReInputException ex = assertThrows(ReInputException.class, () ->
                ReEventBodyBuilder.create()
                        .deliveryType("partial")
                        .addStep(step -> step.stepId(1)
                                .onFailure(List.of("compensation_service"))
                                .addSequence(seq -> seq.seq(1).routing("a").payload(Map.of("k", "v"))))
                        .build());
        assertTrue(ex.getMessage().contains("on_failure"));
    }

    @Test
    void pullSingleStepWithoutRoutingAllowed() {
        assertDoesNotThrow(() -> ReEventBodyBuilder.create()
                .deliveryType("pull")
                .addStep(step -> step
                        .stepId(1)

                        .addSequence(seq -> seq
                                .seq(1)
                                // no routing(...) — pull delivers to the EventType config destination
                                .payload(Map.of("order_id", "12345"))
                        )
                )
                .build());
    }

    @Test
    void pullWithRoutingRejected() {
        ReInputException ex = assertThrows(ReInputException.class, () ->
                ReEventBodyBuilder.create()
                        .deliveryType("pull")
                        .addStep(step -> step.stepId(1)
                                .addSequence(seq -> seq.seq(1).routing("ns_postgres")
                                        .payload(Map.of("k", "v"))))
                        .build());
        assertTrue(ex.getMessage().contains("pull"));
        assertTrue(ex.getMessage().contains("does not accept body routing"));
    }

    @Test
    void pullWithMultipleRoutingsRejected() {
        ReInputException ex = assertThrows(ReInputException.class, () ->
                ReEventBodyBuilder.create()
                        .deliveryType("pull")
                        .addStep(step -> step.stepId(1)
                                .addSequence(seq -> seq.seq(1).routing("ns_postgres", "ns_dynamo")
                                        .payload(Map.of("k", "v"))))
                        .build());
        assertTrue(ex.getMessage().contains("does not accept body routing"));
    }

    @Test
    void pullMultipleStepsRejected() {
        ReInputException ex = assertThrows(ReInputException.class, () ->
                ReEventBodyBuilder.create()
                        .deliveryType("pull")
                        .addStep(step -> step.stepId(1)
                                .addSequence(seq -> seq.seq(1).payload(Map.of("k", "v"))))
                        .addStep(step -> step.stepId(2)
                                .addSequence(seq -> seq.seq(1).payload(Map.of("k", "v"))))
                        .build());
        assertTrue(ex.getMessage().contains("pull"));
        assertTrue(ex.getMessage().contains("multiple steps"));
    }

    @Test
    void pullDecisionRejected() {
        ReInputException ex = assertThrows(ReInputException.class, () ->
                ReEventBodyBuilder.create()
                        .deliveryType("pull")
                        .addStep(step -> step.stepId(1).decision("ALL_SUCCESS")
                                .addSequence(seq -> seq.seq(1).payload(Map.of("k", "v"))))
                        .build());
        assertTrue(ex.getMessage().contains("decision"));
    }

    @Test
    void unknownDeliveryTypeRejected() {
        ReInputException ex = assertThrows(ReInputException.class, () ->
                ReEventBodyBuilder.create()
                        .deliveryType("unknown")
                        .addStep(step -> step.stepId(1)
                                .addSequence(seq -> seq.seq(1).routing("a").payload(Map.of("k", "v"))))
                        .build());
        assertTrue(ex.getMessage().contains("Unknown delivery type"));
    }

    @Test
    void toNotifyPayloadSingleRouting() throws Exception {
        NotifyPayload payload = ReEventBodyBuilder.create()
                .deliveryType("atomic")
                .addStep(step -> step.stepId(1)
                        .addSequence(seq -> seq.seq(1).routing("ns_postgres")
                                .payload(Map.of("order_id", "123"))))
                .toNotifyPayload();

        assertNotNull(payload.body());
        assertEquals(Set.of("ns_postgres"), payload.routingNamespaces());

        // Verify body is valid JSON
        ReEventBody parsed = ReEventBodyParser.parse(payload.body());
        assertNotNull(parsed.getSteps());
    }

    @Test
    void toNotifyPayloadMultiRouting() throws Exception {
        NotifyPayload payload = ReEventBodyBuilder.create()
                .deliveryType("atomic")
                .addStep(step -> step.stepId(1)
                        .addSequence(seq -> seq.seq(1).routing("ns_postgres")
                                .payload(Map.of("k", "v")))
                        .addSequence(seq -> seq.seq(2).routing("ns_dynamo")
                                .payload(Map.of("k", "v")))
                        .addSequence(seq -> seq.seq(3).routing("ns_postgres")
                                .payload(Map.of("k", "v"))))
                .toNotifyPayload();

        // ns_postgres appears twice but should be deduplicated
        assertEquals(Set.of("ns_postgres", "ns_dynamo"), payload.routingNamespaces());
    }

    @Test
    void toNotifyPayloadRelayFirstStepOnly() throws Exception {
        NotifyPayload payload = ReEventBodyBuilder.create()
                .deliveryType("relay")
                .addStep(step -> step.stepId(1).decision("ALL_SUCCESS")
                        .addSequence(seq -> seq.seq(1)
                                .routing(new RoutingDestination("ns_postgres", true))
                                .payload(Map.of("k", "v"))))
                .addStep(step -> step.stepId(2).decision("ALL_SUCCESS")
                        .onFailure(List.of("ns_postgres"))
                        .addSequence(seq -> seq.seq(1)
                                .routing(new RoutingDestination("ns_dynamo", true))
                                .payload(Map.of("k", "v"))))
                .toNotifyPayload();

        // Only first step's routing should be extracted
        assertEquals(Set.of("ns_postgres"), payload.routingNamespaces());
    }

    @Test
    void getFirstStepRoutingNamespacesEmpty() {
        ReEventBody body = new ReEventBody(List.of());
        assertEquals(Set.of(), body.getFirstStepRoutingNamespaces());
    }

    @Test
    void spullWithoutRoutingAllowed() {
        assertDoesNotThrow(() -> ReEventBodyBuilder.create()
                .deliveryType("spull")
                .addStep(step -> step.stepId(1)
                        .addSequence(seq -> seq.seq(1)
                                // no routing(...) — SPull resolves by subscription cache
                                .payload(Map.of("order_id", "123"))))
                .build());
    }

    @Test
    void spullWithRoutingRejected() {
        ReInputException ex = assertThrows(ReInputException.class, () ->
                ReEventBodyBuilder.create()
                        .deliveryType("spull")
                        .addStep(step -> step.stepId(1)
                                .addSequence(seq -> seq.seq(1).routing("ns_postgres")
                                        .payload(Map.of("k", "v"))))
                        .build());
        assertTrue(ex.getMessage().contains("spull"));
        assertTrue(ex.getMessage().contains("does not accept body routing"));
    }

    @Test
    void spullWithMultipleRoutingsRejected() {
        ReInputException ex = assertThrows(ReInputException.class, () ->
                ReEventBodyBuilder.create()
                        .deliveryType("spull")
                        .addStep(step -> step.stepId(1)
                                .addSequence(seq -> seq.seq(1).routing("ns_postgres", "ns_dynamo")
                                        .payload(Map.of("k", "v"))))
                        .build());
        assertTrue(ex.getMessage().contains("spull"));
        assertTrue(ex.getMessage().contains("2 destination(s)"));
    }

    @Test
    void relayMultiStepAllowed() {
        assertDoesNotThrow(() -> ReEventBodyBuilder.create()
                .deliveryType("relay")
                .addStep(step -> step.stepId(1).decision("ALL_SUCCESS")
                        .addSequence(seq -> seq.seq(1).routing("a").payload(Map.of("k", "v"))))
                .addStep(step -> step.stepId(2).decision("ALL_SUCCESS")
                        .onFailure(List.of("a"))
                        .addSequence(seq -> seq.seq(1).routing("b").payload(Map.of("k", "v"))))
                .build());
    }

    // §1.17 whitelist: atomic accepts only common fields — relay-only fields are now rejected
    // (previously atomic shared an empty switch case with relay and let them through silently).
    @Test
    void atomicDecisionRejected() {
        ReInputException ex = assertThrows(ReInputException.class, () ->
                ReEventBodyBuilder.create()
                        .deliveryType("atomic")
                        .addStep(step -> step.stepId(1).decision("ALL_SUCCESS")
                                .addSequence(seq -> seq.seq(1).routing("a").payload(Map.of("k", "v"))))
                        .build());
        assertTrue(ex.getMessage().contains("atomic"));
        assertTrue(ex.getMessage().contains("decision"));
    }

    @Test
    void atomicOnFailureRejected() {
        ReInputException ex = assertThrows(ReInputException.class, () ->
                ReEventBodyBuilder.create()
                        .deliveryType("atomic")
                        .addStep(step -> step.stepId(1)
                                .onFailure(List.of("compensation_service"))
                                .addSequence(seq -> seq.seq(1).routing("a").payload(Map.of("k", "v"))))
                        .build());
        assertTrue(ex.getMessage().contains("on_failure"));
    }

    // §1.17 universal-reject: ack_status is ScalarRE-managed and must not be set by the producer,
    // regardless of delivery type.
    @Test
    void ackStatusRejectedUniversally() {
        for (String type : List.of("atomic", "relay", "partial", "pull", "qpull", "spull")) {
            // validateNoReservedFields runs before the per-type switch, so an empty sequence list
            // is fine: the reserved-field check throws first.
            Step stepWithAck = new Step(1, List.of());
            stepWithAck.setAckStatus(Map.of("dest", new AckStatusEntry(true, null, null)));
            ReInputException ex = assertThrows(ReInputException.class, () ->
                    ReEventBodyBuilder.create()
                            .deliveryType(type)
                            .addStep(stepWithAck)
                            .build(),
                    "ack_status should be rejected for delivery type " + type);
            assertTrue(ex.getMessage().contains("ack_status"),
                    "message should mention ack_status for type " + type);
        }
    }

    // §1.17 / Q4 backward-compat: a body persisted before delay_ms was removed still contains
    // "delay_ms" inside each step; the parser must ignore the now-unknown key, not throw.
    @Test
    void legacyDelayMsKeyIsIgnoredOnParse() throws Exception {
        String legacyJson = "{\"steps\":[{\"step_id\":1,\"delay_ms\":0,"
                + "\"sequences\":[{\"seq\":1,\"routing\":[{\"destination\":\"a\",\"ack_required\":false}],"
                + "\"payload\":{\"k\":\"v\"}}]}]}";
        ReEventBody body = assertDoesNotThrow(() -> ReEventBodyParser.parse(legacyJson));
        assertEquals(1, body.getSteps().size());
        assertEquals(1, body.getSteps().get(0).getStepId());
    }

    // -----------------------------------------------------------------------------------------
    // Reserved metadata namespace re.* (data-model §4.5): re.partition / re.ordinal validation.
    // -----------------------------------------------------------------------------------------

    /** Build a body with the given metadata on its single sequence (atomic, one step/seq). */
    private static ReEventBody buildWithMetadata(JsonNode metadata) {
        return ReEventBodyBuilder.create()
                .deliveryType("atomic")
                .addStep(step -> step.stepId(1).addSequence(seq -> seq
                        .seq(1).routing("svc.re_inbox").payload(Map.of("k", "v")).metadata(metadata)))
                .build();
    }

    private static JsonNode reMeta(Object partition, Object ordinal) {
        // valueToTree maps Integer/Long -> numeric nodes, String -> TextNode, Double -> DoubleNode,
        // Boolean -> BooleanNode — matching how a real producer body deserializes (not POJONode).
        var re = objectMapper.createObjectNode();
        if (partition != null) re.set("partition", objectMapper.valueToTree(partition));
        if (ordinal != null) re.set("ordinal", objectMapper.valueToTree(ordinal));
        var root = objectMapper.createObjectNode();
        root.set("re", re);
        return root;
    }

    @Test
    void rePartitionBoundaryAccepted() {
        // boundary: 0 / 9999 (in-range, value-1 / value of the >=10000 reject threshold)
        assertDoesNotThrow(() -> buildWithMetadata(reMeta(0, null)));
        assertDoesNotThrow(() -> buildWithMetadata(reMeta(9999, null)));
    }

    @Test
    void rePartitionOutOfRangeRejected() {
        // 10000 (= MAX_NORMAL_PARTITION + 1, reserved) and -1 (below 0) rejected
        for (int bad : new int[]{10000, -1, 20000, Integer.MIN_VALUE}) {
            ReInputException ex = assertThrows(ReInputException.class,
                    () -> buildWithMetadata(reMeta(bad, null)),
                    "re.partition=" + bad + " should be rejected");
            assertTrue(ex.getMessage().contains("re.partition"),
                    "message should mention re.partition, got: " + ex.getMessage());
        }
    }

    @Test
    void rePartitionNonIntegerRejected() {
        assertThrows(ReInputException.class, () -> buildWithMetadata(reMeta("3", null)));   // string
        assertThrows(ReInputException.class, () -> buildWithMetadata(reMeta(3.5, null)));    // fractional
        assertThrows(ReInputException.class, () -> buildWithMetadata(reMeta(true, null)));   // boolean
    }

    @Test
    void reOrdinalBoundaryAccepted() {
        assertDoesNotThrow(() -> buildWithMetadata(reMeta(null, 0L)));
        assertDoesNotThrow(() -> buildWithMetadata(reMeta(null, 9_999_999_999L)));
    }

    @Test
    void reOrdinalNegativeOrNonIntegerRejected() {
        assertThrows(ReInputException.class, () -> buildWithMetadata(reMeta(null, -1L)));
        assertThrows(ReInputException.class, () -> buildWithMetadata(reMeta(null, "5")));
        assertThrows(ReInputException.class, () -> buildWithMetadata(reMeta(null, 1.5)));
    }

    @Test
    void reAbsentOrNonReMetadataPasses() {
        // no metadata at all
        assertDoesNotThrow(() -> ReEventBodyBuilder.create().deliveryType("atomic")
                .addStep(step -> step.stepId(1).addSequence(seq -> seq.seq(1).routing("a").payload(Map.of("k", "v"))))
                .build());
        // metadata present but no re namespace (user free key only)
        assertDoesNotThrow(() -> buildWithMetadata(objectMapper.valueToTree(Map.of("trace_id", "abc"))));
        // re present but empty object (no partition/ordinal)
        assertDoesNotThrow(() -> buildWithMetadata(reMeta(null, null)));
    }

    @Test
    void reMetadataMustBeObjectNotScalar() {
        JsonNode bad = objectMapper.createObjectNode().put("re", "not-an-object");
        ReInputException ex = assertThrows(ReInputException.class, () -> buildWithMetadata(bad));
        assertTrue(ex.getMessage().contains("re"), "message should mention re");
    }

    @Test
    void rePartitionValidatedForAllDeliveryTypes() {
        // re.partition is delivery-type-agnostic: rejected out-of-range for every type.
        for (String type : List.of("atomic", "relay", "partial", "pull", "qpull", "spull")) {
            assertThrows(ReInputException.class, () ->
                    ReEventBodyBuilder.create().deliveryType(type)
                            .addStep(step -> step.stepId(1).addSequence(seq -> seq
                                    .seq(1).payload(Map.of("k", "v")).metadata(reMeta(10000, null))))
                            .build(),
                    "re.partition=10000 should be rejected for type " + type);
        }
    }

    @Test
    void sequenceBuilderRePartitionAndReOrdinalHelpersWriteNestedReObject() throws Exception {
        String json = ReEventBodyBuilder.create()
                .deliveryType("atomic")
                .addStep(step -> step.stepId(1).addSequence(seq -> seq
                        .seq(1).routing("svc.re_inbox").payload(Map.of("k", "v"))
                        .rePartition(3).reOrdinal(5)))
                .toJson();

        JsonNode root = objectMapper.readTree(json);
        JsonNode meta = root.path("steps").get(0).path("sequences").get(0).path("metadata");
        assertEquals(3, meta.path("re").path("partition").asInt());
        assertEquals(5, meta.path("re").path("ordinal").asLong());
        // round-trips through the parser and re-validates without error
        assertDoesNotThrow(() -> ReEventBodyParser.parse(json));
    }

    @Test
    void sequenceBuilderHelpersPreserveExistingMetadata() throws Exception {
        String json = ReEventBodyBuilder.create()
                .deliveryType("atomic")
                .addStep(step -> step.stepId(1).addSequence(seq -> seq
                        .seq(1).routing("svc.re_inbox").payload(Map.of("k", "v"))
                        .metadata(Map.of("trace_id", "abc"))
                        .rePartition(7)))
                .toJson();

        JsonNode meta = objectMapper.readTree(json)
                .path("steps").get(0).path("sequences").get(0).path("metadata");
        assertEquals("abc", meta.path("trace_id").asText(), "user metadata preserved");
        assertEquals(7, meta.path("re").path("partition").asInt(), "re namespace added alongside");
    }

    @Test
    void sequenceBuilderRePartitionOutOfRangeRejectedAtBuild() {
        // helper writes the value; validation at build() still enforces the range.
        assertThrows(ReInputException.class, () ->
                ReEventBodyBuilder.create().deliveryType("atomic")
                        .addStep(step -> step.stepId(1).addSequence(seq -> seq
                                .seq(1).payload(Map.of("k", "v")).rePartition(10000)))
                        .build());
    }

    // -------------------------------------------------------------------------------------------
    // ordered_atomic contract (delivery-ordered.md D7/D8). Boundary + full-branch coverage of
    // validateOrderedAtomic = validateOrderedCommon (1 step / 1 seq / re.partition+ordinal present
    // / no relay fields) + atomic-base body routing required.
    // -------------------------------------------------------------------------------------------

    /** A valid single-step, single-sequence ordered_atomic body with routing + re.partition/ordinal. */
    private static ReEventBodyBuilder validOrderedAtomic() {
        return ReEventBodyBuilder.create()
                .deliveryType("ordered_atomic")
                .addStep(step -> step.stepId(1).addSequence(seq -> seq
                        .seq(1).routing("svc.re_inbox").payload(Map.of("k", "v"))
                        .rePartition(3).reOrdinal(5)));
    }

    @Test
    void orderedAtomicValidBuilds() {
        assertDoesNotThrow(() -> validOrderedAtomic().build());
    }

    @Test
    void orderedAtomicBroadcastRoutingAllowed() {
        // multiple destinations on one sequence = broadcast (v1, D3).
        assertDoesNotThrow(() -> ReEventBodyBuilder.create()
                .deliveryType("ordered_atomic")
                .addStep(step -> step.stepId(1).addSequence(seq -> seq
                        .seq(1).routing("a.re_inbox", "b.re_inbox").payload(Map.of("k", "v"))
                        .rePartition(0).reOrdinal(0)))
                .build());
    }

    @Test
    void orderedAtomicMultiStepRejected() {
        ReInputException ex = assertThrows(ReInputException.class, () ->
                ReEventBodyBuilder.create().deliveryType("ordered_atomic")
                        .addStep(step -> step.stepId(1).addSequence(seq -> seq
                                .seq(1).routing("a").payload(Map.of("k", "v")).rePartition(1).reOrdinal(1)))
                        .addStep(step -> step.stepId(2).addSequence(seq -> seq
                                .seq(1).routing("a").payload(Map.of("k", "v")).rePartition(1).reOrdinal(2)))
                        .build());
        assertTrue(ex.getMessage().contains("ordered_atomic"));
        assertTrue(ex.getMessage().contains("multiple steps"));
    }

    @Test
    void orderedAtomicMultiSequenceRejected() {
        ReInputException ex = assertThrows(ReInputException.class, () ->
                ReEventBodyBuilder.create().deliveryType("ordered_atomic")
                        .addStep(step -> step.stepId(1)
                                .addSequence(seq -> seq.seq(1).routing("a").payload(Map.of("k", "v"))
                                        .rePartition(1).reOrdinal(1))
                                .addSequence(seq -> seq.seq(2).routing("a").payload(Map.of("k", "v"))
                                        .rePartition(1).reOrdinal(2)))
                        .build());
        assertTrue(ex.getMessage().contains("one sequence"));
    }

    @Test
    void orderedAtomicMissingPartitionRejected() {
        ReInputException ex = assertThrows(ReInputException.class, () ->
                ReEventBodyBuilder.create().deliveryType("ordered_atomic")
                        .addStep(step -> step.stepId(1).addSequence(seq -> seq
                                .seq(1).routing("a").payload(Map.of("k", "v")).reOrdinal(5)))
                        .build());
        assertTrue(ex.getMessage().contains("re.partition"));
    }

    @Test
    void orderedAtomicMissingOrdinalRejected() {
        ReInputException ex = assertThrows(ReInputException.class, () ->
                ReEventBodyBuilder.create().deliveryType("ordered_atomic")
                        .addStep(step -> step.stepId(1).addSequence(seq -> seq
                                .seq(1).routing("a").payload(Map.of("k", "v")).rePartition(3)))
                        .build());
        assertTrue(ex.getMessage().contains("re.ordinal"));
    }

    @Test
    void orderedAtomicMissingRoutingRejected() {
        ReInputException ex = assertThrows(ReInputException.class, () ->
                ReEventBodyBuilder.create().deliveryType("ordered_atomic")
                        .addStep(step -> step.stepId(1).addSequence(seq -> seq
                                .seq(1).payload(Map.of("k", "v")).rePartition(3).reOrdinal(5)))
                        .build());
        assertTrue(ex.getMessage().contains("routing"));
    }

    @Test
    void orderedAtomicPartitionBoundaryAccepted() {
        // re.partition boundary: 0 and 9999 (MAX_NORMAL_PARTITION) are valid.
        assertDoesNotThrow(() -> ReEventBodyBuilder.create().deliveryType("ordered_atomic")
                .addStep(step -> step.stepId(1).addSequence(seq -> seq
                        .seq(1).routing("a").payload(Map.of("k", "v")).rePartition(9999).reOrdinal(0)))
                .build());
    }

    @Test
    void orderedAtomicPartitionReservedRangeRejected() {
        // re.partition 10000 = Replay-reserved → rejected by validateReservedMetadata.
        assertThrows(ReInputException.class, () ->
                ReEventBodyBuilder.create().deliveryType("ordered_atomic")
                        .addStep(step -> step.stepId(1).addSequence(seq -> seq
                                .seq(1).routing("a").payload(Map.of("k", "v")).rePartition(10000).reOrdinal(0)))
                        .build());
    }

    @Test
    void orderedAtomicRelayFieldRejected() {
        ReInputException ex = assertThrows(ReInputException.class, () ->
                ReEventBodyBuilder.create().deliveryType("ordered_atomic")
                        .addStep(step -> step.stepId(1).decision("ALL_SUCCESS").addSequence(seq -> seq
                                .seq(1).routing("a").payload(Map.of("k", "v")).rePartition(3).reOrdinal(5)))
                        .build());
        assertTrue(ex.getMessage().contains("decision"));
    }

    @Test
    void orderedAtomicReOrdinalNotInJsonAsTopLevel() throws Exception {
        // re.ordinal is carried under metadata.re (body transparent), not promoted to a column.
        String json = validOrderedAtomic().toJson();
        JsonNode meta = objectMapper.readTree(json)
                .path("steps").get(0).path("sequences").get(0).path("metadata");
        assertEquals(3, meta.path("re").path("partition").asInt());
        assertEquals(5, meta.path("re").path("ordinal").asInt());
    }
}
