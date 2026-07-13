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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CosmosOutboxWriterTest {

    @Test
    void buildItem_populatesScalarDbRecordShape() {
        long now = 1_700_000_000_000L;
        Map<String, Object> doc = CosmosOutboxWriter.buildItem(
                "OrderCreated", "event-123", "{\"k\":\"v\"}", now);

        assertEquals("OrderCreated:event-123", doc.get("id"));
        assertEquals("OrderCreated", doc.get("concatenatedPartitionKey"));

        @SuppressWarnings("unchecked")
        Map<String, Object> pk = (Map<String, Object>) doc.get("partitionKey");
        assertEquals("OrderCreated", pk.get("event_type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> ck = (Map<String, Object>) doc.get("clusteringKey");
        assertEquals("event-123", ck.get("event_id"));

        @SuppressWarnings("unchecked")
        Map<String, Object> values = (Map<String, Object>) doc.get("values");
        assertEquals("{\"k\":\"v\"}", values.get("body"));
        assertEquals(now, values.get("created_at"));
        assertEquals(3, values.get("tx_state"));
    }

    @Test
    void buildItem_defaultCreatedAtIsPositive() {
        Map<String, Object> doc = CosmosOutboxWriter.buildItem("E", "id", "body");
        @SuppressWarnings("unchecked")
        Map<String, Object> values = (Map<String, Object>) doc.get("values");
        assertEquals(true, (Long) values.get("created_at") > 0);
    }

    @Test
    void buildItem_rejectsIllegalEventTypeChar() {
        assertThrows(IllegalArgumentException.class,
                () -> CosmosOutboxWriter.buildItem("bad:eventType", "id", "body"));
    }

    @Test
    void buildItem_rejectsIllegalEventIdChar() {
        assertThrows(IllegalArgumentException.class,
                () -> CosmosOutboxWriter.buildItem("E", "bad/id", "body"));
    }

    @Test
    void buildItem_rejectsNullBody() {
        assertThrows(IllegalArgumentException.class,
                () -> CosmosOutboxWriter.buildItem("E", "id", null));
    }
}
