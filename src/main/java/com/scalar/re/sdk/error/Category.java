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
package com.scalar.re.sdk.error;

/**
 * Immutable fault-nature category for ScalarRE error / message codes.
 *
 * <p>The category is the first digit {@code C} of a code ({@code RE-{CORE|SDK}-[C][NNN]}).
 * It classifies <b>what happened</b> (the fact), never the disposition (retry / pause / DLQ),
 * which is a mutable policy. See {@code docs/design/common/error-handling.md} §2.3 and ADR D1.
 *
 * <p>Numbers are assigned in encounter order. {@code 7} and {@code 8} are reserved for future
 * append-only additions (candidates: NOT_FOUND, CAPACITY).
 */
public enum Category {

    /** Configuration / deployment environment is invalid. */
    CONFIG("1"),
    /** Authentication / authorization check failed. */
    AUTH("2"),
    /** Received data / request violates the contract (malformed body, missing field, bad param). */
    INPUT("3"),
    /** Concurrent update conflict detected. */
    CONFLICT("4"),
    /** A required external resource (storage / destination) is unreachable. */
    UNAVAILABLE("5"),
    /** The success or failure of the operation cannot be determined. */
    INDETERMINATE("6"),
    /** Unexpected internal state / program defect (catch-all). */
    INTERNAL("9");

    private final String id;

    Category(String id) {
        this.id = id;
    }

    /** The single-digit category id used as the {@code C} segment of a code. */
    public String getId() {
        return id;
    }
}
