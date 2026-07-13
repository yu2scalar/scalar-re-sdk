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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scalar.re.sdk.model.ReCompletedTarget;
import com.scalar.re.sdk.model.ReCompletedTargetArray;
import com.scalar.re.sdk.model.ReCompletedTargetQueue;

import java.util.Arrays;
import java.util.List;

/**
 * Builds {@link ReCompletedTarget} instances and serializes them to the JSON form
 * stored in the {@code re_completed.target} (TEXT) column.
 *
 * <p>JSON shapes:
 * <ul>
 *   <li>array: {@code ["destA","destB"]}</li>
 *   <li>queue: {@code {"queue":["destA","destB"]}}</li>
 * </ul>
 *
 * <p>The {@code step object} shape was removed in Cluster L — a relay step now writes a plain
 * {@code array} target plus the dedicated {@code step_id} CK column.
 *
 * <p>Destination order is preserved (body order, not canonical sort).
 */
public final class ReCompletedTargetBuilder {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ReCompletedTargetBuilder() {}

    // --- Factory methods (model construction) ---

    /** Build an array shape target. Used by pull / atomic / partial / relay-step-1 / QPull-TX2 / SPull-TX2. */
    public static ReCompletedTargetArray array(String... destinations) {
        return new ReCompletedTargetArray(Arrays.asList(destinations));
    }

    /** Build an array shape target from a List. */
    public static ReCompletedTargetArray array(List<String> destinations) {
        return new ReCompletedTargetArray(destinations);
    }

    /** Build a queue object shape target. Used by QPull TX1 / SPull TX1. */
    public static ReCompletedTargetQueue queue(String... destinations) {
        return new ReCompletedTargetQueue(Arrays.asList(destinations));
    }

    /** Build a queue object shape target from a List. */
    public static ReCompletedTargetQueue queue(List<String> destinations) {
        return new ReCompletedTargetQueue(destinations);
    }

    // --- Serialization to JSON ---

    /** Serialize a target to its canonical JSON representation. */
    public static String toJson(ReCompletedTarget target) {
        try {
            if (target instanceof ReCompletedTargetArray a) {
                return MAPPER.writeValueAsString(a.destinations());
            }
            if (target instanceof ReCompletedTargetQueue q) {
                ObjectNode node = MAPPER.createObjectNode();
                ArrayNode arr = node.putArray("queue");
                q.destinations().forEach(arr::add);
                return MAPPER.writeValueAsString(node);
            }
            throw new IllegalArgumentException("Unknown target type: " + target.getClass());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize target", e);
        }
    }
}
