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

import com.scalar.re.sdk.storage.TxState;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds a Cosmos DB document Map ready to be passed to {@code CosmosContainer.createItem(...)}.
 *
 * <p>The returned map matches the {@code com.scalar.db.storage.cosmos.Record} JSON shape used by
 * ScalarDB: top-level {@code id}, {@code concatenatedPartitionKey}, and nested
 * {@code partitionKey} / {@code clusteringKey} / {@code values} sub-maps. {@code tx_state=3}
 * (COMMITTED) is written into {@code values} so that RE's polling can read the record.
 *
 * <p>The helper is stateless. The caller owns the {@code CosmosClient} and the transactional batch
 * (if any). See {@link CosmosContainerNames} for building the database/container names.
 */
public final class CosmosOutboxWriter {

    static final String VALUE_BODY = "body";
    static final String VALUE_CREATED_AT = "created_at";
    static final String VALUE_TX_STATE = TxState.COLUMN;
    static final int TX_STATE_COMMITTED = TxState.COMMITTED;

    private CosmosOutboxWriter() {}

    /** Build an outbox document using {@code System.currentTimeMillis()} for {@code created_at}. */
    public static Map<String, Object> buildItem(String eventType, String eventId, String body) {
        return buildItem(eventType, eventId, body, System.currentTimeMillis());
    }

    /** Build an outbox document with an explicit {@code created_at}. */
    public static Map<String, Object> buildItem(
            String eventType, String eventId, String body, long createdAt) {

        if (body == null) {
            throw new IllegalArgumentException("body must not be null");
        }

        Map<String, Object> partitionKey = new LinkedHashMap<>();
        partitionKey.put("event_type", eventType);

        Map<String, Object> clusteringKey = new LinkedHashMap<>();
        clusteringKey.put("event_id", eventId);

        Map<String, Object> values = new LinkedHashMap<>();
        values.put(VALUE_BODY, body);
        values.put(VALUE_CREATED_AT, createdAt);
        values.put(VALUE_TX_STATE, TX_STATE_COMMITTED);

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", CosmosKeyCodec.buildOutboxId(eventType, eventId));
        document.put("concatenatedPartitionKey", CosmosKeyCodec.buildPartitionKey(eventType));
        document.put("partitionKey", partitionKey);
        document.put("clusteringKey", clusteringKey);
        document.put("values", values);

        return document;
    }
}
