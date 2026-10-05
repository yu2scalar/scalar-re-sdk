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
import java.util.Map;
import com.scalar.re.sdk.model.DeliveryDestinationStatus;
import com.scalar.re.sdk.model.DestinationStatus;
import com.scalar.re.sdk.model.ReEventBody;
import com.scalar.re.sdk.model.ReHoldBody;
import com.scalar.re.sdk.model.ReHoldTrackingDlq;
import com.scalar.re.sdk.model.ReHoldTrackingPending;
import com.scalar.re.sdk.model.ReHoldTrackingRelay;

public class ReEventBodyParser {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static ReEventBody parse(String json) throws JsonProcessingException {
        return objectMapper.readValue(json, ReEventBody.class);
    }

    /**
     * Parse a JSON string into a ReHoldBody.
     */
    public static ReHoldBody parseHoldBody(String json) throws JsonProcessingException {
        return objectMapper.readValue(json, ReHoldBody.class);
    }

    /**
     * Check if the JSON represents a re_hold body: both {@code original_body} and {@code tracking}
     * at the top level (data-model §6.4). A producer body that happens to carry a top-level
     * {@code original_body} key is still an event body.
     */
    public static boolean isHoldBody(String json) throws JsonProcessingException {
        return isHoldNode(objectMapper.readTree(json));
    }

    /**
     * Parse an outbox / hold body JSON once, deciding event body vs hold body with the same rule as
     * {@link #isHoldBody}. The result is passed along instead of re-parsing the JSON.
     */
    public static ParsedBody parseAny(String json) throws JsonProcessingException {
        JsonNode node = objectMapper.readTree(json);
        if (isHoldNode(node)) {
            return ParsedBody.ofHold(objectMapper.treeToValue(node, ReHoldBody.class));
        }
        return ParsedBody.ofEvent(objectMapper.treeToValue(node, ReEventBody.class));
    }

    private static boolean isHoldNode(JsonNode node) {
        return node != null && node.isObject() && node.has("original_body") && node.has("tracking");
    }

    /**
     * Extract typed relay tracking from a ReHoldBody.
     */
    public static ReHoldTrackingRelay extractRelayTracking(ReHoldBody holdBody)
            throws JsonProcessingException {
        if (holdBody.getTracking() == null) {
            return null;
        }
        return objectMapper.treeToValue(holdBody.getTracking(), ReHoldTrackingRelay.class);
    }

    /**
     * Extract typed pending tracking from a ReHoldBody.
     */
    public static ReHoldTrackingPending extractPendingTracking(ReHoldBody holdBody)
            throws JsonProcessingException {
        if (holdBody.getTracking() == null) {
            return null;
        }
        return objectMapper.treeToValue(holdBody.getTracking(), ReHoldTrackingPending.class);
    }

    /**
     * Extract typed DLQ tracking from a ReHoldBody.
     */
    public static ReHoldTrackingDlq extractDlqTracking(ReHoldBody holdBody)
            throws JsonProcessingException {
        if (holdBody.getTracking() == null) {
            return null;
        }
        return objectMapper.treeToValue(holdBody.getTracking(), ReHoldTrackingDlq.class);
    }

    /**
     * Build a ReHoldBody with relay tracking.
     */
    public static ReHoldBody buildRelayHoldBody(ReEventBody originalBody, int currentStep,
            Map<String, DestinationStatus> statusPerDestination) {
        ReHoldTrackingRelay tracking = new ReHoldTrackingRelay(currentStep, statusPerDestination);
        JsonNode trackingNode = objectMapper.valueToTree(tracking);
        return new ReHoldBody(originalBody, trackingNode);
    }

    /**
     * Build a ReHoldBody with DLQ tracking.
     */
    public static ReHoldBody buildDlqHoldBody(ReEventBody originalBody, String errorType,
            String errorMessage, int retryCount, long failedAt) {
        ReHoldTrackingDlq tracking = new ReHoldTrackingDlq(errorType, errorMessage, retryCount, failedAt);
        JsonNode trackingNode = objectMapper.valueToTree(tracking);
        return new ReHoldBody(originalBody, trackingNode);
    }

    /**
     * Build a ReHoldBody with DLQ tracking that preserves relay progress
     * (reached step + per-destination status) for relay-origin DLQ entries
     * (decision NG / ack timeout, recovery.md §7.5.3). Use {@link #buildDlqHoldBody}
     * for push-origin DLQ where no relay progress exists.
     */
    public static ReHoldBody buildRelayDlqHoldBody(ReEventBody originalBody, String errorType,
            String errorMessage, int retryCount, long failedAt,
            Integer reachedStep, Map<String, DestinationStatus> statusPerDestination) {
        ReHoldTrackingDlq tracking = new ReHoldTrackingDlq(
                errorType, errorMessage, retryCount, failedAt, reachedStep, statusPerDestination);
        JsonNode trackingNode = objectMapper.valueToTree(tracking);
        return new ReHoldBody(originalBody, trackingNode);
    }

    /**
     * Build a ReHoldBody with DLQ tracking — the general form every DLQ write uses (recovery.md
     * §7.1, P6): relay progress when there is one, and the unreadable source body as
     * {@code raw_body} when {@code originalBody} could not be built.
     *
     * @param reachedStep          relay progress, or null
     * @param statusPerDestination relay progress, or null
     * @param rawBody              the source body as is when it was not readable JSON, or null
     */
    public static ReHoldBody buildDlqHoldBody(ReEventBody originalBody, String errorType,
            String errorMessage, int retryCount, long failedAt, Integer reachedStep,
            Map<String, DestinationStatus> statusPerDestination, String rawBody) {
        ReHoldTrackingDlq tracking = new ReHoldTrackingDlq(
                errorType, errorMessage, retryCount, failedAt, reachedStep, statusPerDestination);
        tracking.setRawBody(rawBody);
        JsonNode trackingNode = objectMapper.valueToTree(tracking);
        return new ReHoldBody(originalBody, trackingNode);
    }

    /**
     * Build a ReHoldBody with pending tracking.
     */
    public static ReHoldBody buildPendingHoldBody(ReEventBody originalBody,
            Map<String, DeliveryDestinationStatus> deliveryStatus) {
        ReHoldTrackingPending tracking = new ReHoldTrackingPending(deliveryStatus);
        JsonNode trackingNode = objectMapper.valueToTree(tracking);
        return new ReHoldBody(originalBody, trackingNode);
    }

    public static String toJson(Object body) throws JsonProcessingException {
        return objectMapper.writeValueAsString(body);
    }
}
