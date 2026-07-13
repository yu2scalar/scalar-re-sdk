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
package com.scalar.re.sdk.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.scalar.re.sdk.builder.ReCompletedTargetBuilder;
import com.scalar.re.sdk.model.ReCompletedTarget;
import com.scalar.re.sdk.model.ReCompletedTargetArray;
import com.scalar.re.sdk.model.ReCompletedTargetQueue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReCompletedTargetParserTest {

    @Test
    void parse_array_singleDestination() throws JsonProcessingException {
        ReCompletedTarget t = ReCompletedTargetParser.parse("[\"destA\"]");
        var array = assertInstanceOf(ReCompletedTargetArray.class, t);
        assertEquals(List.of("destA"), array.destinations());
    }

    @Test
    void parse_array_multipleDestinations() throws JsonProcessingException {
        ReCompletedTarget t = ReCompletedTargetParser.parse("[\"destA\",\"destB\",\"destC\"]");
        var array = assertInstanceOf(ReCompletedTargetArray.class, t);
        assertEquals(List.of("destA", "destB", "destC"), array.destinations());
    }

    @Test
    void parse_stepObjectJson_nowRejected_throwsIllegalArgument() {
        // Cluster L removed the step object shape; {"step":k,"to":[...]} is no longer recognized.
        assertThrows(IllegalArgumentException.class,
                () -> ReCompletedTargetParser.parse("{\"step\":2,\"to\":[\"destA\",\"destB\"]}"));
    }

    @Test
    void parse_queue_object() throws JsonProcessingException {
        ReCompletedTarget t = ReCompletedTargetParser.parse("{\"queue\":[\"destA\",\"destB\"]}");
        var queue = assertInstanceOf(ReCompletedTargetQueue.class, t);
        assertEquals(List.of("destA", "destB"), queue.destinations());
    }

    @Test
    void parse_invalidJson_throws() {
        assertThrows(JsonProcessingException.class,
                () -> ReCompletedTargetParser.parse("{bad json"));
    }

    @Test
    void parse_unrecognizedShape_throwsIllegalArgument() {
        // object without step+to or queue
        assertThrows(IllegalArgumentException.class,
                () -> ReCompletedTargetParser.parse("{\"unknown\":\"field\"}"));
    }

    @Test
    void parse_objectWithStepButNoTo_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> ReCompletedTargetParser.parse("{\"step\":2}"));
    }

    @Test
    void parse_arrayWithNonString_throws() {
        // Array elements must be strings
        assertThrows(IllegalArgumentException.class,
                () -> ReCompletedTargetParser.parse("[1, 2, 3]"));
    }

    // Round-trip: builder → parser

    @Test
    void roundTrip_array() throws JsonProcessingException {
        var built = ReCompletedTargetBuilder.array("destA", "destB");
        String json = ReCompletedTargetBuilder.toJson(built);
        ReCompletedTarget parsed = ReCompletedTargetParser.parse(json);
        assertInstanceOf(ReCompletedTargetArray.class, parsed);
        assertEquals(built.destinations(), parsed.destinations());
    }

    @Test
    void roundTrip_queue() throws JsonProcessingException {
        var built = ReCompletedTargetBuilder.queue("subA", "subB", "subC");
        String json = ReCompletedTargetBuilder.toJson(built);
        ReCompletedTarget parsed = ReCompletedTargetParser.parse(json);
        var queue = assertInstanceOf(ReCompletedTargetQueue.class, parsed);
        assertEquals(built.destinations(), queue.destinations());
    }
}
