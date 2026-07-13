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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReSdkErrorTest {

    /** ScalarDB {@code CoreErrorTest#checkDuplicateErrorCode} equivalent. */
    @Test
    void codesAreUnique() {
        Set<String> codes = new HashSet<>();
        for (ReSdkError e : ReSdkError.values()) {
            assertTrue(codes.add(e.buildCode()), "Duplicate code: " + e.buildCode());
        }
        assertEquals(ReSdkError.values().length, codes.size());
    }

    @Test
    void everyEntryIsWellFormed() {
        for (ReSdkError e : ReSdkError.values()) {
            assertEquals("RE-SDK", e.getComponentName());
            assertEquals(3, e.getId().length(), "id must be 3 digits: " + e);
            assertTrue(e.getCause() != null && !e.getCause().isEmpty(), "cause required: " + e);
            assertTrue(e.getSolution() != null && !e.getSolution().isEmpty(), "solution required: " + e);
            assertTrue(e.getMessage() != null && !e.getMessage().isEmpty(), "message required: " + e);
        }
    }

    @Test
    void buildCodeFollowsScheme() {
        assertEquals("RE-SDK-3002", ReSdkError.UNKNOWN_DELIVERY_TYPE.buildCode());
        assertTrue(ReSdkError.UNKNOWN_DELIVERY_TYPE.buildCode().startsWith("RE-SDK-3"),
                "INPUT category digit is 3");
    }

    @Test
    void buildMessageFormatsArgs() {
        String msg = ReSdkError.UNKNOWN_DELIVERY_TYPE.buildMessage("foo");
        assertEquals("RE-SDK-3002: Unknown delivery type: foo", msg);
    }

    @Test
    void buildMessageWithoutArgsLeavesTemplate() {
        assertEquals("RE-SDK-3001: Delivery type must be set",
                ReSdkError.DELIVERY_TYPE_REQUIRED.buildMessage());
    }

    @Test
    void validateRejectsWrongIdLength() {
        ScalarReError bad = stub(Category.INPUT, "12", "m", "c", "s");
        assertThrows(IllegalArgumentException.class,
                () -> bad.validate("RE-SDK", bad.getCategory(), bad.getId(), bad.getSeverity(),
                        bad.getMessage(), bad.getCause(), bad.getSolution()));
    }

    @Test
    void validateRejectsEmptyCauseOrSolution() {
        ScalarReError noCause = stub(Category.INPUT, "001", "m", "", "s");
        assertThrows(IllegalArgumentException.class,
                () -> noCause.validate("RE-SDK", noCause.getCategory(), noCause.getId(),
                        noCause.getSeverity(), noCause.getMessage(), noCause.getCause(), noCause.getSolution()));

        ScalarReError noSolution = stub(Category.INPUT, "001", "m", "c", "");
        assertThrows(IllegalArgumentException.class,
                () -> noSolution.validate("RE-SDK", noSolution.getCategory(), noSolution.getId(),
                        noSolution.getSeverity(), noSolution.getMessage(), noSolution.getCause(),
                        noSolution.getSolution()));
    }

    @Test
    void everyCategoryDigitIsDistinct() {
        Set<String> digits = Arrays.stream(Category.values())
                .map(Category::getId)
                .collect(Collectors.toSet());
        assertEquals(Category.values().length, digits.size());
    }

    private static ScalarReError stub(Category category, String id, String message,
                                      String cause, String solution) {
        return new ScalarReError() {
            public String getComponentName() { return "RE-SDK"; }
            public Category getCategory() { return category; }
            public String getId() { return id; }
            public Severity getSeverity() { return Severity.ERROR; }
            public String getMessage() { return message; }
            public String getCause() { return cause; }
            public String getSolution() { return solution; }
        };
    }
}
