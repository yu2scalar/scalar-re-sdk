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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scalar.re.sdk.model.ReEventBody;
import com.scalar.re.sdk.model.RoutingDestination;
import com.scalar.re.sdk.model.Sequence;
import com.scalar.re.sdk.model.Step;

import java.util.List;

/**
 * Builder for relay ack messages.
 *
 * Design doc section 3.6 (lines 1239-1264):
 * Consumer builds ack from inbox record columns (event_type, event_id, step_id, seq)
 * + own namespace. Routing is "re.relay" (reserved keyword).
 *
 * Usage:
 *   String ackJson = ReAckBuilder.create()
 *       .originalEventType("OrderSaga")
 *       .originalEventId("uuid-xxx")
 *       .stepId(1)
 *       .seq(1)
 *       .destination("inventory_service")
 *       .status("SUCCESS")
 *       .metadata(Map.of("msg", "done"))
 *       .toJson();
 */
public class ReAckBuilder {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String RELAY_ROUTING = "re.relay";

    private String originalEventType;
    private String originalEventId;
    private int stepId;
    private int seq;
    private String destination;
    private String status;
    private Object metadata;

    public static ReAckBuilder create() {
        return new ReAckBuilder();
    }

    public ReAckBuilder originalEventType(String originalEventType) {
        this.originalEventType = originalEventType;
        return this;
    }

    public ReAckBuilder originalEventId(String originalEventId) {
        this.originalEventId = originalEventId;
        return this;
    }

    public ReAckBuilder stepId(int stepId) {
        this.stepId = stepId;
        return this;
    }

    public ReAckBuilder seq(int seq) {
        this.seq = seq;
        return this;
    }

    public ReAckBuilder destination(String destination) {
        this.destination = destination;
        return this;
    }

    public ReAckBuilder status(String status) {
        this.status = status;
        return this;
    }

    public ReAckBuilder metadata(Object metadata) {
        this.metadata = metadata;
        return this;
    }

    public String toJson() throws JsonProcessingException {
        // Build ack payload (design doc 1268-1272)
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("original_event_type", originalEventType);
        payload.put("original_event_id", originalEventId);
        payload.put("step_id", stepId);
        payload.put("seq", seq);
        payload.put("destination", destination);
        payload.put("status", status);

        // Build sequence with routing="re.relay"
        Sequence sequence = new Sequence(
                1,
                List.of(new RoutingDestination(RELAY_ROUTING, false)),
                payload,
                metadata != null ? objectMapper.valueToTree(metadata) : null
        );

        // Build step
        Step step = new Step(1, List.of(sequence));

        // Build event body
        ReEventBody body = new ReEventBody(List.of(step));

        return objectMapper.writeValueAsString(body);
    }
}
