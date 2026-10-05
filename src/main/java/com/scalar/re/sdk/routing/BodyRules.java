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

import com.scalar.re.sdk.error.ReInputException;
import com.scalar.re.sdk.error.ReSdkError;
import com.scalar.re.sdk.model.RelayProtocol;
import com.scalar.re.sdk.model.RoutingDestination;
import com.scalar.re.sdk.model.Sequence;
import com.scalar.re.sdk.model.Step;
import java.util.List;

/**
 * Body rules shared by the SDK builder and the RE core's pre-TX validation (data-model §4.3.1), in
 * the same way as {@link com.scalar.re.sdk.metadata.ReservedMetadata}: the builder throws, the
 * core turns the {@link ReInputException} into a poison message (→ DLQ). Rules that need the RE
 * configuration (routing required per delivery type, configured namespaces) live in the core.
 */
public final class BodyRules {

    private BodyRules() {}

    /**
     * At least one step.
     *
     * @throws ReInputException {@link ReSdkError#STEPS_REQUIRED}
     */
    public static void requireSteps(List<Step> steps) {
        if (steps == null || steps.isEmpty()) {
            throw new ReInputException(ReSdkError.STEPS_REQUIRED);
        }
    }

    /**
     * No routing entry has a null, empty or blank destination (any delivery type).
     *
     * @throws ReInputException {@link ReSdkError#ROUTING_DESTINATION_BLANK} on the first violation
     */
    public static void requireDestinationsNotBlank(List<Step> steps) {
        if (steps == null) return;
        for (Step step : steps) {
            if (step == null || step.getSequences() == null) continue;
            for (Sequence seq : step.getSequences()) {
                if (seq == null || seq.getRouting() == null) continue;
                for (RoutingDestination rd : seq.getRouting()) {
                    if (rd == null || rd.getDestination() == null || rd.getDestination().isBlank()) {
                        throw new ReInputException(ReSdkError.ROUTING_DESTINATION_BLANK,
                                step.getStepId(), seq.getSeq());
                    }
                }
            }
        }
    }

    /**
     * Every step's {@code decision} is {@code ALL_SUCCESS}, {@code ANY_SUCCESS} or null (relay,
     * delivery-relay D6). Case-sensitive.
     *
     * @throws ReInputException {@link ReSdkError#INVALID_DECISION} on the first violation
     */
    public static void requireKnownDecisions(List<Step> steps) {
        if (steps == null) return;
        for (Step step : steps) {
            if (step != null && !RelayProtocol.isKnownDecision(step.getDecision())) {
                throw new ReInputException(ReSdkError.INVALID_DECISION,
                        step.getDecision(), step.getStepId());
            }
        }
    }
}
