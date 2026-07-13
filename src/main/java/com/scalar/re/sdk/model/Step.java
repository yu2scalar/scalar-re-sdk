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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ignoreUnknown: tolerate keys no longer modelled (e.g. the removed timeline field "delay_ms")
// so bodies persisted before that field's removal still deserialize.
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Step {

    @JsonProperty("step_id")
    private int stepId;

    private String decision;

    @JsonProperty("on_failure")
    private List<String> onFailure;

    @JsonProperty("on_failure_payload")
    private Map<String, Object> onFailurePayload;

    private List<Sequence> sequences;

    @JsonProperty("ack_status")
    private Map<String, AckStatusEntry> ackStatus;

    // ack timeout for this step, in seconds (§1.22 / recovery.md §7.5.4).
    // null = no timeout (wait indefinitely, backward compatible). Only relay steps honor it.
    @JsonProperty("timeout_seconds")
    private Integer timeoutSeconds;

    public Step() {
    }

    public Step(int stepId, List<Sequence> sequences) {
        this.stepId = stepId;
        this.sequences = sequences;
    }

    public Step(int stepId, String decision,
                List<String> onFailure, Map<String, Object> onFailurePayload,
                List<Sequence> sequences) {
        this.stepId = stepId;
        this.decision = decision;
        this.onFailure = onFailure;
        this.onFailurePayload = onFailurePayload;
        this.sequences = sequences;
    }

    public int getStepId() { return stepId; }
    public void setStepId(int stepId) { this.stepId = stepId; }

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }

    public List<String> getOnFailure() { return onFailure; }
    public void setOnFailure(List<String> onFailure) { this.onFailure = onFailure; }

    public Map<String, Object> getOnFailurePayload() { return onFailurePayload; }
    public void setOnFailurePayload(Map<String, Object> onFailurePayload) { this.onFailurePayload = onFailurePayload; }

    public List<Sequence> getSequences() { return sequences; }
    public void setSequences(List<Sequence> sequences) { this.sequences = sequences; }

    public Map<String, AckStatusEntry> getAckStatus() { return ackStatus; }
    public void setAckStatus(Map<String, AckStatusEntry> ackStatus) { this.ackStatus = ackStatus; }

    public Integer getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(Integer timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    /**
     * Initialize ack_status from this step's routing destinations.
     * Only ack_required=true destinations are tracked.
     */
    public void initializeAckStatus() {
        ackStatus = new LinkedHashMap<>();
        if (sequences == null) return;
        for (Sequence sequence : sequences) {
            if (sequence.getRouting() == null) continue;
            for (RoutingDestination rd : sequence.getRouting()) {
                if (rd.isAckRequired() && !ackStatus.containsKey(rd.getDestination())) {
                    ackStatus.put(rd.getDestination(), new AckStatusEntry(true, null, null));
                }
            }
        }
    }

    /**
     * Check if all ack_required destinations have responded.
     */
    @JsonIgnore
    public boolean allAcksReceived() {
        if (ackStatus == null || ackStatus.isEmpty()) return true;
        return ackStatus.values().stream()
                .filter(AckStatusEntry::isAckRequired)
                .allMatch(e -> e.getStatus() != null);
    }
}
