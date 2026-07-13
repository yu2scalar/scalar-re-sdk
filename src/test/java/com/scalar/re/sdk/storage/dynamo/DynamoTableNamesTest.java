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
package com.scalar.re.sdk.storage.dynamo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DynamoTableNamesTest {

    @Test
    void outbox_withPrefix() {
        assertEquals("scalardb_ns_dynamo.re_outbox",
                DynamoTableNames.outbox("scalardb_", "ns_dynamo"));
    }

    @Test
    void inbox_withPrefix() {
        assertEquals("scalardb_ns_dynamo.re_inbox",
                DynamoTableNames.inbox("scalardb_", "ns_dynamo"));
    }

    @Test
    void outbox_withEmptyPrefix() {
        assertEquals("ns_dynamo.re_outbox",
                DynamoTableNames.outbox("", "ns_dynamo"));
    }

    @Test
    void outbox_withNullPrefix_treatedAsEmpty() {
        assertEquals("ns_dynamo.re_outbox",
                DynamoTableNames.outbox(null, "ns_dynamo"));
    }

    @Test
    void outbox_rejectsNullNamespace() {
        assertThrows(IllegalArgumentException.class,
                () -> DynamoTableNames.outbox("scalardb_", null));
    }

    @Test
    void outbox_rejectsEmptyNamespace() {
        assertThrows(IllegalArgumentException.class,
                () -> DynamoTableNames.outbox("scalardb_", ""));
    }
}
