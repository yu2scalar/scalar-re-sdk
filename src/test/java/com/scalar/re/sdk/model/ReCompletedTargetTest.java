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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReCompletedTargetTest {

    @Test
    void array_holdsDestinations() {
        ReCompletedTargetArray t = new ReCompletedTargetArray(List.of("destA", "destB"));
        assertEquals(List.of("destA", "destB"), t.destinations());
    }

    @Test
    void array_isImmutable() {
        var input = new java.util.ArrayList<String>();
        input.add("destA");
        ReCompletedTargetArray t = new ReCompletedTargetArray(input);
        input.add("destB");
        // Internal list copied, so mutation outside should not be reflected
        assertEquals(List.of("destA"), t.destinations());
    }

    @Test
    void array_rejectsNullDestinations() {
        assertThrows(NullPointerException.class,
                () -> new ReCompletedTargetArray(null));
    }

    @Test
    void queue_holdsDestinations() {
        ReCompletedTargetQueue t = new ReCompletedTargetQueue(List.of("destA", "destB"));
        assertEquals(List.of("destA", "destB"), t.destinations());
    }

    @Test
    void queue_rejectsNullDestinations() {
        assertThrows(NullPointerException.class,
                () -> new ReCompletedTargetQueue(null));
    }
}
