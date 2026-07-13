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
package com.scalar.re.sdk.builder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scalar.re.sdk.model.ReCompletedTargetArray;
import com.scalar.re.sdk.model.ReCompletedTargetQueue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReCompletedTargetBuilderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void array_singleDestination_serializesAsArray() throws JsonProcessingException {
        var target = ReCompletedTargetBuilder.array("destA");
        String json = ReCompletedTargetBuilder.toJson(target);
        assertEquals("[\"destA\"]", json);
        // Round-trip via JsonNode for shape stability
        JsonNode node = MAPPER.readTree(json);
        assertTrue(node.isArray());
        assertEquals(1, node.size());
    }

    @Test
    void array_multipleDestinations_preservesOrder() throws JsonProcessingException {
        var target = ReCompletedTargetBuilder.array("destA", "destC", "destB");
        String json = ReCompletedTargetBuilder.toJson(target);
        // body order, NOT canonical sort — see §2.2
        assertEquals("[\"destA\",\"destC\",\"destB\"]", json);
    }

    @Test
    void array_fromList() {
        var target = ReCompletedTargetBuilder.array(List.of("destA", "destB"));
        assertEquals(List.of("destA", "destB"), target.destinations());
    }

    @Test
    void queue_serializesAsObjectWithQueue() throws JsonProcessingException {
        var target = ReCompletedTargetBuilder.queue("destA", "destB", "destC");
        String json = ReCompletedTargetBuilder.toJson(target);
        JsonNode node = MAPPER.readTree(json);
        assertTrue(node.isObject());
        assertTrue(node.get("queue").isArray());
        assertEquals(3, node.get("queue").size());
        assertEquals("destA", node.get("queue").get(0).asText());
    }

    @Test
    void queue_fromList() {
        var target = ReCompletedTargetBuilder.queue(List.of("subA", "subB"));
        assertEquals(List.of("subA", "subB"), target.destinations());
    }

    @Test
    void array_returnsImpl_assignableToReCompletedTargetArray() {
        var target = ReCompletedTargetBuilder.array("X");
        assertTrue(target instanceof ReCompletedTargetArray);
    }

    @Test
    void queue_returnsImpl_assignableToReCompletedTargetQueue() {
        var target = ReCompletedTargetBuilder.queue("X");
        assertTrue(target instanceof ReCompletedTargetQueue);
    }
}
