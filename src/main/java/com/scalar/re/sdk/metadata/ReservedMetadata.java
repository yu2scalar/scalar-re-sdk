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
package com.scalar.re.sdk.metadata;

import com.fasterxml.jackson.databind.JsonNode;
import com.scalar.re.sdk.error.ReInputException;
import com.scalar.re.sdk.error.ReSdkError;
import com.scalar.re.sdk.model.Sequence;
import com.scalar.re.sdk.model.Step;
import java.util.List;

/**
 * Validation and reading of the reserved metadata namespace {@code metadata.re} (data-model §4.5).
 *
 * <p>The producer carries partition / ordinal hints under {@code metadata.re} as a nested object
 * (e.g. {@code {"re":{"partition":3,"ordinal":5}}}), not dotted keys. The SDK builder and the RE
 * core's pre-TX validation both call {@link #validateValues} and {@link #validateOrderedShape}, so a
 * body written to the outbox without the SDK is held to the same rules.
 */
public final class ReservedMetadata {

    /** Reserved metadata namespace object key. */
    public static final String KEY = "re";
    /** {@code metadata.re.partition}: the partition to place the delivery in. */
    public static final String PARTITION = "partition";
    /** {@code metadata.re.ordinal}: per-partition gapless sequence number (ordered only). */
    public static final String ORDINAL = "ordinal";

    /**
     * Upper bound of the normal partition range (data-model §3). {@code re.partition} must be in
     * {@code [0, 9999]}; {@code [10000, BIGINT_MAX]} is reserved for Replay.
     */
    public static final int MAX_NORMAL_PARTITION = 9999;

    private static final String ORDERED_FAMILY_PREFIX = "ordered_";

    private ReservedMetadata() {}

    /** Whether the delivery type belongs to the ordered family ({@code ordered_atomic}, ...). */
    public static boolean isOrderedFamily(String deliveryType) {
        return deliveryType != null && deliveryType.startsWith(ORDERED_FAMILY_PREFIX);
    }

    /**
     * Value rules for every delivery type: {@code metadata.re}, when present, is an object;
     * {@code re.partition}, when present, is an integer in {@code [0, MAX_NORMAL_PARTITION]};
     * {@code re.ordinal}, when present, is a non-negative integer. Absent {@code metadata} / absent
     * {@code re} = nothing to validate.
     *
     * @throws ReInputException on the first violation
     */
    public static void validateValues(List<Step> steps) {
        if (steps == null) return;
        for (Step step : steps) {
            if (step.getSequences() == null) continue;
            for (Sequence seq : step.getSequences()) {
                JsonNode metadata = seq.getMetadata();
                if (metadata == null || !metadata.isObject()) continue;
                JsonNode re = metadata.get(KEY);
                if (re == null || re.isNull()) continue;
                if (!re.isObject()) {
                    throw new ReInputException(ReSdkError.RE_PARTITION_OUT_OF_RANGE,
                            MAX_NORMAL_PARTITION, "metadata.re is not an object: " + re.getNodeType());
                }
                validatePartition(re.get(PARTITION));
                validateOrdinal(re.get(ORDINAL));
            }
        }
    }

    /**
     * Shape rules shared by every ordered_* variant (delivery-ordered.md §2.1): exactly 1 step,
     * exactly 1 sequence per step, and both {@code re.partition} and {@code re.ordinal} present.
     * Value ranges are {@link #validateValues}'s job; this checks presence only.
     *
     * @throws ReInputException on the first violation
     */
    public static void validateOrderedShape(String deliveryType, List<Step> steps) {
        int stepCount = steps == null ? 0 : steps.size();
        if (stepCount != 1) {
            throw new ReInputException(ReSdkError.MULTI_STEP_NOT_SUPPORTED, deliveryType, stepCount);
        }
        List<Sequence> sequences = steps.get(0).getSequences();
        if (sequences == null || sequences.size() != 1) {
            throw new ReInputException(ReSdkError.ORDERED_SINGLE_SEQUENCE_REQUIRED,
                    deliveryType, sequences == null ? 0 : sequences.size());
        }
        JsonNode re = reNode(sequences.get(0).getMetadata());
        JsonNode partition = re == null ? null : re.get(PARTITION);
        JsonNode ordinal = re == null ? null : re.get(ORDINAL);
        if (partition == null || partition.isNull()) {
            throw new ReInputException(ReSdkError.ORDERED_PARTITION_REQUIRED, deliveryType);
        }
        if (ordinal == null || ordinal.isNull()) {
            throw new ReInputException(ReSdkError.ORDERED_ORDINAL_REQUIRED, deliveryType);
        }
    }

    /**
     * Read {@code re.partition} from the first sequence that carries one (an event maps to one
     * partition), or null if absent. Tolerant: a non-integer value reads as absent.
     */
    public static Long readPartition(List<Step> steps) {
        return readFirst(steps, PARTITION);
    }

    /**
     * Read {@code re.ordinal} from the first sequence that carries one, or null if absent.
     * Tolerant: a non-integer value reads as absent.
     */
    public static Long readOrdinal(List<Step> steps) {
        return readFirst(steps, ORDINAL);
    }

    /**
     * Read {@code re.ordinal} from a single metadata node (e.g. the simplified inbox body's
     * {@code metadata}), or null if absent / not an integer.
     */
    public static Long readOrdinal(JsonNode metadata) {
        return readLong(reNode(metadata), ORDINAL);
    }

    private static Long readFirst(List<Step> steps, String field) {
        if (steps == null) return null;
        for (Step step : steps) {
            if (step.getSequences() == null) continue;
            for (Sequence seq : step.getSequences()) {
                Long value = readLong(reNode(seq.getMetadata()), field);
                if (value != null) return value;
            }
        }
        return null;
    }

    private static JsonNode reNode(JsonNode metadata) {
        if (metadata == null || !metadata.isObject()) return null;
        JsonNode re = metadata.get(KEY);
        return re != null && re.isObject() ? re : null;
    }

    private static Long readLong(JsonNode re, String field) {
        if (re == null) return null;
        JsonNode value = re.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) return null;
        return value.asLong();
    }

    private static void validatePartition(JsonNode partition) {
        if (partition == null || partition.isNull()) return;  // absent = LB by hash (§4.5)
        if (!partition.isIntegralNumber() || !partition.canConvertToLong()) {
            throw new ReInputException(ReSdkError.RE_PARTITION_OUT_OF_RANGE,
                    MAX_NORMAL_PARTITION, partition.asText());
        }
        long value = partition.asLong();
        if (value < 0 || value > MAX_NORMAL_PARTITION) {
            throw new ReInputException(ReSdkError.RE_PARTITION_OUT_OF_RANGE,
                    MAX_NORMAL_PARTITION, Long.toString(value));
        }
    }

    private static void validateOrdinal(JsonNode ordinal) {
        if (ordinal == null || ordinal.isNull()) return;  // optional (ordered only)
        if (!ordinal.isIntegralNumber() || !ordinal.canConvertToLong() || ordinal.asLong() < 0) {
            throw new ReInputException(ReSdkError.RE_ORDINAL_INVALID, ordinal.asText());
        }
    }
}
