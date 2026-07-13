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

import com.scalar.db.io.Key;
import com.scalar.db.storage.cosmos.ConcatenationVisitor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies that {@link CosmosKeyCodec} produces the same concatenated strings as ScalarDB's
 * {@code ConcatenationVisitor}. This is the invariant the Cosmos SDK relies on.
 */
class CosmosKeyCodecTest {

    @Test
    void buildPartitionKey_matchesScalarDbForAscii() {
        String expected = scalarDbConcat(Key.ofText("event_type", "OrderCreated"));
        assertEquals(expected, CosmosKeyCodec.buildPartitionKey("OrderCreated"));
    }

    @Test
    void buildOutboxId_matchesScalarDbConcatenation() {
        Key pkCk = Key.newBuilder()
                .addText("event_type", "OrderCreated")
                .addText("event_id", "019d990a-26d4-7a41-874d-e23d6af526c2")
                .build();
        String expected = scalarDbConcat(pkCk);
        String actual = CosmosKeyCodec.buildOutboxId(
                "OrderCreated", "019d990a-26d4-7a41-874d-e23d6af526c2");
        assertEquals(expected, actual);
    }

    @Test
    void buildInboxId_matchesScalarDbConcatenation() {
        Key pkCk = Key.newBuilder()
                .addText("event_type", "OrderCreated")
                .addBigInt("partition", 3L)
                .addText("event_id", "019d990a-26d4-7a41-874d-e23d6af526c2")
                .addInt("step_id", 1)
                .addInt("seq", 2)
                .build();
        String expected = scalarDbConcat(pkCk);
        String actual = CosmosKeyCodec.buildInboxId(
                "OrderCreated", 3L, "019d990a-26d4-7a41-874d-e23d6af526c2", 1, 2);
        assertEquals(expected, actual);
    }

    @Test
    void buildInboxId_acceptsLargePartitionForReplay() {
        // partition 202604271742 is a timestamp-encoded value used in Replay (BIGINT range)
        Key pkCk = Key.newBuilder()
                .addText("event_type", "OrderCreated")
                .addBigInt("partition", 202604271742L)
                .addText("event_id", "019d990a-26d4-7a41-874d-e23d6af526c2")
                .addInt("step_id", 1)
                .addInt("seq", 2)
                .build();
        String expected = scalarDbConcat(pkCk);
        String actual = CosmosKeyCodec.buildInboxId(
                "OrderCreated", 202604271742L, "019d990a-26d4-7a41-874d-e23d6af526c2", 1, 2);
        assertEquals(expected, actual);
    }

    @Test
    void validateText_rejectsColon() {
        assertThrows(IllegalArgumentException.class,
                () -> CosmosKeyCodec.validateText("event_type", "has:colon"));
    }

    @Test
    void validateText_rejectsSlash() {
        assertThrows(IllegalArgumentException.class,
                () -> CosmosKeyCodec.validateText("event_type", "has/slash"));
    }

    @Test
    void validateText_rejectsBackslash() {
        assertThrows(IllegalArgumentException.class,
                () -> CosmosKeyCodec.validateText("event_type", "has\\back"));
    }

    @Test
    void validateText_rejectsHash() {
        assertThrows(IllegalArgumentException.class,
                () -> CosmosKeyCodec.validateText("event_type", "has#hash"));
    }

    @Test
    void validateText_rejectsQuestion() {
        assertThrows(IllegalArgumentException.class,
                () -> CosmosKeyCodec.validateText("event_type", "has?q"));
    }

    @Test
    void validateText_rejectsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> CosmosKeyCodec.validateText("event_type", null));
    }

    @Test
    void validateText_acceptsSafe() {
        CosmosKeyCodec.validateText("event_type", "OrderCreated");
        CosmosKeyCodec.validateText("event_id", "019d990a-26d4-7a41-874d-e23d6af526c2");
    }

    private static String scalarDbConcat(Key key) {
        ConcatenationVisitor v = new ConcatenationVisitor();
        key.getColumns().forEach(c -> c.accept(v));
        return v.build();
    }
}
