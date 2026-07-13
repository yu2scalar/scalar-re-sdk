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
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DynamoInboxReaderTest {

    @Test
    void buildGetKey_containsConcatenatedKeys() {
        InboxKey key = new InboxKey("OrderCreated", 3L, "event-123", 1, 1);

        Map<String, AttributeValue> result = DynamoInboxReader.buildGetKey(key);

        assertNotNull(result.get("concatenatedPartitionKey"));
        assertNotNull(result.get("concatenatedClusteringKey"));
        assertEquals(
                SdkBytes.fromByteBuffer(DynamoKeyCodec.buildPartitionKey("OrderCreated")),
                result.get("concatenatedPartitionKey").b());
        assertEquals(
                SdkBytes.fromByteBuffer(DynamoKeyCodec.buildInboxClusteringKey(3L, "event-123", 1, 1)),
                result.get("concatenatedClusteringKey").b());
    }

    @Test
    void buildDeleteKey_matchesBuildGetKey() {
        InboxKey key = new InboxKey("OrderCreated", 0L, "event-id", 2, 4);
        assertEquals(DynamoInboxReader.buildGetKey(key), DynamoInboxReader.buildDeleteKey(key));
    }

    @Test
    void parseItem_producesFullRecord() {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("event_type", AttributeValue.builder().s("OrderCreated").build());
        item.put("partition", AttributeValue.builder().n("3").build());
        item.put("event_id", AttributeValue.builder().s("event-123").build());
        item.put("step_id", AttributeValue.builder().n("1").build());
        item.put("seq", AttributeValue.builder().n("2").build());
        item.put("body", AttributeValue.builder().s("{\"k\":\"v\"}").build());
        item.put("delivered_at", AttributeValue.builder().n("1700000000000").build());
        item.put("status", AttributeValue.builder().n("0").build());
        item.put("ack_required", AttributeValue.builder().bool(true).build());

        InboxRecord record = DynamoInboxReader.parseItem(item);

        assertEquals("OrderCreated", record.eventType());
        assertEquals(3L, record.partition());
        assertEquals("event-123", record.eventId());
        assertEquals(1, record.stepId());
        assertEquals(2, record.seq());
        assertEquals("{\"k\":\"v\"}", record.body());
        assertEquals(1700000000000L, record.deliveredAt());
        assertEquals(0, record.status());
        assertEquals(true, record.ackRequired());
    }

    @Test
    void parseItem_tolerantOfMissingOptionalFields() {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("event_type", AttributeValue.builder().s("E").build());
        item.put("partition", AttributeValue.builder().n("0").build());
        item.put("event_id", AttributeValue.builder().s("id").build());
        item.put("step_id", AttributeValue.builder().n("0").build());
        item.put("seq", AttributeValue.builder().n("0").build());
        // body / delivered_at / status / ack_required omitted

        InboxRecord record = DynamoInboxReader.parseItem(item);

        assertNull(record.body());
        assertEquals(0L, record.deliveredAt());
        assertEquals(0, record.status());
        assertEquals(false, record.ackRequired());
    }

    @Test
    void parseItem_rejectsEmptyItem() {
        assertThrows(IllegalArgumentException.class,
                () -> DynamoInboxReader.parseItem(new HashMap<>()));
    }

    @Test
    void parseItem_rejectsMissingRequiredField() {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("event_type", AttributeValue.builder().s("E").build());
        // partition missing
        assertThrows(IllegalArgumentException.class,
                () -> DynamoInboxReader.parseItem(item));
    }
}
