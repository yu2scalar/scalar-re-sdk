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

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds a DynamoDB {@link AttributeValue} {@link Map} ready to be passed to
 * {@code PutItemRequest.item(...)} or embedded into a {@code TransactWriteItems} Put.
 *
 * <p>The returned map satisfies ScalarDB's storage format for {@code re_outbox} on DynamoDB:
 * concatenated PK/CK binary keys, raw key columns, value columns, and
 * {@code tx_state=3} (COMMITTED) ConsensusCommit metadata so that RE's polling can read it.
 *
 * <p>The helper is stateless. The caller owns the {@code DynamoDbClient} and the transaction
 * boundary (if any). See {@link DynamoTableNames} for building the table name.
 */
public final class DynamoOutboxWriter {

    static final String CONCATENATED_PK = "concatenatedPartitionKey";
    static final String CONCATENATED_CK = "concatenatedClusteringKey";
    static final String EVENT_TYPE = "event_type";
    static final String EVENT_ID = "event_id";
    static final String BODY = "body";
    static final String CREATED_AT = "created_at";
    static final String TX_STATE = "tx_state";
    static final int TX_STATE_COMMITTED = 3;

    private DynamoOutboxWriter() {}

    /** Build an outbox item using {@code System.currentTimeMillis()} for {@code created_at}. */
    public static Map<String, AttributeValue> buildItem(String eventType, String eventId, String body) {
        return buildItem(eventType, eventId, body, System.currentTimeMillis());
    }

    /** Build an outbox item with an explicit {@code created_at} (epoch milliseconds). */
    public static Map<String, AttributeValue> buildItem(
            String eventType, String eventId, String body, long createdAt) {

        if (body == null) {
            throw new IllegalArgumentException("body must not be null");
        }

        Map<String, AttributeValue> item = new HashMap<>();

        item.put(CONCATENATED_PK, AttributeValue.builder()
                .b(SdkBytes.fromByteBuffer(DynamoKeyCodec.buildPartitionKey(eventType)))
                .build());
        item.put(CONCATENATED_CK, AttributeValue.builder()
                .b(SdkBytes.fromByteBuffer(DynamoKeyCodec.buildOutboxClusteringKey(eventId)))
                .build());

        item.put(EVENT_TYPE, AttributeValue.builder().s(eventType).build());
        item.put(EVENT_ID, AttributeValue.builder().s(eventId).build());
        item.put(BODY, AttributeValue.builder().s(body).build());
        item.put(CREATED_AT, AttributeValue.builder().n(String.valueOf(createdAt)).build());
        item.put(TX_STATE, AttributeValue.builder().n(String.valueOf(TX_STATE_COMMITTED)).build());

        return item;
    }
}
