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
package com.scalar.re.sdk.storage;

/**
 * Parsed content of an inbox item. Produced by {@code *InboxReader.parseItem(...)}.
 */
public record InboxRecord(
        String eventType,
        long partition,
        String eventId,
        int stepId,
        int seq,
        String body,
        long deliveredAt,
        int status,
        boolean ackRequired) {

    public InboxKey key() {
        return new InboxKey(eventType, partition, eventId, stepId, seq);
    }
}
