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

import com.scalar.re.sdk.storage.InboxKey;
import com.scalar.re.sdk.storage.InboxRecord;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.HashMap;
import java.util.Map;

/**
 * Helpers for reading {@code re_inbox} on DynamoDB from a consumer microservice.
 *
 * <p>Inputs (from {@code POST /api/v1/re/poll} v2.7+ records) are wrapped in {@link InboxKey}.
 * The helper builds the composite {@code concatenatedPartitionKey} / {@code concatenatedClusteringKey}
 * required by {@code GetItem} / {@code DeleteItem} / {@code TransactWriteItems}.
 */
public final class DynamoInboxReader {

    private static final String CONCATENATED_PK = "concatenatedPartitionKey";
    private static final String CONCATENATED_CK = "concatenatedClusteringKey";

    private DynamoInboxReader() {}

    /**
     * Build the primary-key map for {@code GetItem} / the target of {@code Delete} inside
     * {@code TransactWriteItems}.
     */
    public static Map<String, AttributeValue> buildGetKey(InboxKey key) {
        return buildKey(key);
    }

    /** Alias of {@link #buildGetKey(InboxKey)} — same shape, used for delete semantics. */
    public static Map<String, AttributeValue> buildDeleteKey(InboxKey key) {
        return buildKey(key);
    }

    /**
     * Parse a {@code Map<String, AttributeValue>} returned by {@code GetItem} / {@code Query}
     * into an {@link InboxRecord}.
     */
    public static InboxRecord parseItem(Map<String, AttributeValue> item) {
        if (item == null || item.isEmpty()) {
            throw new IllegalArgumentException("item must not be null or empty");
        }
        return new InboxRecord(
                requiredString(item, "event_type"),
                requiredNumber(item, "partition"),
                requiredString(item, "event_id"),
                requiredNumber(item, "step_id").intValue(),
                requiredNumber(item, "seq").intValue(),
                optionalString(item, "body"),
                optionalNumber(item, "delivered_at", 0L),
                optionalNumber(item, "status", 0L).intValue(),
                optionalBool(item, "ack_required", false)
        );
    }

    private static Map<String, AttributeValue> buildKey(InboxKey key) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        Map<String, AttributeValue> out = new HashMap<>();
        out.put(CONCATENATED_PK, AttributeValue.builder()
                .b(SdkBytes.fromByteBuffer(DynamoKeyCodec.buildPartitionKey(key.eventType())))
                .build());
        out.put(CONCATENATED_CK, AttributeValue.builder()
                .b(SdkBytes.fromByteBuffer(DynamoKeyCodec.buildInboxClusteringKey(
                        key.partition(), key.eventId(), key.stepId(), key.seq())))
                .build());
        return out;
    }

    private static String requiredString(Map<String, AttributeValue> item, String name) {
        AttributeValue v = item.get(name);
        if (v == null || v.s() == null) {
            throw new IllegalArgumentException("Missing required String attribute: " + name);
        }
        return v.s();
    }

    private static Long requiredNumber(Map<String, AttributeValue> item, String name) {
        AttributeValue v = item.get(name);
        if (v == null || v.n() == null) {
            throw new IllegalArgumentException("Missing required Number attribute: " + name);
        }
        return Long.parseLong(v.n());
    }

    private static String optionalString(Map<String, AttributeValue> item, String name) {
        AttributeValue v = item.get(name);
        return v == null ? null : v.s();
    }

    private static Long optionalNumber(Map<String, AttributeValue> item, String name, long defaultValue) {
        AttributeValue v = item.get(name);
        return (v == null || v.n() == null) ? defaultValue : Long.parseLong(v.n());
    }

    private static boolean optionalBool(Map<String, AttributeValue> item, String name, boolean defaultValue) {
        AttributeValue v = item.get(name);
        return (v == null || v.bool() == null) ? defaultValue : v.bool();
    }
}
