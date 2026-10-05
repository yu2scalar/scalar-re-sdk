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
package com.scalar.re.sdk.routing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.scalar.re.sdk.model.ReEventBody;
import com.scalar.re.sdk.model.ReHoldTrackingRelay;
import com.scalar.re.sdk.model.RelayProtocol;
import com.scalar.re.sdk.model.RoutingDestination;
import com.scalar.re.sdk.model.Sequence;
import com.scalar.re.sdk.model.Step;
import com.scalar.re.sdk.parser.ParsedBody;
import com.scalar.re.sdk.parser.ReEventBodyParser;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reading the routing destinations out of a body — the single implementation shared by the SDK
 * (notify payload) and the RE core (poll, transfer, relay advance). See data-model §4.3.2.
 *
 * <p>Every method skips null / blank destinations and the relay ack marker
 * {@link RelayProtocol#RELAY_ROUTING} (the ack goes to the internal queue, it names no namespace),
 * and keeps the body order (first appearance).
 */
public final class Destinations {

    private static final String RELAY = "relay";

    private Destinations() {}

    /** Destinations of every step. */
    public static Set<String> of(List<Step> steps) {
        Set<String> destinations = new LinkedHashSet<>();
        if (steps == null) return destinations;
        for (Step step : steps) {
            addStep(step, destinations);
        }
        return destinations;
    }

    /** Destinations of one step. */
    public static Set<String> of(Step step) {
        Set<String> destinations = new LinkedHashSet<>();
        addStep(step, destinations);
        return destinations;
    }

    /**
     * The destinations this transfer writes (data-model §4.3.2) — the set the queue selection
     * compares: relay = the current step (an event body = its first step), every other delivery
     * type = all steps (they write every step at once). Pull / spull bodies carry no routing, so
     * the set is empty for them.
     */
    public static Set<String> writeTargets(ReEventBody body, String deliveryType) {
        if (body == null || body.getSteps() == null || body.getSteps().isEmpty()) {
            return new LinkedHashSet<>();
        }
        if (RELAY.equals(deliveryType)) {
            return of(body.getSteps().get(0));
        }
        return of(body.getSteps());
    }

    /**
     * Same as {@link #writeTargets(ReEventBody, String)} for a parsed outbox body. A relay hold
     * body (a step 2+ outbox row) writes the step named by its tracking {@code current_step};
     * an unreadable tracking or a missing step falls back to all steps of {@code original_body}.
     */
    public static Set<String> writeTargets(ParsedBody body, String deliveryType) {
        if (body == null) return new LinkedHashSet<>();
        if (!body.isHold() || !RELAY.equals(deliveryType)) {
            return writeTargets(body.eventBody(), deliveryType);
        }
        ReEventBody original = body.eventBody();
        if (original == null) return new LinkedHashSet<>();
        Step current = currentStep(body, original);
        return current != null ? of(current) : of(original.getSteps());
    }

    /** Whether the body is a relay ack: some routing destination is {@link RelayProtocol#RELAY_ROUTING}. */
    public static boolean isRelayAck(ReEventBody body) {
        if (body == null || body.getSteps() == null) return false;
        for (Step step : body.getSteps()) {
            if (step == null || step.getSequences() == null) continue;
            for (Sequence seq : step.getSequences()) {
                if (seq == null || seq.getRouting() == null) continue;
                for (RoutingDestination rd : seq.getRouting()) {
                    if (rd != null && RelayProtocol.RELAY_ROUTING.equals(rd.getDestination())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static Step currentStep(ParsedBody body, ReEventBody original) {
        ReHoldTrackingRelay tracking;
        try {
            tracking = ReEventBodyParser.extractRelayTracking(body.holdBody());
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return null;
        }
        if (tracking == null || original.getSteps() == null) return null;
        for (Step step : original.getSteps()) {
            if (step != null && step.getStepId() == tracking.getCurrentStep()) {
                return step;
            }
        }
        return null;
    }

    private static void addStep(Step step, Set<String> destinations) {
        if (step == null || step.getSequences() == null) return;
        for (Sequence seq : step.getSequences()) {
            if (seq == null || seq.getRouting() == null) continue;
            for (RoutingDestination rd : seq.getRouting()) {
                if (rd == null) continue;
                String destination = rd.getDestination();
                if (destination == null || destination.isBlank()
                        || RelayProtocol.RELAY_ROUTING.equals(destination)) {
                    continue;
                }
                destinations.add(destination);
            }
        }
    }
}
