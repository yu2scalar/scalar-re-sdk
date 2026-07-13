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

/**
 * Per-destination acknowledgement status carried inside a relay {@link Step}'s body JSON
 * ({@code ackStatus} map, keyed by destination namespace).
 *
 * <p>Holds whether an ack is required for the destination, the current status label, and an
 * optional payload. This is the body-JSON view of ack tracking; the RE-side {@code re_hold}
 * table uses {@link DestinationStatus} for the equivalent per-destination row.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AckStatusEntry {

    @JsonProperty("ack_required")
    private boolean ackRequired;

    private String status;

    private Object payload;

    public AckStatusEntry() {
    }

    public AckStatusEntry(boolean ackRequired, String status, Object payload) {
        this.ackRequired = ackRequired;
        this.status = status;
        this.payload = payload;
    }

    public boolean isAckRequired() { return ackRequired; }
    public void setAckRequired(boolean ackRequired) { this.ackRequired = ackRequired; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Object getPayload() { return payload; }
    public void setPayload(Object payload) { this.payload = payload; }
}
