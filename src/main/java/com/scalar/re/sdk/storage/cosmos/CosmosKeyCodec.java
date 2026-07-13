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
 * Encodes ScalarDB PK/CK column values to the colon-concatenated strings used by the
 * Cosmos DB adapter ({@code concatenatedPartitionKey} and {@code id}).
 *
 * <p>Matches {@code com.scalar.db.storage.cosmos.ConcatenationVisitor}:
 * values joined with {@code ':'}, TEXT values left as-is, numeric values converted via
 * {@code String.valueOf()}. BLOB (not used by re_outbox / re_inbox here) would be Base64 URL-safe.
 *
 * <p>PK/CK TEXT columns must not contain any of {@code : / \ # ?} — these are rejected by
 * ScalarDB's {@code CosmosOperationChecker} because Cosmos {@code id} values are URI-pathed.
 */
public final class CosmosKeyCodec {

    private static final char[] ILLEGAL_CHARS = {':', '/', '\\', '#', '?'};
    static final String SEPARATOR = ":";

    private CosmosKeyCodec() {}

    /** Validate a TEXT PK/CK value. Throws {@link IllegalArgumentException} when invalid. */
    public static void validateText(String columnName, String value) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "PK/CK TEXT value must not be null (column=" + columnName + ")");
        }
        for (char illegal : ILLEGAL_CHARS) {
            if (value.indexOf(illegal) >= 0) {
                throw new IllegalArgumentException(
                        "PK/CK TEXT value for '" + columnName + "' contains illegal character '"
                                + illegal + "'. Cosmos prohibits : / \\ # ? in id-derived values.");
            }
        }
    }

    /** {@code concatenatedPartitionKey} for outbox/inbox: single TEXT column {@code event_type}. */
    public static String buildPartitionKey(String eventType) {
        validateText("event_type", eventType);
        return eventType;
    }

    /** {@code id} for {@code re_outbox}: {@code event_type:event_id}. */
    public static String buildOutboxId(String eventType, String eventId) {
        validateText("event_type", eventType);
        validateText("event_id", eventId);
        return eventType + SEPARATOR + eventId;
    }

    /**
     * {@code id} for {@code re_inbox}:
     * {@code event_type:partition:event_id:step_id:seq}.
     *
     * <p>partition is BIGINT (was INT in v1) to support reserved Replay partition range
     * (10000+) including timestamp-encoded values like {@code 202604271742}.
     */
    public static String buildInboxId(
            String eventType, long partition, String eventId, int stepId, int seq) {
        validateText("event_type", eventType);
        validateText("event_id", eventId);
        return eventType + SEPARATOR + partition + SEPARATOR + eventId
                + SEPARATOR + stepId + SEPARATOR + seq;
    }
}
