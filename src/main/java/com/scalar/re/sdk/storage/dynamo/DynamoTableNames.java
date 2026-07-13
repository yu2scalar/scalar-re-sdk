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

/**
 * Builds the DynamoDB table names that ScalarDB uses for {@code re_outbox} / {@code re_inbox}.
 *
 * <p>ScalarDB prepends the configured {@code scalar.db.dynamo.namespace.prefix} to every namespace,
 * then joins it with the table name with a dot: {@code <prefix><namespace>.<table>}.
 * For example, prefix {@code "scalardb_"} with namespace {@code "ns_dynamo"} produces
 * {@code "scalardb_ns_dynamo.re_outbox"}.
 *
 * <p>The caller is expected to know the prefix from their ScalarDB config. The SDK intentionally
 * does not load ScalarDB config to avoid bringing it as a runtime dependency.
 */
public final class DynamoTableNames {

    public static final String OUTBOX = "re_outbox";
    public static final String INBOX = "re_inbox";

    private DynamoTableNames() {}

    /** Outbox table name. Pass {@code ""} for {@code namespacePrefix} if unset. */
    public static String outbox(String namespacePrefix, String namespace) {
        return buildTableName(namespacePrefix, namespace, OUTBOX);
    }

    /** Inbox table name. Pass {@code ""} for {@code namespacePrefix} if unset. */
    public static String inbox(String namespacePrefix, String namespace) {
        return buildTableName(namespacePrefix, namespace, INBOX);
    }

    private static String buildTableName(String namespacePrefix, String namespace, String table) {
        if (namespace == null || namespace.isEmpty()) {
            throw new IllegalArgumentException("namespace must not be null or empty");
        }
        String prefix = namespacePrefix == null ? "" : namespacePrefix;
        return prefix + namespace + "." + table;
    }
}
