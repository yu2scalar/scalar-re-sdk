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

import com.azure.cosmos.models.PartitionKey;
import com.scalar.re.sdk.storage.InboxKey;
import com.scalar.re.sdk.storage.InboxRecord;

import java.util.Map;

/**
 * Helpers for reading {@code re_inbox} on Cosmos DB from a consumer microservice.
 *
 * <p>Inputs (from {@code POST /api/v1/re/poll} v2.7+ records) are wrapped in {@link InboxKey}.
 * The helper builds the {@code id} and {@link PartitionKey} required by
 * {@code container.readItem(id, partitionKey, clazz)} and {@code container.deleteItem(...)}.
 */
public final class CosmosInboxReader {

    private CosmosInboxReader() {}

    /** Cosmos {@code id} for a given inbox key. */
    public static String buildId(InboxKey key) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        return CosmosKeyCodec.buildInboxId(
                key.eventType(), key.partition(), key.eventId(), key.stepId(), key.seq());
    }

    /** Cosmos logical partition key for a given inbox key (uses {@code event_type}). */
    public static PartitionKey buildPartitionKey(InboxKey key) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        return new PartitionKey(CosmosKeyCodec.buildPartitionKey(key.eventType()));
    }

    /** Cosmos logical partition key for a given {@code event_type}. */
    public static PartitionKey buildPartitionKey(String eventType) {
        return new PartitionKey(CosmosKeyCodec.buildPartitionKey(eventType));
    }

    /**
     * Parse a Cosmos DB document (as {@code Map<String, Object>}) into an {@link InboxRecord}.
     * Expects the ScalarDB {@code Record} shape with nested {@code partitionKey},
     * {@code clusteringKey}, {@code values} maps.
     */
    @SuppressWarnings("unchecked")
    public static InboxRecord parseItem(Map<String, Object> document) {
        if (document == null || document.isEmpty()) {
            throw new IllegalArgumentException("document must not be null or empty");
        }

        Map<String, Object> partitionKey = asMap(document.get("partitionKey"));
        Map<String, Object> clusteringKey = asMap(document.get("clusteringKey"));
        Map<String, Object> values = asMap(document.get("values"));

        return new InboxRecord(
                requiredString(partitionKey, "event_type"),
                requiredLong(clusteringKey, "partition"),
                requiredString(clusteringKey, "event_id"),
                requiredInt(clusteringKey, "step_id"),
                requiredInt(clusteringKey, "seq"),
                optionalString(values, "body"),
                optionalLong(values, "delivered_at", 0L),
                optionalInt(values, "status", 0),
                optionalBool(values, "ack_required", false)
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        if (o == null) return java.util.Collections.emptyMap();
        if (!(o instanceof Map)) {
            throw new IllegalArgumentException("Expected nested Map, got " + o.getClass());
        }
        return (Map<String, Object>) o;
    }

    private static String requiredString(Map<String, Object> m, String name) {
        Object v = m.get(name);
        if (!(v instanceof String)) {
            throw new IllegalArgumentException("Missing required String: " + name);
        }
        return (String) v;
    }

    private static int requiredInt(Map<String, Object> m, String name) {
        Object v = m.get(name);
        if (!(v instanceof Number)) {
            throw new IllegalArgumentException("Missing required Number: " + name);
        }
        return ((Number) v).intValue();
    }

    private static long requiredLong(Map<String, Object> m, String name) {
        Object v = m.get(name);
        if (!(v instanceof Number)) {
            throw new IllegalArgumentException("Missing required Number: " + name);
        }
        return ((Number) v).longValue();
    }

    private static String optionalString(Map<String, Object> m, String name) {
        Object v = m.get(name);
        return v instanceof String ? (String) v : null;
    }

    private static long optionalLong(Map<String, Object> m, String name, long defaultValue) {
        Object v = m.get(name);
        return v instanceof Number ? ((Number) v).longValue() : defaultValue;
    }

    private static int optionalInt(Map<String, Object> m, String name, int defaultValue) {
        Object v = m.get(name);
        return v instanceof Number ? ((Number) v).intValue() : defaultValue;
    }

    private static boolean optionalBool(Map<String, Object> m, String name, boolean defaultValue) {
        Object v = m.get(name);
        return v instanceof Boolean ? (Boolean) v : defaultValue;
    }
}
