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

/** A required external resource is unreachable ({@link Category#UNAVAILABLE}). */
public class ReUnavailableException extends ReException {

    public ReUnavailableException(ScalarReError error, Object... args) {
        this(error, (Throwable) null, args);
    }

    public ReUnavailableException(ScalarReError error, Throwable cause, Object... args) {
        super(error, cause, args);
        requireCategory(error, Category.UNAVAILABLE);
    }
}
