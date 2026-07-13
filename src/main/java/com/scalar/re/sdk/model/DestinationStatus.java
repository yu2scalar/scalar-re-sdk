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

/**
 * Status of a destination in relay tracking.
 * Replaces AckStatusEntry with a more general structure for the unified re_hold table.
 *
 * status: null=pending, "SUCCESS", "FAILURE"
 * timestamp: epoch ms when status was recorded, null if pending
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DestinationStatus {

    private String status;

    private Long timestamp;

    public DestinationStatus() {
    }

    public DestinationStatus(String status, Long timestamp) {
        this.status = status;
        this.timestamp = timestamp;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
}
