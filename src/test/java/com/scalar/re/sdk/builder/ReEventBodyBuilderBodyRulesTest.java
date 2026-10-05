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

import static org.junit.jupiter.api.Assertions.*;

import com.scalar.re.sdk.error.ReInputException;
import com.scalar.re.sdk.error.ReSdkError;
import com.scalar.re.sdk.model.ReEventBody;
import com.scalar.re.sdk.model.RelayProtocol;
import com.scalar.re.sdk.model.RoutingDestination;
import com.scalar.re.sdk.model.Sequence;
import com.scalar.re.sdk.model.Step;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * §2.2.1 UT for the body rules the builder applies (data-model §4.3.1, R-02 / R-11 ③) and the
 * notify payload's routing set (data-model §4.3.2, B-13).
 */
class ReEventBodyBuilderBodyRulesTest {

    private static final List<String> ALL_TYPES =
            List.of("atomic", "partial", "relay", "qpull", "pull", "spull", "ordered_atomic");

    private static String code(Runnable r) {
        return assertThrows(ReInputException.class, r::run).getCode();
    }

    private static ReEventBodyBuilder withDestination(String type, String destination) {
        List<RoutingDestination> routing = new ArrayList<>();
        routing.add(new RoutingDestination(destination, false));
        Map<String, Object> re = Map.of("re", Map.of("partition", 0, "ordinal", 0));
        Sequence seq = new Sequence(1, routing, null,
                new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(re));
        return ReEventBodyBuilder.create().deliveryType(type).addStep(new Step(1, List.of(seq)));
    }

    // ---- STEPS_REQUIRED ----

    @Test
    void noSteps_everyDeliveryType_stepsRequired() {
        for (String type : ALL_TYPES) {
            assertEquals(ReSdkError.STEPS_REQUIRED.buildCode(),
                    code(() -> ReEventBodyBuilder.create().deliveryType(type).build()), type);
        }
    }

    // ---- ROUTING_DESTINATION_BLANK ----

    @Test
    void blankDestination_everyDeliveryType_destinationBlank() {
        for (String type : ALL_TYPES) {
            for (String bad : new String[] {null, "", "  "}) {
                assertEquals(ReSdkError.ROUTING_DESTINATION_BLANK.buildCode(),
                        code(() -> withDestination(type, bad).build()), type + "/" + bad);
            }
        }
    }

    /** R-02 = case B: the SDK does not reject zero routing for atomic / partial / qpull / relay. */
    @Test
    void zeroRouting_nonOrderedTypes_stillBuilds() {
        for (String type : List.of("atomic", "partial", "qpull", "relay")) {
            assertDoesNotThrow(() -> ReEventBodyBuilder.create().deliveryType(type)
                    .addStep(s -> s.stepId(1).addSequence(q -> q.seq(1).payload(Map.of("k", 1))))
                    .build(), type);
        }
    }

    // ---- INVALID_DECISION (relay) ----

    @Test
    void relayDecision_knownValuesAndUnset_build() {
        for (String decision : new String[] {null, RelayProtocol.DECISION_ALL_SUCCESS,
                RelayProtocol.DECISION_ANY_SUCCESS}) {
            assertDoesNotThrow(() -> ReEventBodyBuilder.create().deliveryType("relay")
                    .addStep(s -> s.stepId(1).decision(decision)
                            .addSequence(q -> q.seq(1).routing("ns_a")))
                    .build(), String.valueOf(decision));
        }
    }

    @Test
    void relayDecision_unknownValues_invalidDecision() {
        for (String bad : new String[] {"any_success", "", "MAJORITY"}) {
            assertEquals(ReSdkError.INVALID_DECISION.buildCode(), code(() -> ReEventBodyBuilder.create()
                    .deliveryType("relay")
                    .addStep(s -> s.stepId(1).addSequence(q -> q.seq(1).routing("ns_a")))
                    .addStep(s -> s.stepId(2).decision(bad).addSequence(q -> q.seq(1).routing("ns_b")))
                    .build()), bad);
        }
    }

    /** Non-relay types keep rejecting any decision as an unsupported field (unchanged). */
    @Test
    void nonRelayDecision_stillFieldNotSupported() {
        assertEquals(ReSdkError.FIELD_NOT_SUPPORTED.buildCode(), code(() -> ReEventBodyBuilder.create()
                .deliveryType("atomic")
                .addStep(s -> s.stepId(1).decision("ALL_SUCCESS").addSequence(q -> q.seq(1).routing("ns_a")))
                .build()));
    }

    // ---- notify payload routing = the destinations the transfer writes ----

    private static ReEventBodyBuilder twoSteps(String type) {
        return ReEventBodyBuilder.create().deliveryType(type)
                .addStep(s -> s.stepId(1).addSequence(q -> q.seq(1).routing("ns_a")))
                .addStep(s -> s.stepId(2).addSequence(q -> q.seq(1).routing("ns_b", "ns_c")));
    }

    @Test
    void notifyPayload_multiStepAtomicAndQpull_allSteps() throws Exception {
        assertEquals(Set.of("ns_a", "ns_b", "ns_c"), twoSteps("atomic").toNotifyPayload().routingNamespaces());
        assertEquals(Set.of("ns_a", "ns_b", "ns_c"), twoSteps("qpull").toNotifyPayload().routingNamespaces());
    }

    @Test
    void notifyPayload_multiStepRelay_firstStepOnly() throws Exception {
        assertEquals(Set.of("ns_a"), twoSteps("relay").toNotifyPayload().routingNamespaces());
    }

    @Test
    void getFirstStepRoutingNamespaces_skipsNullAndRelayMarker() {
        List<RoutingDestination> routing = new ArrayList<>();
        routing.add(new RoutingDestination(null, false));
        routing.add(new RoutingDestination(RelayProtocol.RELAY_ROUTING, false));
        routing.add(new RoutingDestination("ns_a", false));
        ReEventBody body = new ReEventBody(List.of(new Step(1, List.of(new Sequence(1, routing, null, null)))));
        assertEquals(Set.of("ns_a"), body.getFirstStepRoutingNamespaces());
    }
}
