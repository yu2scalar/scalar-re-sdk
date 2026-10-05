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
package com.scalar.re.sdk.parser;

import com.scalar.re.sdk.model.ReEventBody;
import com.scalar.re.sdk.model.ReHoldBody;

/**
 * An outbox / hold body JSON parsed once ({@link ReEventBodyParser#parseAny}): either an event body
 * or a hold body (data-model §6.4: {@code original_body} and {@code tracking} both present). Callers
 * pass this along instead of re-parsing the JSON for validation, routing and the transfer itself.
 */
public final class ParsedBody {

    private final ReEventBody eventBody;
    private final ReHoldBody holdBody;

    private ParsedBody(ReEventBody eventBody, ReHoldBody holdBody) {
        this.eventBody = eventBody;
        this.holdBody = holdBody;
    }

    static ParsedBody ofEvent(ReEventBody eventBody) {
        return new ParsedBody(eventBody, null);
    }

    static ParsedBody ofHold(ReHoldBody holdBody) {
        return new ParsedBody(holdBody.getOriginalBody(), holdBody);
    }

    /** Whether the JSON was a hold body. */
    public boolean isHold() {
        return holdBody != null;
    }

    /**
     * The event body: the body itself, or the hold body's {@code original_body} (may be null when a
     * hold body carries {@code "original_body": null}).
     */
    public ReEventBody eventBody() {
        return eventBody;
    }

    /** The hold body, or null for an event body. */
    public ReHoldBody holdBody() {
        return holdBody;
    }
}
