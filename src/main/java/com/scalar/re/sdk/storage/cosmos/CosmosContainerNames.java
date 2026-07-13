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

/**
 * Builds the Cosmos DB database/container names used by ScalarDB for {@code re_outbox} / {@code re_inbox}.
 *
 * <p>Cosmos does NOT apply any namespace prefix (unlike DynamoDB): ScalarDB calls
 * {@code client.getDatabase(namespace)} directly. The container name is the table name
 * verbatim (e.g. {@code "re_outbox"}).
 */
public final class CosmosContainerNames {

    public static final String OUTBOX_CONTAINER = "re_outbox";
    public static final String INBOX_CONTAINER = "re_inbox";

    private CosmosContainerNames() {}

    /** Cosmos database name for a ScalarDB namespace. Returns the namespace as-is. */
    public static String database(String namespace) {
        if (namespace == null || namespace.isEmpty()) {
            throw new IllegalArgumentException("namespace must not be null or empty");
        }
        return namespace;
    }

    public static String outboxContainer() {
        return OUTBOX_CONTAINER;
    }

    public static String inboxContainer() {
        return INBOX_CONTAINER;
    }
}
