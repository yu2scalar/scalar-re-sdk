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
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Unified body structure for the re_hold table.
 *
 * Contains the original producer body unchanged, plus tracking data
 * that varies by hold type (relay, pending, dlq).
 *
 * The tracking field is stored as JsonNode to support polymorphic deserialization.
 * Use ReEventBodyParser to extract typed tracking objects.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReHoldBody {

    @JsonProperty("original_body")
    private ReEventBody originalBody;

    private JsonNode tracking;

    public ReHoldBody() {
    }

    public ReHoldBody(ReEventBody originalBody, JsonNode tracking) {
        this.originalBody = originalBody;
        this.tracking = tracking;
    }

    public ReEventBody getOriginalBody() { return originalBody; }
    public void setOriginalBody(ReEventBody originalBody) { this.originalBody = originalBody; }

    public JsonNode getTracking() { return tracking; }
    public void setTracking(JsonNode tracking) { this.tracking = tracking; }
}
