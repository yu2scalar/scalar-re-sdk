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
package com.scalar.re.sdk.model;

/**
 * Relay protocol values shared by the SDK and the RE core (data-model §4.3.1, delivery-relay D6):
 * the ack routing marker and the step {@code decision} values. The single definition both sides
 * read, so a value cannot drift between the builder's validation and the core's evaluation.
 */
public final class RelayProtocol {

    /** Routing destination of a relay ack (written by {@code ReAckBuilder}); not a namespace. */
    public static final String RELAY_ROUTING = "re.relay";

    /** Proceed to the next step only when every ack-required destination acked SUCCESS (default). */
    public static final String DECISION_ALL_SUCCESS = "ALL_SUCCESS";
    /** Proceed to the next step when at least one ack-required destination acked SUCCESS. */
    public static final String DECISION_ANY_SUCCESS = "ANY_SUCCESS";

    private RelayProtocol() {}

    /** Whether {@code decision} is allowed: {@code ALL_SUCCESS}, {@code ANY_SUCCESS} or null (= ALL_SUCCESS). */
    public static boolean isKnownDecision(String decision) {
        return decision == null
                || DECISION_ALL_SUCCESS.equals(decision)
                || DECISION_ANY_SUCCESS.equals(decision);
    }
}
