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
package com.scalar.re.sdk.storage.cosmos;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CosmosContainerNamesTest {

    @Test
    void database_returnsNamespaceAsIs() {
        assertEquals("ns_cosmos", CosmosContainerNames.database("ns_cosmos"));
    }

    @Test
    void database_rejectsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> CosmosContainerNames.database(null));
    }

    @Test
    void database_rejectsEmpty() {
        assertThrows(IllegalArgumentException.class,
                () -> CosmosContainerNames.database(""));
    }

    @Test
    void containerConstants() {
        assertEquals("re_outbox", CosmosContainerNames.outboxContainer());
        assertEquals("re_inbox", CosmosContainerNames.inboxContainer());
    }
}
