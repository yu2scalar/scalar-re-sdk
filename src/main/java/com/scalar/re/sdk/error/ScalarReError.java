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

import java.util.Objects;

/**
 * Contract for a ScalarRE error / message code definition.
 *
 * <p>Borrows the structure of ScalarDB's {@code ScalarDbError} but makes {@code cause} and
 * {@code solution} <b>required</b> (non-empty) and adds {@link Severity}. Implemented by the
 * per-component enums {@code ReSdkError} (this repo, {@code RE-SDK-}) and {@code ReCoreError}
 * (scalar-re, {@code RE-CORE-}). See {@code docs/design/common/error-handling.md} §2.1 / §2.10.
 *
 * <p>Code format: {@code RE-{CORE|SDK}-[C][NNN]} where {@code C} is the {@link Category} digit
 * and {@code NNN} is a 3-digit per-category sequence number (append-only, never reused).
 */
public interface ScalarReError {

    /** The component name, e.g. {@code "RE-CORE"} / {@code "RE-SDK"}. */
    String getComponentName();

    /** The fault-nature category (the {@code C} digit). */
    Category getCategory();

    /** The 3-digit per-category sequence id (the {@code NNN} segment). */
    String getId();

    /** The severity, mapped to the runtime log level. */
    Severity getSeverity();

    /** The message template; may contain {@code %s}-style placeholders. */
    String getMessage();

    /** The probable cause (required, non-empty). */
    String getCause();

    /** The suggested solution (required, non-empty). */
    String getSolution();

    /**
     * Validates the definition. Call from each enum constructor so malformed codes fail fast at
     * class-load time (ScalarDB {@code ScalarDbError#validate} convention, with stricter cause /
     * solution requirements).
     */
    default void validate(
            String componentName,
            Category category,
            String id,
            Severity severity,
            String message,
            String cause,
            String solution) {
        Objects.requireNonNull(componentName, "The component name must not be null");
        Objects.requireNonNull(category, "The category must not be null");
        Objects.requireNonNull(id, "The id must not be null");
        if (id.length() != 3) {
            throw new IllegalArgumentException("The length of the id must be 3, but was " + id);
        }
        Objects.requireNonNull(severity, "The severity must not be null");
        Objects.requireNonNull(message, "The message must not be null");
        if (cause == null || cause.isEmpty()) {
            throw new IllegalArgumentException("The cause must be non-empty for " + componentName + " " + id);
        }
        if (solution == null || solution.isEmpty()) {
            throw new IllegalArgumentException("The solution must be non-empty for " + componentName + " " + id);
        }
    }

    /**
     * Builds the code, e.g. {@code RE-SDK-3001}.
     *
     * <p>{@code <componentName>-<categoryId><id>}
     */
    default String buildCode() {
        return getComponentName() + "-" + getCategory().getId() + getId();
    }

    /**
     * Builds the full message: {@code <code>: <message>} with {@code args} formatted in.
     */
    default String buildMessage(Object... args) {
        return buildCode()
                + ": "
                + (args.length == 0 ? getMessage() : String.format(getMessage(), args));
    }
}
