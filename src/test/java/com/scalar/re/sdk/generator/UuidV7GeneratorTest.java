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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UuidV7GeneratorTest {

    private static final long FIXED_TS = 0x0190_A1B2_C3D4L; // arbitrary 48-bit epoch ms

    private static long timestampOf(String id) {
        return UUID.fromString(id).getMostSignificantBits() >>> 16;
    }

    private static long packedOrdinalOf(String id) {
        UUID uuid = UUID.fromString(id);
        long randA = uuid.getMostSignificantBits() & 0x0FFFL;
        long ordinalLow33 = (uuid.getLeastSignificantBits() >>> UuidV7Generator.RANDOM_BITS)
                & ((1L << 33) - 1);
        return (randA << 33) | ordinalLow33;
    }

    @Nested
    class Plain {

        @Test
        void generate_hasVersion7AndVariant2AndCurrentTimestamp() {
            long before = System.currentTimeMillis();
            String id = UuidV7Generator.generate();
            long after = System.currentTimeMillis();

            UUID uuid = UUID.fromString(id);
            assertThat(uuid.version()).isEqualTo(7);
            assertThat(uuid.variant()).isEqualTo(2);
            assertThat(timestampOf(id)).isBetween(before, after);
        }

        @Test
        void generate_producesDistinctIds() {
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < 1000; i++) {
                ids.add(UuidV7Generator.generate());
            }
            assertThat(ids).hasSize(1000);
        }
    }

    @Nested
    class Ordered {

        @Test
        void generateOrdered_negativeOrdinal_throws() {
            assertThatThrownBy(() -> UuidV7Generator.generateOrdered(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("non-negative");
        }

        @Test
        void generateOrdered_zeroOrdinal_isAcceptedAndPacksZero() {
            String id = UuidV7Generator.generateOrdered(0, FIXED_TS);
            assertThat(packedOrdinalOf(id)).isZero();
        }

        @Test
        void generateOrdered_packsOrdinalBitsAndKeepsVersionVariantTimestamp() {
            // Boundaries of the 45-bit packed field: 0 / 1 / rand_a-only edge (2^33) /
            // max / max+1 (wraps to 0) / a value exercising both halves.
            long[] ordinals = {
                0L, 1L, 1L << 33, UuidV7Generator.MAX_PACKED_ORDINAL,
                UuidV7Generator.MAX_PACKED_ORDINAL + 1, 0x1234_5678_9ABL
            };
            long[] expectedPacked = {
                0L, 1L, 1L << 33, UuidV7Generator.MAX_PACKED_ORDINAL, 0L, 0x1234_5678_9ABL
            };
            for (int i = 0; i < ordinals.length; i++) {
                String id = UuidV7Generator.generateOrdered(ordinals[i], FIXED_TS);
                UUID uuid = UUID.fromString(id);
                assertThat(uuid.version()).as("version for ordinal %d", ordinals[i]).isEqualTo(7);
                assertThat(uuid.variant()).as("variant for ordinal %d", ordinals[i]).isEqualTo(2);
                assertThat(timestampOf(id)).isEqualTo(FIXED_TS);
                assertThat(packedOrdinalOf(id))
                        .as("packed ordinal for ordinal %d", ordinals[i])
                        .isEqualTo(expectedPacked[i]);
            }
        }

        @Test
        void generateOrdered_sameMillisecond_lexicographicOrderFollowsOrdinal() {
            // Consecutive ordinals plus the rand_a/rand_b boundary; random tail bits are
            // below every packed bit, so ordering must be deterministic.
            long[] ordinals = {0, 1, 2, (1L << 33) - 1, 1L << 33, (1L << 33) + 1,
                    UuidV7Generator.MAX_PACKED_ORDINAL};
            List<String> generated = new ArrayList<>();
            for (long ordinal : ordinals) {
                generated.add(UuidV7Generator.generateOrdered(ordinal, FIXED_TS));
            }
            List<String> sorted = new ArrayList<>(generated);
            sorted.sort(String::compareTo);
            assertThat(generated).isEqualTo(sorted);
        }

        @Test
        void generateOrdered_laterMillisecond_ordersAboveEarlierRegardlessOfOrdinal() {
            String earlierMsHighOrdinal =
                    UuidV7Generator.generateOrdered(UuidV7Generator.MAX_PACKED_ORDINAL, FIXED_TS);
            String laterMsLowOrdinal = UuidV7Generator.generateOrdered(0, FIXED_TS + 1);
            assertThat(laterMsLowOrdinal).isGreaterThan(earlierMsHighOrdinal);
        }

        @Test
        void generateOrdered_sameOrdinal_differsInRandomTail() {
            String first = UuidV7Generator.generateOrdered(42, FIXED_TS);
            String second = UuidV7Generator.generateOrdered(42, FIXED_TS);
            // 29 random bits: two draws colliding is ~2^-29 — treat equality as failure.
            assertThat(first).isNotEqualTo(second);
            assertThat(packedOrdinalOf(first)).isEqualTo(packedOrdinalOf(second)).isEqualTo(42);
        }

        @Test
        void generateOrdered_publicOverload_usesCurrentTime() {
            long before = System.currentTimeMillis();
            String id = UuidV7Generator.generateOrdered(7);
            long after = System.currentTimeMillis();
            assertThat(timestampOf(id)).isBetween(before, after);
            assertThat(packedOrdinalOf(id)).isEqualTo(7);
        }
    }
}
