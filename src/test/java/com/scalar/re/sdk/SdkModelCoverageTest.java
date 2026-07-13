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
import com.scalar.re.sdk.builder.ReAckBuilder;
import com.scalar.re.sdk.model.DeliveryDestinationStatus;
import com.scalar.re.sdk.model.DestinationStatus;
import com.scalar.re.sdk.model.ReHoldTrackingPending;
import com.scalar.re.sdk.model.ReHoldTrackingRelay;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * §2.2.1 UT for previously-uncovered SDK model / builder classes:
 * {@link ReAckBuilder} (relay ack JSON shape), {@link DeliveryDestinationStatus},
 * {@link ReHoldTrackingPending}, {@link ReHoldTrackingRelay}.
 */
class SdkModelCoverageTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void reAckBuilder_toJson_emitsRelayRoutingAndAckPayload() throws Exception {
        String json = ReAckBuilder.create()
                .originalEventType("OrderSaga")
                .originalEventId("uuid-1")
                .stepId(2)
                .seq(3)
                .destination("inventory")
                .status("SUCCESS")
                .metadata(Map.of("msg", "done"))
                .toJson();

        JsonNode body = MAPPER.readTree(json);
        JsonNode seq = body.get("steps").get(0).get("sequences").get(0);
        assertThat(seq.get("routing").get(0).get("destination").asText()).isEqualTo("re.relay");
        JsonNode payload = seq.get("payload");
        assertThat(payload.get("original_event_type").asText()).isEqualTo("OrderSaga");
        assertThat(payload.get("original_event_id").asText()).isEqualTo("uuid-1");
        assertThat(payload.get("step_id").asInt()).isEqualTo(2);
        assertThat(payload.get("seq").asInt()).isEqualTo(3);
        assertThat(payload.get("destination").asText()).isEqualTo("inventory");
        assertThat(payload.get("status").asText()).isEqualTo("SUCCESS");
        assertThat(seq.get("metadata").get("msg").asText()).isEqualTo("done");
    }

    @Test
    void reAckBuilder_toJson_withoutMetadata_omitsMetadataNode() throws Exception {
        String json = ReAckBuilder.create()
                .originalEventType("E").originalEventId("i").stepId(1).seq(1)
                .destination("d").status("FAIL")
                .toJson();
        JsonNode seq = MAPPER.readTree(json).get("steps").get(0).get("sequences").get(0);
        // metadata==null branch → not serialized (NON_NULL) or explicit null
        assertThat(seq.hasNonNull("metadata")).isFalse();
    }

    @Test
    void deliveryDestinationStatus_ctorAndSetters() {
        DeliveryDestinationStatus s = new DeliveryDestinationStatus(true, 123L);
        assertThat(s.isDelivered()).isTrue();
        assertThat(s.getTimestamp()).isEqualTo(123L);

        DeliveryDestinationStatus empty = new DeliveryDestinationStatus();
        empty.setDelivered(true);
        empty.setTimestamp(9L);
        assertThat(empty.isDelivered()).isTrue();
        assertThat(empty.getTimestamp()).isEqualTo(9L);
    }

    @Test
    void reHoldTrackingPending_holdsDeliveryStatusMap() {
        ReHoldTrackingPending p = new ReHoldTrackingPending(
                Map.of("inventory", new DeliveryDestinationStatus(false, null)));
        assertThat(p.getDeliveryStatus()).containsKey("inventory");
        assertThat(p.getDeliveryStatus().get("inventory").isDelivered()).isFalse();
        assertThat(new ReHoldTrackingPending().getDeliveryStatus()).isNull();
    }

    @Test
    void reHoldTrackingRelay_holdsStepAndPerDestinationStatus() {
        ReHoldTrackingRelay r = new ReHoldTrackingRelay(
                4, Map.of("payment", new DestinationStatus("SUCCESS", 5L)));
        assertThat(r.getCurrentStep()).isEqualTo(4);
        assertThat(r.getStatusPerDestination().get("payment").getStatus()).isEqualTo("SUCCESS");

        ReHoldTrackingRelay empty = new ReHoldTrackingRelay();
        empty.setCurrentStep(1);
        assertThat(empty.getCurrentStep()).isEqualTo(1);
    }
}
