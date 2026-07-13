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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scalar.re.sdk.builder.InboxBodyBuilder;
import com.scalar.re.sdk.builder.ReEventBodyBuilder;
import com.scalar.re.sdk.model.InboxBody;
import com.scalar.re.sdk.model.ReEventBody;
import com.scalar.re.sdk.model.Sequence;
import com.scalar.re.sdk.parser.InboxBodyParser;
import com.scalar.re.sdk.parser.ReEventBodyParser;
import com.scalar.re.sdk.processor.InboxEntry;
import com.scalar.re.sdk.processor.InboxEntryProcessor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InboxBodyTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void roundTripPreservesPayloadAndMetadata() throws Exception {
        JsonNode payload = objectMapper.valueToTree(Map.of("order_id", "12345", "amount", 5000));
        JsonNode metadata = objectMapper.valueToTree(Map.of("trace_id", "abc-123"));
        String json = InboxBodyParser.toJson(new InboxBody(payload, metadata));

        InboxBody parsed = InboxBodyParser.parse(json);
        assertEquals(payload, parsed.getPayload());
        assertEquals(metadata, parsed.getMetadata());
    }

    @Test
    void parseToleratesUnknownFields() throws Exception {
        String json = "{\"payload\":{\"a\":1},\"metadata\":{\"b\":2},\"unknown\":\"x\",\"steps\":[]}";
        InboxBody parsed = InboxBodyParser.parse(json);
        assertEquals(1, parsed.getPayload().get("a").asInt());
        assertEquals(2, parsed.getMetadata().get("b").asInt());
    }

    @Test
    void serializeOmitsNullFields() throws Exception {
        JsonNode payload = objectMapper.valueToTree(Map.of("k", "v"));
        String json = InboxBodyParser.toJson(new InboxBody(payload, null));
        assertTrue(json.contains("payload"));
        assertFalse(json.contains("metadata"), "metadata=null must be omitted: " + json);
    }

    @Test
    void fromSequenceExtractsPayloadAndMetadata() throws Exception {
        JsonNode payload = objectMapper.valueToTree(Map.of("order_id", "X"));
        JsonNode metadata = objectMapper.valueToTree(Map.of("trace_id", "Y"));
        Sequence sequence = new Sequence(1, List.of(), payload, metadata);

        String json = InboxBodyBuilder.fromSequence(sequence);
        InboxBody parsed = InboxBodyParser.parse(json);
        assertEquals(payload, parsed.getPayload());
        assertEquals(metadata, parsed.getMetadata());
        assertFalse(json.contains("seq"), "simplified body must not contain seq: " + json);
        assertFalse(json.contains("routing"), "simplified body must not contain routing: " + json);
    }

    @Test
    void fromSubBodyExtractsFirstSequencePayloadAndMetadata() throws Exception {
        // Mimic InboxEntryProcessor.buildInboxBody output: 1 step + 1 seq full sub-body.
        String fullEventJson = ReEventBodyBuilder.create()
                .addStep(step -> step.stepId(1)
                        .addSequence(seq -> seq.seq(1)
                                .routing("ns_postgres")
                                .payload(Map.of("currency", "JPY", "amount", 5000))
                                .metadata(Map.of("trace_id", "trace-1"))))
                .toJson();
        ReEventBody parsed = ReEventBodyParser.parse(fullEventJson);
        List<InboxEntry> entries = InboxEntryProcessor.process(parsed);
        assertEquals(1, entries.size());
        String subBody = entries.get(0).getBody();

        String simplified = InboxBodyBuilder.fromSubBody(subBody);
        InboxBody body = InboxBodyParser.parse(simplified);
        assertEquals("JPY", body.getPayload().get("currency").asText());
        assertEquals(5000, body.getPayload().get("amount").asInt());
        assertEquals("trace-1", body.getMetadata().get("trace_id").asText());
    }

    @Test
    void fromSubBodyRejectsEmptySteps() {
        String empty = "{\"steps\":[]}";
        assertThrows(IllegalArgumentException.class, () -> InboxBodyBuilder.fromSubBody(empty));
    }

    @Test
    void fromSubBodyRejectsStepWithoutSequences() throws Exception {
        ObjectNode step = objectMapper.createObjectNode();
        step.put("step_id", 1);
        ObjectNode root = objectMapper.createObjectNode();
        root.putArray("steps").add(step);
        String json = objectMapper.writeValueAsString(root);

        assertThrows(IllegalArgumentException.class, () -> InboxBodyBuilder.fromSubBody(json));
    }
}
