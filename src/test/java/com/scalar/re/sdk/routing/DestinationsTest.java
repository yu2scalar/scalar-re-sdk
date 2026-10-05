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

import com.scalar.re.sdk.model.ReEventBody;
import com.scalar.re.sdk.model.RelayProtocol;
import com.scalar.re.sdk.model.RoutingDestination;
import com.scalar.re.sdk.model.Sequence;
import com.scalar.re.sdk.model.Step;
import com.scalar.re.sdk.parser.ParsedBody;
import com.scalar.re.sdk.parser.ReEventBodyParser;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * §2.2.1 UT for {@link Destinations} (data-model §4.3.2): which destinations a transfer writes,
 * per delivery type × step count × event / hold body, and what is skipped.
 */
class DestinationsTest {

    // ---- helpers ----

    private static Sequence seq(int seq, String... destinations) {
        List<RoutingDestination> routing = new ArrayList<>();
        for (String d : destinations) routing.add(new RoutingDestination(d, false));
        return new Sequence(seq, routing, null, null);
    }

    private static Step step(int stepId, Sequence... sequences) {
        return new Step(stepId, new ArrayList<>(Arrays.asList(sequences)));
    }

    private static ReEventBody body(Step... steps) {
        return new ReEventBody(new ArrayList<>(Arrays.asList(steps)));
    }

    /** 3 steps: step 1 → a, step 2 → b + c, step 3 → d. */
    private static ReEventBody threeSteps() {
        return body(step(1, seq(1, "a")), step(2, seq(1, "b"), seq(2, "c")), step(3, seq(1, "d")));
    }

    private static ParsedBody relayHold(ReEventBody original, String trackingJson) throws Exception {
        String json = "{\"original_body\":" + ReEventBodyParser.toJson(original)
                + ",\"tracking\":" + trackingJson + "}";
        return ReEventBodyParser.parseAny(json);
    }

    // ---- of(steps) / of(step) ----

    @Test
    void of_nullSteps_isEmpty() {
        assertTrue(Destinations.of((List<Step>) null).isEmpty());
    }

    @Test
    void of_emptySteps_isEmpty() {
        assertTrue(Destinations.of(List.of()).isEmpty());
    }

    @Test
    void of_nullStep_isEmpty() {
        assertTrue(Destinations.of((Step) null).isEmpty());
    }

    @Test
    void of_skipsNullStepNullSequencesNullRoutingNullEntry() {
        Step nullSequences = new Step(1, null);
        Sequence nullRouting = new Sequence(1, null, null, null);
        List<RoutingDestination> withNullEntry = new ArrayList<>();
        withNullEntry.add(null);
        withNullEntry.add(new RoutingDestination("a", false));
        Step mixed = step(2, nullRouting, new Sequence(2, withNullEntry, null, null));
        List<Step> steps = new ArrayList<>();
        steps.add(null);
        steps.add(nullSequences);
        steps.add(mixed);
        assertEquals(Set.of("a"), Destinations.of(steps));
    }

    @Test
    void of_skipsNullEmptyBlankAndRelayMarker() {
        List<RoutingDestination> routing = new ArrayList<>();
        routing.add(new RoutingDestination(null, false));
        routing.add(new RoutingDestination("", false));
        routing.add(new RoutingDestination("  ", false));
        routing.add(new RoutingDestination(RelayProtocol.RELAY_ROUTING, false));
        routing.add(new RoutingDestination("a", false));
        assertEquals(Set.of("a"), Destinations.of(step(1, new Sequence(1, routing, null, null))));
    }

    @Test
    void of_keepsFirstAppearanceOrder_noDuplicates() {
        Set<String> result = Destinations.of(List.of(step(1, seq(1, "b", "a"), seq(2, "a", "c"))));
        assertEquals(List.of("b", "a", "c"), new ArrayList<>(result));
    }

    // ---- writeTargets(ReEventBody, deliveryType) ----

    @Test
    void writeTargets_nullBody_nullSteps_emptySteps_areEmpty() {
        assertTrue(Destinations.writeTargets((ReEventBody) null, "atomic").isEmpty());
        assertTrue(Destinations.writeTargets(new ReEventBody(null), "atomic").isEmpty());
        assertTrue(Destinations.writeTargets(body(), "relay").isEmpty());
    }

    @Test
    void writeTargets_relay_isFirstStepOnly() {
        assertEquals(Set.of("a"), Destinations.writeTargets(threeSteps(), "relay"));
    }

    @Test
    void writeTargets_relaySingleStep_isThatStep() {
        assertEquals(Set.of("a", "b"),
                Destinations.writeTargets(body(step(1, seq(1, "a", "b"))), "relay"));
    }

    @Test
    void writeTargets_nonRelayTypes_areAllSteps() {
        for (String type : List.of("atomic", "qpull", "partial", "ordered_atomic")) {
            assertEquals(List.of("a", "b", "c", "d"),
                    new ArrayList<>(Destinations.writeTargets(threeSteps(), type)), type);
        }
    }

    @Test
    void writeTargets_nullDeliveryType_isAllSteps() {
        assertEquals(4, Destinations.writeTargets(threeSteps(), null).size());
    }

    @Test
    void writeTargets_pullAndSpull_bodiesWithoutRouting_areEmpty() {
        ReEventBody noRouting = body(step(1, new Sequence(1, null, null, null)));
        assertTrue(Destinations.writeTargets(noRouting, "pull").isEmpty());
        assertTrue(Destinations.writeTargets(noRouting, "spull").isEmpty());
    }

    // ---- writeTargets(ParsedBody, deliveryType) ----

    @Test
    void writeTargetsParsed_null_isEmpty() {
        assertTrue(Destinations.writeTargets((ParsedBody) null, "relay").isEmpty());
    }

    @Test
    void writeTargetsParsed_eventBody_sameAsEventBodyOverload() throws Exception {
        ParsedBody parsed = ReEventBodyParser.parseAny(ReEventBodyParser.toJson(threeSteps()));
        assertEquals(Set.of("a"), Destinations.writeTargets(parsed, "relay"));
        assertEquals(4, Destinations.writeTargets(parsed, "atomic").size());
    }

    @Test
    void writeTargetsParsed_relayHold_firstMiddleLastCurrentStep() throws Exception {
        assertEquals(Set.of("a"), Destinations.writeTargets(
                relayHold(threeSteps(), "{\"current_step\":1}"), "relay"));
        assertEquals(List.of("b", "c"), new ArrayList<>(Destinations.writeTargets(
                relayHold(threeSteps(), "{\"current_step\":2}"), "relay")));
        assertEquals(Set.of("d"), Destinations.writeTargets(
                relayHold(threeSteps(), "{\"current_step\":3}"), "relay"));
    }

    @Test
    void writeTargetsParsed_relayHold_currentStepNotInBody_fallsBackToAllSteps() throws Exception {
        assertEquals(4, Destinations.writeTargets(
                relayHold(threeSteps(), "{\"current_step\":9}"), "relay").size());
    }

    @Test
    void writeTargetsParsed_relayHold_unreadableTracking_fallsBackToAllSteps() throws Exception {
        assertEquals(4, Destinations.writeTargets(
                relayHold(threeSteps(), "{\"current_step\":\"x\"}"), "relay").size());
    }

    @Test
    void writeTargetsParsed_relayHold_nullTracking_fallsBackToAllSteps() throws Exception {
        assertEquals(4, Destinations.writeTargets(relayHold(threeSteps(), "null"), "relay").size());
    }

    @Test
    void writeTargetsParsed_relayHold_nullOriginalBody_isEmpty() throws Exception {
        ParsedBody parsed = ReEventBodyParser.parseAny(
                "{\"original_body\":null,\"tracking\":{\"current_step\":1}}");
        assertTrue(Destinations.writeTargets(parsed, "relay").isEmpty());
    }

    @Test
    void writeTargetsParsed_holdUnderNonRelayType_isAllSteps() throws Exception {
        assertEquals(4, Destinations.writeTargets(
                relayHold(threeSteps(), "{\"current_step\":2}"), "atomic").size());
    }

    // ---- isRelayAck ----

    @Test
    void isRelayAck_trueWhenAnyDestinationIsTheMarker() {
        assertTrue(Destinations.isRelayAck(body(step(1, seq(1, RelayProtocol.RELAY_ROUTING)))));
        assertTrue(Destinations.isRelayAck(body(step(1, seq(1, "a")), step(2, seq(1, "re.relay")))));
    }

    @Test
    void isRelayAck_falseForOrdinaryNullAndEmptyBodies() {
        assertFalse(Destinations.isRelayAck(threeSteps()));
        assertFalse(Destinations.isRelayAck(null));
        assertFalse(Destinations.isRelayAck(new ReEventBody(null)));
        List<Step> steps = new ArrayList<>();
        steps.add(null);
        steps.add(new Step(1, null));
        steps.add(step(2, new Sequence(1, null, null, null)));
        assertFalse(Destinations.isRelayAck(new ReEventBody(steps)));
    }

    @Test
    void isRelayAck_isCaseSensitive() {
        assertFalse(Destinations.isRelayAck(body(step(1, seq(1, "RE.RELAY")))));
    }
}
