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
import com.scalar.re.sdk.model.DestinationStatus;
import com.scalar.re.sdk.model.ReEventBody;
import com.scalar.re.sdk.model.ReHoldBody;
import com.scalar.re.sdk.model.ReHoldTrackingDlq;
import com.scalar.re.sdk.model.Step;
import com.scalar.re.sdk.parser.ReEventBodyParser;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * SDK-side model coverage for §1.22 relay ack timeout (recovery.md §7.5.3 / §7.5.4):
 * Step.timeout_seconds round-trip, relay-progress preservation in ReHoldTrackingDlq,
 * and ReEventBodyParser.buildRelayDlqHoldBody.
 */
class RelayAckTimeoutModelTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void step_timeoutSeconds_roundTrip() throws Exception {
        Step step = new Step(1, List.of());
        step.setTimeoutSeconds(1800);

        String json = mapper.writeValueAsString(step);
        Step back = mapper.readValue(json, Step.class);

        assertEquals(1800, back.getTimeoutSeconds());
    }

    @Test
    void step_timeoutSeconds_unspecified_isNullAndOmitted() throws Exception {
        Step step = new Step(1, List.of());

        String json = mapper.writeValueAsString(step);
        // NON_NULL: unspecified timeout must not appear in the serialized body
        org.junit.jupiter.api.Assertions.assertFalse(json.contains("timeout_seconds"));

        Step back = mapper.readValue(json, Step.class);
        assertNull(back.getTimeoutSeconds());
    }

    @Test
    void step_legacyBody_withoutTimeout_deserializes() throws Exception {
        String legacy = "{\"step_id\":2,\"decision\":\"ALL_SUCCESS\"}";
        Step back = mapper.readValue(legacy, Step.class);
        assertEquals(2, back.getStepId());
        assertNull(back.getTimeoutSeconds());
    }

    @Test
    void dlqTracking_relayProgress_roundTrip() throws Exception {
        Map<String, DestinationStatus> statusMap = new LinkedHashMap<>();
        statusMap.put("destA", new DestinationStatus("SUCCESS", 1000L));
        statusMap.put("destB", new DestinationStatus(null, null));

        ReHoldTrackingDlq dlq = new ReHoldTrackingDlq(
                "ACK_TIMEOUT", "step 2 ack timed out", 0, 5000L, 2, statusMap);

        String json = mapper.writeValueAsString(dlq);
        ReHoldTrackingDlq back = mapper.readValue(json, ReHoldTrackingDlq.class);

        assertEquals("ACK_TIMEOUT", back.getErrorType());
        assertEquals(2, back.getReachedStep());
        assertNotNull(back.getStatusPerDestination());
        assertEquals("SUCCESS", back.getStatusPerDestination().get("destA").getStatus());
        assertNull(back.getStatusPerDestination().get("destB").getStatus());
    }

    @Test
    void dlqTracking_pushOrigin_hasNoRelayProgress() throws Exception {
        ReHoldTrackingDlq dlq = new ReHoldTrackingDlq("POISON", "bad body", 3, 5000L);

        String json = mapper.writeValueAsString(dlq);
        // NON_NULL: push-origin DLQ must not carry relay-progress fields
        org.junit.jupiter.api.Assertions.assertFalse(json.contains("reached_step"));
        org.junit.jupiter.api.Assertions.assertFalse(json.contains("status_per_destination"));

        ReHoldTrackingDlq back = mapper.readValue(json, ReHoldTrackingDlq.class);
        assertNull(back.getReachedStep());
        assertNull(back.getStatusPerDestination());
    }

    @Test
    void buildRelayDlqHoldBody_carriesProgressIntoTracking() throws Exception {
        ReEventBody originalBody = new ReEventBody(List.of(new Step(2, List.of())));
        Map<String, DestinationStatus> statusMap = new LinkedHashMap<>();
        statusMap.put("destA", new DestinationStatus("FAILURE", 2000L));

        ReHoldBody holdBody = ReEventBodyParser.buildRelayDlqHoldBody(
                originalBody, "ACK_TIMEOUT", "timed out", 0, 9000L, 2, statusMap);

        ReHoldTrackingDlq tracking = ReEventBodyParser.extractDlqTracking(holdBody);
        assertEquals("ACK_TIMEOUT", tracking.getErrorType());
        assertEquals(2, tracking.getReachedStep());
        assertEquals("FAILURE", tracking.getStatusPerDestination().get("destA").getStatus());

        // sanity: the JsonNode form also has the keys
        JsonNode node = holdBody.getTracking();
        org.junit.jupiter.api.Assertions.assertTrue(node.has("reached_step"));
        org.junit.jupiter.api.Assertions.assertTrue(node.has("status_per_destination"));
    }
}
