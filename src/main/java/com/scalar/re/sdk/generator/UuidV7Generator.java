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
package com.scalar.re.sdk.generator;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Generates UUIDv7 event ids (RFC 9562 bit layout:
 * {@code [48bit unix_ts_ms][4bit ver=0111][12bit rand_a][2bit var=10][62bit rand_b]}).
 *
 * <p>Two flavors are provided:
 *
 * <ul>
 *   <li>{@link #generate()} — plain UUIDv7. The 74 usable bits ({@code rand_a} +
 *       {@code rand_b}) are random. Use this for every delivery type except ordered.
 *   <li>{@link #generateOrdered(long)} — ordered variant for {@code ordered_*} delivery
 *       types. The low {@value #ORDINAL_BITS} bits of the producer-assigned ordinal are
 *       packed into {@code rand_a} (top 12 bits) and the top 33 bits of {@code rand_b},
 *       leaving the low {@value #RANDOM_BITS} bits of {@code rand_b} random. Version and
 *       variant bits are preserved, so the result is still a valid UUIDv7. Within one
 *       millisecond this makes the lexicographic order of event ids equal to the ordinal
 *       order (the packed bits act as a same-ms tiebreaker); across milliseconds the
 *       48-bit timestamp already orders the ids.
 * </ul>
 *
 * <p><b>Contract:</b> for {@code ordered_*} delivery types the event_id lexicographic order
 * MUST equal the ordinal order within a partition lane (delivery-ordered.md ADR D6). RE
 * scans, pages, and diagnoses gaps by event_id order (the inbox clustering key), so a
 * misaligned id can push the expected ordinal beyond a page boundary or a cursor and shows
 * up as {@code gap.status = ORDER_VIOLATION} at Poll time. Use {@link #generateOrdered(long)}
 * (or a scheme with the same guarantee) for every ordered event; plain {@link #generate()}
 * satisfies the contract only across milliseconds, not within one. The authoritative,
 * unbounded ordinal still travels as {@code metadata.re.ordinal} in the event body, and gap
 * detection compares that value — consumers never need to parse bits out of the event id.
 *
 * <p>Uniqueness of the ordered variant is probabilistic, like every UUID: a collision
 * requires two events in the same millisecond, with the same low-45-bit ordinal (i.e. on
 * different partitions), and the same 29 random bits.
 */
public final class UuidV7Generator {

    /** Number of low ordinal bits packed into the id as the same-ms tiebreaker. */
    public static final int ORDINAL_BITS = 45;

    /** Number of random bits kept in {@code rand_b} for cross-partition uniqueness. */
    public static final int RANDOM_BITS = 29;

    /** Maximum ordinal value that fits into the packed bits without wrapping. */
    public static final long MAX_PACKED_ORDINAL = (1L << ORDINAL_BITS) - 1;

    private static final long ORDINAL_MASK = MAX_PACKED_ORDINAL;
    private static final long RANDOM_MASK = (1L << RANDOM_BITS) - 1;
    private static final long ORDINAL_LOW_33_MASK = (1L << 33) - 1;

    private static final SecureRandom random = new SecureRandom();

    private UuidV7Generator() {}

    /**
     * Generates a plain UUIDv7 string (timestamp-ordered, fully random payload bits).
     *
     * @return a UUIDv7 string such as {@code 0190a1b2-...}
     */
    public static String generate() {
        long timestamp = System.currentTimeMillis();
        long msb = ((timestamp & 0xFFFFFFFFFFFFL) << 16)
                | 0x7000L
                | (random.nextLong() & 0x0FFFL);
        long lsb = (random.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(msb, lsb).toString();
    }

    /**
     * Generates an ordered UUIDv7 string with the ordinal packed as a same-ms tiebreaker.
     *
     * <p>Only the low {@value #ORDINAL_BITS} bits of {@code ordinal} are packed (values
     * above {@link #MAX_PACKED_ORDINAL} wrap in the packed bits — ordering within one
     * millisecond is still correct for consecutive ordinals, and the full value is
     * carried in {@code metadata.re.ordinal}).
     *
     * @param ordinal the producer-assigned, per-partition gapless ordinal (non-negative)
     * @return a UUIDv7 string whose lexicographic order follows the ordinal within a millisecond
     * @throws IllegalArgumentException if {@code ordinal} is negative
     */
    public static String generateOrdered(long ordinal) {
        return generateOrdered(ordinal, System.currentTimeMillis());
    }

    // Visible for tests: fixed timestamp makes ordering assertions deterministic.
    static String generateOrdered(long ordinal, long timestampMs) {
        if (ordinal < 0) {
            throw new IllegalArgumentException("Ordinal must be non-negative: " + ordinal);
        }
        long packed = ordinal & ORDINAL_MASK;
        long randA = packed >>> 33; // top 12 of the 45 packed bits
        long ordinalLow33 = packed & ORDINAL_LOW_33_MASK;
        long msb = ((timestampMs & 0xFFFFFFFFFFFFL) << 16) | 0x7000L | randA;
        long lsb = 0x8000000000000000L
                | (ordinalLow33 << RANDOM_BITS)
                | (random.nextLong() & RANDOM_MASK);
        return new UUID(msb, lsb).toString();
    }
}
