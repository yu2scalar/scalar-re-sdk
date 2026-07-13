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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Tracking data for relay-type records in the unified re_hold table.
 *
 * Tracks the current step being processed and the ack status of all destinations
 * across all steps.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReHoldTrackingRelay {

    @JsonProperty("current_step")
    private int currentStep;

    @JsonProperty("status_per_destination")
    private Map<String, DestinationStatus> statusPerDestination;

    public ReHoldTrackingRelay() {
    }

    public ReHoldTrackingRelay(int currentStep, Map<String, DestinationStatus> statusPerDestination) {
        this.currentStep = currentStep;
        this.statusPerDestination = statusPerDestination;
    }

    public int getCurrentStep() { return currentStep; }
    public void setCurrentStep(int currentStep) { this.currentStep = currentStep; }

    public Map<String, DestinationStatus> getStatusPerDestination() { return statusPerDestination; }
    public void setStatusPerDestination(Map<String, DestinationStatus> statusPerDestination) {
        this.statusPerDestination = statusPerDestination;
    }
}
