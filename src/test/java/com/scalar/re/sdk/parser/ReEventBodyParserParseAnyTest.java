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

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

/**
 * §2.2.1 UT for the hold body judgement (data-model §6.4, B-22): {@code original_body} and
 * {@code tracking} both present = hold body; {@link ReEventBodyParser#parseAny} and
 * {@link ReEventBodyParser#isHoldBody} agree.
 */
class ReEventBodyParserParseAnyTest {

    private static final String STEPS = "{\"steps\":[{\"step_id\":1,\"sequences\":[{\"seq\":1,"
            + "\"routing\":[{\"destination\":\"ns_a\"}]}]}]}";

    @Test
    void bothKeys_isHoldBody() throws Exception {
        String json = "{\"original_body\":" + STEPS + ",\"tracking\":{\"current_step\":1}}";
        assertTrue(ReEventBodyParser.isHoldBody(json));
        ParsedBody parsed = ReEventBodyParser.parseAny(json);
        assertTrue(parsed.isHold());
        assertNotNull(parsed.holdBody());
        assertEquals(1, parsed.eventBody().getSteps().size());
        assertSame(parsed.holdBody().getOriginalBody(), parsed.eventBody());
    }

    /** A producer body with a top-level original_body key is NOT a hold body (was misjudged before). */
    @Test
    void originalBodyOnly_isEventBody() throws Exception {
        String json = "{\"steps\":[{\"step_id\":1,\"sequences\":[]}],\"original_body\":{\"x\":1}}";
        assertFalse(ReEventBodyParser.isHoldBody(json));
        ParsedBody parsed = ReEventBodyParser.parseAny(json);
        assertFalse(parsed.isHold());
        assertNull(parsed.holdBody());
        assertEquals(1, parsed.eventBody().getSteps().size());
    }

    @Test
    void trackingOnly_isEventBody() throws Exception {
        String json = "{\"steps\":[],\"tracking\":{\"current_step\":1}}";
        assertFalse(ReEventBodyParser.isHoldBody(json));
        assertFalse(ReEventBodyParser.parseAny(json).isHold());
    }

    @Test
    void neitherKey_isEventBody() throws Exception {
        assertFalse(ReEventBodyParser.isHoldBody(STEPS));
        ParsedBody parsed = ReEventBodyParser.parseAny(STEPS);
        assertFalse(parsed.isHold());
        assertEquals("ns_a", parsed.eventBody().getSteps().get(0).getSequences().get(0)
                .getRouting().get(0).getDestination());
    }

    @Test
    void holdWithNullOriginalBody_isHoldWithNullEventBody() throws Exception {
        ParsedBody parsed = ReEventBodyParser.parseAny("{\"original_body\":null,\"tracking\":{}}");
        assertTrue(parsed.isHold());
        assertNull(parsed.eventBody());
    }

    @Test
    void invalidJson_throws() {
        assertThrows(JsonProcessingException.class, () -> ReEventBodyParser.parseAny("{not json"));
        assertThrows(JsonProcessingException.class, () -> ReEventBodyParser.isHoldBody("{not json"));
    }

    @Test
    void nonObjectJson_isNotHold_andDoesNotParseAsBody() throws Exception {
        assertFalse(ReEventBodyParser.isHoldBody("[1,2]"));
        assertThrows(JsonProcessingException.class, () -> ReEventBodyParser.parseAny("[1,2]"));
    }
}
