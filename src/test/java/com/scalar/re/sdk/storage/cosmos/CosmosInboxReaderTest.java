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
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CosmosInboxReaderTest {

    @Test
    void buildId_matchesExpectedConcatenation() {
        InboxKey key = new InboxKey("OrderCreated", 3, "event-123", 1, 2);
        assertEquals("OrderCreated:3:event-123:1:2", CosmosInboxReader.buildId(key));
    }

    @Test
    void buildPartitionKey_producesCosmosPartitionKeyFromEventType() {
        PartitionKey pk = CosmosInboxReader.buildPartitionKey("OrderCreated");
        assertNotNull(pk);
        assertEquals(new PartitionKey("OrderCreated"), pk);
    }

    @Test
    void buildPartitionKey_fromInboxKey_matchesDirectForm() {
        InboxKey key = new InboxKey("OrderCreated", 0, "id", 0, 0);
        assertEquals(
                CosmosInboxReader.buildPartitionKey("OrderCreated"),
                CosmosInboxReader.buildPartitionKey(key));
    }

    @Test
    void parseItem_producesFullRecord() {
        Map<String, Object> doc = buildSampleDocument();

        InboxRecord record = CosmosInboxReader.parseItem(doc);

        assertEquals("OrderCreated", record.eventType());
        assertEquals(3L, record.partition());
        assertEquals("event-123", record.eventId());
        assertEquals(1, record.stepId());
        assertEquals(2, record.seq());
        assertEquals("{\"k\":\"v\"}", record.body());
        assertEquals(1_700_000_000_000L, record.deliveredAt());
        assertEquals(0, record.status());
        assertEquals(true, record.ackRequired());
    }

    @Test
    void parseItem_tolerantOfMissingOptionalFields() {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", "E:0:id:0:0");
        doc.put("concatenatedPartitionKey", "E");
        doc.put("partitionKey", Map.of("event_type", "E"));
        doc.put("clusteringKey", Map.of(
                "partition", 0,
                "event_id", "id",
                "step_id", 0,
                "seq", 0));
        doc.put("values", Map.of()); // no body/delivered_at/status/ack_required

        InboxRecord record = CosmosInboxReader.parseItem(doc);
        assertNull(record.body());
        assertEquals(0L, record.deliveredAt());
        assertEquals(0, record.status());
        assertEquals(false, record.ackRequired());
    }

    @Test
    void parseItem_rejectsEmptyDocument() {
        assertThrows(IllegalArgumentException.class,
                () -> CosmosInboxReader.parseItem(new HashMap<>()));
    }

    private static Map<String, Object> buildSampleDocument() {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", "OrderCreated:3:event-123:1:2");
        doc.put("concatenatedPartitionKey", "OrderCreated");
        doc.put("partitionKey", Map.of("event_type", "OrderCreated"));
        doc.put("clusteringKey", Map.of(
                "partition", 3,
                "event_id", "event-123",
                "step_id", 1,
                "seq", 2));
        doc.put("values", Map.of(
                "body", "{\"k\":\"v\"}",
                "delivered_at", 1_700_000_000_000L,
                "status", 0,
                "ack_required", true,
                "tx_state", 3));
        return doc;
    }
}
