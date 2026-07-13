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

import com.scalar.db.io.Key;
import com.scalar.db.storage.dynamo.bytes.KeyBytesEncoder;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies that {@link DynamoKeyCodec} produces byte-for-byte identical output to
 * ScalarDB's own {@code KeyBytesEncoder}. This is the invariant the whole SDK relies on.
 */
class DynamoKeyCodecTest {

    @Test
    void encodeText_matchesScalarDbForAscii() {
        assertMatchesScalarDbPartitionKey("OrderCreated");
    }

    @Test
    void encodeText_matchesScalarDbForMultiByteUtf8() {
        assertMatchesScalarDbPartitionKey("注文作成");
    }

    @Test
    void encodeText_matchesScalarDbForEmptyString() {
        assertMatchesScalarDbPartitionKey("");
    }

    @Test
    void encodeText_rejectsNullCharacter() {
        assertThrows(IllegalArgumentException.class,
                () -> DynamoKeyCodec.encodeText("with\u0000null"));
    }

    @Test
    void encodeText_rejectsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> DynamoKeyCodec.encodeText(null));
    }

    @Test
    void encodeInt_matchesScalarDbForZero() {
        assertMatchesScalarDbSingleIntCk(0);
    }

    @Test
    void encodeInt_matchesScalarDbForPositive() {
        assertMatchesScalarDbSingleIntCk(12345);
    }

    @Test
    void encodeInt_matchesScalarDbForNegative() {
        assertMatchesScalarDbSingleIntCk(-12345);
    }

    @Test
    void encodeInt_matchesScalarDbForMinValue() {
        assertMatchesScalarDbSingleIntCk(Integer.MIN_VALUE);
    }

    @Test
    void encodeInt_matchesScalarDbForMaxValue() {
        assertMatchesScalarDbSingleIntCk(Integer.MAX_VALUE);
    }

    @Test
    void encodeInt_preservesNumericOrderLexicographically() {
        byte[] a = DynamoKeyCodec.encodeInt(Integer.MIN_VALUE);
        byte[] b = DynamoKeyCodec.encodeInt(-1);
        byte[] c = DynamoKeyCodec.encodeInt(0);
        byte[] d = DynamoKeyCodec.encodeInt(1);
        byte[] e = DynamoKeyCodec.encodeInt(Integer.MAX_VALUE);
        assertTrueLex(a, b);
        assertTrueLex(b, c);
        assertTrueLex(c, d);
        assertTrueLex(d, e);
    }

    @Test
    void buildInboxClusteringKey_matchesScalarDb() {
        Key ck = Key.newBuilder()
                .addBigInt("partition", 3L)
                .addText("event_id", "019d990a-26d4-7a41-874d-e23d6af526c2")
                .addInt("step_id", 1)
                .addInt("seq", 1)
                .build();

        ByteBuffer expected = new KeyBytesEncoder().encode(ck);
        ByteBuffer actual = DynamoKeyCodec.buildInboxClusteringKey(
                3L, "019d990a-26d4-7a41-874d-e23d6af526c2", 1, 1);

        assertEquals(toByteList(expected), toByteList(actual));
    }

    @Test
    void buildInboxClusteringKey_acceptsLargePartitionForReplay() {
        // partition 202604271742 is a timestamp-encoded value used in Replay (BIGINT range)
        Key ck = Key.newBuilder()
                .addBigInt("partition", 202604271742L)
                .addText("event_id", "019d990a-26d4-7a41-874d-e23d6af526c2")
                .addInt("step_id", 1)
                .addInt("seq", 1)
                .build();

        ByteBuffer expected = new KeyBytesEncoder().encode(ck);
        ByteBuffer actual = DynamoKeyCodec.buildInboxClusteringKey(
                202604271742L, "019d990a-26d4-7a41-874d-e23d6af526c2", 1, 1);

        assertEquals(toByteList(expected), toByteList(actual));
    }

    @Test
    void encodeBigInt_matchesScalarDbForZero() {
        assertMatchesScalarDbSingleBigIntCk(0L);
    }

    @Test
    void encodeBigInt_matchesScalarDbForPositive() {
        assertMatchesScalarDbSingleBigIntCk(202604271742L);
    }

    @Test
    void encodeBigInt_matchesScalarDbForNegative() {
        assertMatchesScalarDbSingleBigIntCk(-12345L);
    }

    @Test
    void encodeBigInt_matchesScalarDbForMinSafeValue() {
        // ScalarDB BIGINT range is JavaScript safe-integer range (±2^53), narrower than Long range.
        assertMatchesScalarDbSingleBigIntCk(-9007199254740992L);
    }

    @Test
    void encodeBigInt_matchesScalarDbForMaxSafeValue() {
        assertMatchesScalarDbSingleBigIntCk(9007199254740992L);
    }

    @Test
    void encodeBigInt_preservesNumericOrderLexicographically() {
        // Use values within ScalarDB BIGINT safe range (±2^53).
        byte[] a = DynamoKeyCodec.encodeBigInt(-9007199254740992L);
        byte[] b = DynamoKeyCodec.encodeBigInt(-1L);
        byte[] c = DynamoKeyCodec.encodeBigInt(0L);
        byte[] d = DynamoKeyCodec.encodeBigInt(1L);
        byte[] e = DynamoKeyCodec.encodeBigInt(9007199254740992L);
        assertTrueLex(a, b);
        assertTrueLex(b, c);
        assertTrueLex(c, d);
        assertTrueLex(d, e);
    }

    // --- helpers ---

    private static void assertMatchesScalarDbPartitionKey(String eventType) {
        ByteBuffer expected = new KeyBytesEncoder().encode(Key.ofText("event_type", eventType));
        ByteBuffer actual = DynamoKeyCodec.buildPartitionKey(eventType);
        assertEquals(toByteList(expected), toByteList(actual));
    }

    private static void assertMatchesScalarDbSingleIntCk(int value) {
        Key ck = Key.newBuilder().addInt("x", value).build();
        ByteBuffer expected = new KeyBytesEncoder().encode(ck);
        byte[] expectedBytes = new byte[expected.remaining()];
        expected.get(expectedBytes);
        assertArrayEquals(expectedBytes, DynamoKeyCodec.encodeInt(value));
    }

    private static void assertMatchesScalarDbSingleBigIntCk(long value) {
        Key ck = Key.newBuilder().addBigInt("x", value).build();
        ByteBuffer expected = new KeyBytesEncoder().encode(ck);
        byte[] expectedBytes = new byte[expected.remaining()];
        expected.get(expectedBytes);
        assertArrayEquals(expectedBytes, DynamoKeyCodec.encodeBigInt(value));
    }

    private static java.util.List<Byte> toByteList(ByteBuffer buf) {
        buf.rewind();
        java.util.List<Byte> out = new java.util.ArrayList<>();
        while (buf.hasRemaining()) {
            out.add(buf.get());
        }
        return out;
    }

    private static void assertTrueLex(byte[] smaller, byte[] larger) {
        int cmp = compareLex(smaller, larger);
        if (cmp >= 0) {
            throw new AssertionError("expected " + java.util.Arrays.toString(smaller)
                    + " < " + java.util.Arrays.toString(larger));
        }
    }

    private static int compareLex(byte[] a, byte[] b) {
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int ai = a[i] & 0xFF;
            int bi = b[i] & 0xFF;
            if (ai != bi) return ai - bi;
        }
        return a.length - b.length;
    }
}
