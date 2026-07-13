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
 * Tracking data for DLQ-type records in the unified re_hold table.
 *
 * Contains error information and retry count for failed events.
 *
 * <p>For relay-origin DLQ (decision NG / ack timeout, recovery.md §7.5.3), the
 * relay progress at the moment of DLQ entry is preserved so the operator can
 * inspect how far delivery got before deciding to delete / re-produce. These
 * fields are null for push-origin DLQ.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReHoldTrackingDlq {

    @JsonProperty("error_type")
    private String errorType;

    @JsonProperty("error_message")
    private String errorMessage;

    @JsonProperty("retry_count")
    private int retryCount;

    @JsonProperty("failed_at")
    private long failedAt;

    // relay progress at DLQ entry (§7.5.3). null for push-origin DLQ.
    @JsonProperty("reached_step")
    private Integer reachedStep;

    @JsonProperty("status_per_destination")
    private Map<String, DestinationStatus> statusPerDestination;

    public ReHoldTrackingDlq() {
    }

    public ReHoldTrackingDlq(String errorType, String errorMessage, int retryCount, long failedAt) {
        this.errorType = errorType;
        this.errorMessage = errorMessage;
        this.retryCount = retryCount;
        this.failedAt = failedAt;
    }

    public ReHoldTrackingDlq(String errorType, String errorMessage, int retryCount, long failedAt,
                             Integer reachedStep, Map<String, DestinationStatus> statusPerDestination) {
        this.errorType = errorType;
        this.errorMessage = errorMessage;
        this.retryCount = retryCount;
        this.failedAt = failedAt;
        this.reachedStep = reachedStep;
        this.statusPerDestination = statusPerDestination;
    }

    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    public long getFailedAt() { return failedAt; }
    public void setFailedAt(long failedAt) { this.failedAt = failedAt; }

    public Integer getReachedStep() { return reachedStep; }
    public void setReachedStep(Integer reachedStep) { this.reachedStep = reachedStep; }

    public Map<String, DestinationStatus> getStatusPerDestination() { return statusPerDestination; }
    public void setStatusPerDestination(Map<String, DestinationStatus> statusPerDestination) {
        this.statusPerDestination = statusPerDestination;
    }
}
