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
 * Base class for all ScalarRE exceptions.
 *
 * <p><b>Unchecked</b> ({@code extends RuntimeException}, ADR D3): RE is an application and handles
 * errors centrally ({@code @ControllerAdvice} for sync, {@code RetryTemplate} + governing model for
 * async), so the compile-time enforcement of checked exceptions buys little and clutters signatures.
 *
 * <p>The concrete subclasses correspond 1:1 to a {@link Category} (the immutable fact), <b>not</b> to
 * a disposition (the mutable policy) — there is intentionally no {@code ReRetryableException} etc.
 * (ADR D10 / principle P1). See {@code docs/design/common/error-handling.md} §2.5.
 *
 * <p>The original (e.g. ScalarDB) exception is always chained as the {@code cause} so the DB
 * exception type, code, and stack frame remain in the trace.
 */
public abstract class ReException extends RuntimeException {

    private final transient ScalarReError error;
    private final transient Object[] args;

    protected ReException(ScalarReError error, Throwable cause, Object... args) {
        super(error.buildMessage(args), cause);
        this.error = error;
        this.args = args;
    }

    /**
     * Asserts that {@code error} belongs to {@code expected}. Called by each subclass constructor to
     * keep the subclass type aligned with the error's category.
     */
    protected static void requireCategory(ScalarReError error, Category expected) {
        if (error.getCategory() != expected) {
            throw new IllegalArgumentException(
                    "Error " + error.buildCode() + " has category " + error.getCategory()
                            + " but " + expected + " was expected");
        }
    }

    /** The error definition this exception was raised from. */
    public ScalarReError getError() {
        return error;
    }

    /** The reCode, e.g. {@code RE-SDK-3001}. */
    public String getCode() {
        return error.buildCode();
    }

    public Category getCategory() {
        return error.getCategory();
    }

    public Severity getSeverity() {
        return error.getSeverity();
    }

    /** The message-template arguments, for structured logging / metrics. */
    public Object[] getArgs() {
        return args == null ? new Object[0] : args.clone();
    }
}
