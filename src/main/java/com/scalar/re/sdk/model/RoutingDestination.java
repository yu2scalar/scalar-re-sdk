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

import com.fasterxml.jackson.annotation.JsonProperty;

public class RoutingDestination {

    private String destination;

    @JsonProperty("ack_required")
    private boolean ackRequired;

    public RoutingDestination() {
    }

    public RoutingDestination(String destination, boolean ackRequired) {
        this.destination = destination;
        this.ackRequired = ackRequired;
    }

    public RoutingDestination(String destination) {
        this(destination, false);
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public boolean isAckRequired() {
        return ackRequired;
    }

    public void setAckRequired(boolean ackRequired) {
        this.ackRequired = ackRequired;
    }
}
