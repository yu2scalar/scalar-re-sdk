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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scalar.re.sdk.model.ReCompletedTarget;
import com.scalar.re.sdk.model.ReCompletedTargetArray;
import com.scalar.re.sdk.model.ReCompletedTargetQueue;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses the JSON value of {@code re_completed.target} into a typed {@link ReCompletedTarget}.
 *
 * <p>The parser auto-detects shape from the JSON structure:
 * <ul>
 *   <li>JSON array → {@link ReCompletedTargetArray}</li>
 *   <li>JSON object with {@code "queue"} key → {@link ReCompletedTargetQueue}</li>
 * </ul>
 *
 * <p>The {@code step object} shape ({@code {"step":k,"to":[...]}}) was removed in Cluster L;
 * such JSON is no longer recognized and triggers an {@link IllegalArgumentException}.
 */
public final class ReCompletedTargetParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ReCompletedTargetParser() {}

    /**
     * Parse a target JSON string into the corresponding typed value.
     *
     * @throws JsonProcessingException if the JSON is malformed
     * @throws IllegalArgumentException if the shape cannot be recognized
     */
    public static ReCompletedTarget parse(String json) throws JsonProcessingException {
        JsonNode node = MAPPER.readTree(json);

        if (node.isArray()) {
            return new ReCompletedTargetArray(extractStringArray(node));
        }

        if (node.isObject()) {
            if (node.has("queue")) {
                List<String> destinations = extractStringArray(node.get("queue"));
                return new ReCompletedTargetQueue(destinations);
            }
        }

        throw new IllegalArgumentException(
                "Unrecognized target shape; expected array or object with 'queue'. JSON: " + json);
    }

    private static List<String> extractStringArray(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            throw new IllegalArgumentException(
                    "Expected JSON array, got " + (arrayNode == null ? "null" : arrayNode.getNodeType()));
        }
        List<String> out = new ArrayList<>(arrayNode.size());
        for (JsonNode element : arrayNode) {
            if (!element.isTextual()) {
                throw new IllegalArgumentException(
                        "Array elements must be strings, got " + element.getNodeType());
            }
            out.add(element.asText());
        }
        return out;
    }
}
