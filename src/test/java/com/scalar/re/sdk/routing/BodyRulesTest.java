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

import static org.junit.jupiter.api.Assertions.*;

import com.scalar.re.sdk.error.ReInputException;
import com.scalar.re.sdk.error.ReSdkError;
import com.scalar.re.sdk.model.RelayProtocol;
import com.scalar.re.sdk.model.RoutingDestination;
import com.scalar.re.sdk.model.Sequence;
import com.scalar.re.sdk.model.Step;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** §2.2.1 UT for {@link BodyRules} and {@link RelayProtocol#isKnownDecision} (data-model §4.3.1). */
class BodyRulesTest {

    private static Step stepWithDestination(String destination) {
        List<RoutingDestination> routing = new ArrayList<>();
        routing.add(new RoutingDestination(destination, false));
        return new Step(3, List.of(new Sequence(7, routing, null, null)));
    }

    private static Step stepWithDecision(String decision) {
        return new Step(2, decision, null, null, List.of());
    }

    // ---- requireSteps ----

    @Test
    void requireSteps_nullOrEmpty_throwsStepsRequired() {
        ReInputException e1 = assertThrows(ReInputException.class, () -> BodyRules.requireSteps(null));
        assertEquals(ReSdkError.STEPS_REQUIRED.buildCode(), e1.getCode());
        ReInputException e2 = assertThrows(ReInputException.class, () -> BodyRules.requireSteps(List.of()));
        assertEquals(ReSdkError.STEPS_REQUIRED.buildCode(), e2.getCode());
    }

    @Test
    void requireSteps_oneStep_passes() {
        assertDoesNotThrow(() -> BodyRules.requireSteps(List.of(new Step(1, List.of()))));
    }

    // ---- requireDestinationsNotBlank ----

    @Test
    void requireDestinationsNotBlank_nullBlankEmptyDestination_throwsWithStepAndSeq() {
        for (String bad : new String[] {null, "", " ", "\t"}) {
            ReInputException e = assertThrows(ReInputException.class,
                    () -> BodyRules.requireDestinationsNotBlank(List.of(stepWithDestination(bad))),
                    String.valueOf(bad));
            assertEquals(ReSdkError.ROUTING_DESTINATION_BLANK.buildCode(), e.getCode());
            assertTrue(e.getMessage().contains("step=3") && e.getMessage().contains("seq=7"), e.getMessage());
        }
    }

    @Test
    void requireDestinationsNotBlank_nullRoutingEntry_throws() {
        List<RoutingDestination> routing = new ArrayList<>();
        routing.add(null);
        Step step = new Step(1, List.of(new Sequence(1, routing, null, null)));
        assertThrows(ReInputException.class, () -> BodyRules.requireDestinationsNotBlank(List.of(step)));
    }

    @Test
    void requireDestinationsNotBlank_validDestinationsAndRelayMarker_pass() {
        assertDoesNotThrow(() -> BodyRules.requireDestinationsNotBlank(
                List.of(stepWithDestination("ns_a"), stepWithDestination(RelayProtocol.RELAY_ROUTING))));
    }

    @Test
    void requireDestinationsNotBlank_nothingToCheck_passes() {
        List<Step> steps = new ArrayList<>();
        steps.add(null);
        steps.add(new Step(1, null));
        List<Sequence> sequences = new ArrayList<>();
        sequences.add(null);
        sequences.add(new Sequence(1, null, null, null));
        steps.add(new Step(2, sequences));
        assertDoesNotThrow(() -> BodyRules.requireDestinationsNotBlank(null));
        assertDoesNotThrow(() -> BodyRules.requireDestinationsNotBlank(steps));
    }

    // ---- requireKnownDecisions / isKnownDecision ----

    @Test
    void requireKnownDecisions_knownValuesAndNull_pass() {
        assertDoesNotThrow(() -> BodyRules.requireKnownDecisions(List.of(
                stepWithDecision(null),
                stepWithDecision(RelayProtocol.DECISION_ALL_SUCCESS),
                stepWithDecision(RelayProtocol.DECISION_ANY_SUCCESS))));
        assertDoesNotThrow(() -> BodyRules.requireKnownDecisions(null));
    }

    @Test
    void requireKnownDecisions_unknownValues_throwWithValueAndStep() {
        for (String bad : new String[] {"all_success", "Any_Success", "", " ", "MAJORITY", "ALL_SUCCESS "}) {
            ReInputException e = assertThrows(ReInputException.class,
                    () -> BodyRules.requireKnownDecisions(List.of(stepWithDecision(bad))), bad);
            assertEquals(ReSdkError.INVALID_DECISION.buildCode(), e.getCode());
            assertTrue(e.getMessage().contains("step=2"), e.getMessage());
        }
    }

    @Test
    void requireKnownDecisions_nullStep_skipped() {
        List<Step> steps = new ArrayList<>();
        steps.add(null);
        assertDoesNotThrow(() -> BodyRules.requireKnownDecisions(steps));
    }

    @Test
    void isKnownDecision_allBranches() {
        assertTrue(RelayProtocol.isKnownDecision(null));
        assertTrue(RelayProtocol.isKnownDecision("ALL_SUCCESS"));
        assertTrue(RelayProtocol.isKnownDecision("ANY_SUCCESS"));
        assertFalse(RelayProtocol.isKnownDecision("any_success"));
        assertFalse(RelayProtocol.isKnownDecision(""));
    }

    @Test
    void newErrorCodes_haveTheAgreedNumbers() {
        assertEquals("RE-SDK-3014", ReSdkError.STEPS_REQUIRED.buildCode());
        assertEquals("RE-SDK-3015", ReSdkError.ROUTING_DESTINATION_BLANK.buildCode());
        assertEquals("RE-SDK-3016", ReSdkError.INVALID_DECISION.buildCode());
    }
}
