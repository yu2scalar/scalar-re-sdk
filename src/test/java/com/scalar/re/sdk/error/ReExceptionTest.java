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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReExceptionTest {

    @Test
    void inputExceptionCarriesCodeMessageAndCategory() {
        ReInputException ex = new ReInputException(ReSdkError.UNKNOWN_DELIVERY_TYPE, "weird");
        assertEquals("RE-SDK-3002", ex.getCode());
        assertEquals(Category.INPUT, ex.getCategory());
        assertEquals(Severity.ERROR, ex.getSeverity());
        assertEquals("RE-SDK-3002: Unknown delivery type: weird", ex.getMessage());
        assertSame(ReSdkError.UNKNOWN_DELIVERY_TYPE, ex.getError());
        assertNull(ex.getCause());
    }

    @Test
    void causeIsChained() {
        Throwable root = new IllegalStateException("boom");
        ReInputException ex = new ReInputException(ReSdkError.DELIVERY_TYPE_REQUIRED, root);
        assertSame(root, ex.getCause());
    }

    @Test
    void subclassRejectsMismatchedCategory() {
        // ReSdkError currently only has INPUT entries, so a CONFLICT subclass must reject them.
        assertThrows(IllegalArgumentException.class,
                () -> new ReConflictException(ReSdkError.UNKNOWN_DELIVERY_TYPE));
    }

    @Test
    void inputSubclassAcceptsInputError() {
        ReInputException ex = new ReInputException(ReSdkError.FIELD_NOT_SUPPORTED, "pull", "decision");
        assertEquals("RE-SDK-3004: Delivery type 'pull' does not support 'decision'", ex.getMessage());
    }
}
