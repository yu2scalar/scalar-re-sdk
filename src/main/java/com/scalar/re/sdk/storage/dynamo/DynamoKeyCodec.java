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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Encodes ScalarDB PK/CK columns to the binary form that ScalarDB's DynamoDB
 * adapter writes into {@code concatenatedPartitionKey} / {@code concatenatedClusteringKey}.
 *
 * <p>Matches {@code com.scalar.db.storage.dynamo.bytes.KeyBytesEncoder} with ASC ordering
 * for the types used by {@code re_outbox} and {@code re_inbox}: TEXT, INT, and BIGINT.
 *
 * <p>TEXT: UTF-8 bytes followed by a {@code 0x00} terminator. The value must not contain
 * {@code \u0000}.
 *
 * <p>INT: 4 bytes big-endian with the sign bit flipped ({@code (b0 ^ 0x80) || b1 || b2 || b3}).
 * BIGINT: 8 bytes big-endian with the sign bit flipped (same scheme, 8 bytes).
 * The flip preserves the numeric ordering under lexicographic byte comparison, which is what
 * DynamoDB uses for Sort Keys.
 */
public final class DynamoKeyCodec {

    private DynamoKeyCodec() {}

    /** Encode a TEXT column (UTF-8 + 0x00 terminator). */
    public static byte[] encodeText(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Text value must not be null");
        }
        if (value.indexOf('\u0000') >= 0) {
            throw new IllegalArgumentException(
                    "Text value must not contain U+0000 (ScalarDB KeyBytesEncoder restriction)");
        }
        byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[utf8.length + 1];
        System.arraycopy(utf8, 0, out, 0, utf8.length);
        out[utf8.length] = 0x00;
        return out;
    }

    /** Encode an INT column as 4 sign-flipped big-endian bytes. */
    public static byte[] encodeInt(int value) {
        byte[] out = new byte[4];
        out[0] = (byte) ((value >> 24) ^ 0x80);
        out[1] = (byte) (value >> 16);
        out[2] = (byte) (value >> 8);
        out[3] = (byte) value;
        return out;
    }

    /** Encode a BIGINT column as 8 sign-flipped big-endian bytes. */
    public static byte[] encodeBigInt(long value) {
        byte[] out = new byte[8];
        out[0] = (byte) ((value >> 56) ^ 0x80);
        out[1] = (byte) (value >> 48);
        out[2] = (byte) (value >> 40);
        out[3] = (byte) (value >> 32);
        out[4] = (byte) (value >> 24);
        out[5] = (byte) (value >> 16);
        out[6] = (byte) (value >> 8);
        out[7] = (byte) value;
        return out;
    }

    /** {@code concatenatedPartitionKey} for outbox/inbox (single TEXT column {@code event_type}). */
    public static ByteBuffer buildPartitionKey(String eventType) {
        return ByteBuffer.wrap(encodeText(eventType));
    }

    /** {@code concatenatedClusteringKey} for {@code re_outbox} (single TEXT column {@code event_id}). */
    public static ByteBuffer buildOutboxClusteringKey(String eventId) {
        return ByteBuffer.wrap(encodeText(eventId));
    }

    /**
     * {@code concatenatedClusteringKey} for {@code re_inbox}:
     * {@code partition (BIGINT) | event_id (TEXT) | step_id (INT) | seq (INT)}.
     *
     * <p>partition is BIGINT (was INT in v1) to support reserved Replay partition range
     * (10000+) including timestamp-encoded values like {@code 202604271742}.
     */
    public static ByteBuffer buildInboxClusteringKey(long partition, String eventId, int stepId, int seq) {
        byte[] partitionBytes = encodeBigInt(partition);
        byte[] eventIdBytes = encodeText(eventId);
        byte[] stepIdBytes = encodeInt(stepId);
        byte[] seqBytes = encodeInt(seq);

        ByteBuffer buf = ByteBuffer.allocate(
                partitionBytes.length + eventIdBytes.length + stepIdBytes.length + seqBytes.length);
        buf.put(partitionBytes);
        buf.put(eventIdBytes);
        buf.put(stepIdBytes);
        buf.put(seqBytes);
        buf.flip();
        return buf;
    }
}
