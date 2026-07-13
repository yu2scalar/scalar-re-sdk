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

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DynamoOutboxWriterTest {

    @Test
    void buildItem_populatesAllRequiredAttributes() {
        long now = 1_700_000_000_000L;
        Map<String, AttributeValue> item = DynamoOutboxWriter.buildItem(
                "OrderCreated", "event-123", "{\"k\":\"v\"}", now);

        assertNotNull(item.get("concatenatedPartitionKey"));
        assertNotNull(item.get("concatenatedClusteringKey"));
        assertEquals("OrderCreated", item.get("event_type").s());
        assertEquals("event-123", item.get("event_id").s());
        assertEquals("{\"k\":\"v\"}", item.get("body").s());
        assertEquals(String.valueOf(now), item.get("created_at").n());
        assertEquals("3", item.get("tx_state").n());
    }

    @Test
    void buildItem_concatenatedKeysMatchCodec() {
        Map<String, AttributeValue> item = DynamoOutboxWriter.buildItem(
                "OrderCreated", "event-123", "body", 1L);

        SdkBytes pk = item.get("concatenatedPartitionKey").b();
        SdkBytes ck = item.get("concatenatedClusteringKey").b();

        assertEquals(
                SdkBytes.fromByteBuffer(DynamoKeyCodec.buildPartitionKey("OrderCreated")),
                pk);
        assertEquals(
                SdkBytes.fromByteBuffer(DynamoKeyCodec.buildOutboxClusteringKey("event-123")),
                ck);
    }

    @Test
    void buildItem_defaultCreatedAtIsPositive() {
        Map<String, AttributeValue> item = DynamoOutboxWriter.buildItem("E", "id", "body");
        assertEquals(true, Long.parseLong(item.get("created_at").n()) > 0);
    }

    @Test
    void buildItem_rejectsNullBody() {
        assertThrows(IllegalArgumentException.class,
                () -> DynamoOutboxWriter.buildItem("E", "id", null));
    }
}
