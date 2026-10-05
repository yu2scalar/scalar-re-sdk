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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scalar.re.sdk.error.ReInputException;
import com.scalar.re.sdk.error.ReSdkError;
import com.scalar.re.sdk.model.Sequence;
import com.scalar.re.sdk.model.Step;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * §2.2.1 boundary-value + full-branch UT for {@link ReservedMetadata} (data-model §4.5): the single
 * rule set the SDK builder and the RE core pre-TX validation both use.
 */
class ReservedMetadataTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String OK_RE = "{\"re\":{\"partition\":3,\"ordinal\":5}}";

    private static JsonNode json(String s) {
        try {
            return MAPPER.readTree(s);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Sequence seq(String metadataJson) {
        return new Sequence(1, List.of(), json("{}"), metadataJson == null ? null : json(metadataJson));
    }

    private static List<Step> steps(Sequence... sequences) {
        return List.of(new Step(1, List.of(sequences)));
    }

    private static List<Step> re(String reJson) {
        return steps(seq("{\"re\":" + reJson + "}"));
    }

    private static ReSdkError errorOf(Runnable r) {
        try {
            r.run();
        } catch (ReInputException e) {
            return (ReSdkError) e.getError();
        }
        throw new AssertionError("Expected ReInputException");
    }

    // ---- isOrderedFamily ----

    @Test
    void isOrderedFamily_orderedVariants_true() {
        assertThat(ReservedMetadata.isOrderedFamily("ordered_atomic")).isTrue();
        assertThat(ReservedMetadata.isOrderedFamily("ordered_partial")).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"atomic", "partial", "relay", "pull", "qpull", "spull", "ordered", ""})
    void isOrderedFamily_others_false(String deliveryType) {
        assertThat(ReservedMetadata.isOrderedFamily(deliveryType)).isFalse();
    }

    // ---- validateValues: structure branches ----

    @Test
    void validateValues_nothingToValidate_passes() {
        assertThatCode(() -> ReservedMetadata.validateValues(null)).doesNotThrowAnyException();
        assertThatCode(() -> ReservedMetadata.validateValues(List.of())).doesNotThrowAnyException();
        assertThatCode(() -> ReservedMetadata.validateValues(List.of(new Step(1, null))))
                .doesNotThrowAnyException();
        assertThatCode(() -> ReservedMetadata.validateValues(steps(seq(null)))).doesNotThrowAnyException();
        assertThatCode(() -> ReservedMetadata.validateValues(steps(seq("[1]")))).doesNotThrowAnyException();
        assertThatCode(() -> ReservedMetadata.validateValues(steps(seq("{\"trace_id\":\"t\"}"))))
                .doesNotThrowAnyException();
        assertThatCode(() -> ReservedMetadata.validateValues(re("null"))).doesNotThrowAnyException();
        assertThatCode(() -> ReservedMetadata.validateValues(re("{}"))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"3", "\"x\"", "[1]", "true"})
    void validateValues_reNotObject_rejected(String reJson) {
        assertThat(errorOf(() -> ReservedMetadata.validateValues(re(reJson))))
                .isEqualTo(ReSdkError.RE_PARTITION_OUT_OF_RANGE);
    }

    // ---- validateValues: re.partition boundaries ----

    @ParameterizedTest
    @ValueSource(strings = {"0", "1", "9998", "9999", "null"})
    void validateValues_partitionInRangeOrNull_passes(String partition) {
        assertThatCode(() -> ReservedMetadata.validateValues(re("{\"partition\":" + partition + "}")))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "10000", "10001", "9223372036854775807", "-9223372036854775808",
            "\"3\"", "3.5", "123456789012345678901234567890", "{}"})
    void validateValues_partitionOutOfRangeOrNotInteger_rejected(String partition) {
        assertThat(errorOf(() -> ReservedMetadata.validateValues(re("{\"partition\":" + partition + "}"))))
                .isEqualTo(ReSdkError.RE_PARTITION_OUT_OF_RANGE);
    }

    // ---- validateValues: re.ordinal boundaries ----

    @ParameterizedTest
    @ValueSource(strings = {"0", "1", "9223372036854775807", "null"})
    void validateValues_ordinalNonNegativeOrNull_passes(String ordinal) {
        assertThatCode(() -> ReservedMetadata.validateValues(re("{\"ordinal\":" + ordinal + "}")))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "-9223372036854775808", "\"1\"", "1.5", "123456789012345678901234567890"})
    void validateValues_ordinalNegativeOrNotInteger_rejected(String ordinal) {
        assertThat(errorOf(() -> ReservedMetadata.validateValues(re("{\"ordinal\":" + ordinal + "}"))))
                .isEqualTo(ReSdkError.RE_ORDINAL_INVALID);
    }

    @Test
    void validateValues_checksEverySequence_laterViolationRejected() {
        List<Step> body = List.of(
                new Step(1, List.of(seq("{\"re\":{\"partition\":1}}"))),
                new Step(2, List.of(seq("{\"re\":{\"partition\":1}}"), seq("{\"re\":{\"partition\":10000}}"))));
        assertThat(errorOf(() -> ReservedMetadata.validateValues(body)))
                .isEqualTo(ReSdkError.RE_PARTITION_OUT_OF_RANGE);
    }

    // ---- validateOrderedShape ----

    @Test
    void validateOrderedShape_oneStepOneSequenceBothPresent_passes() {
        assertThatCode(() -> ReservedMetadata.validateOrderedShape("ordered_atomic", steps(seq(OK_RE))))
                .doesNotThrowAnyException();
    }

    @Test
    void validateOrderedShape_stepCountNotOne_rejected() {
        assertThat(errorOf(() -> ReservedMetadata.validateOrderedShape("ordered_atomic", null)))
                .isEqualTo(ReSdkError.MULTI_STEP_NOT_SUPPORTED);
        assertThat(errorOf(() -> ReservedMetadata.validateOrderedShape("ordered_atomic", List.of())))
                .isEqualTo(ReSdkError.MULTI_STEP_NOT_SUPPORTED);
        List<Step> two = List.of(new Step(1, List.of(seq(OK_RE))), new Step(2, List.of(seq(OK_RE))));
        assertThat(errorOf(() -> ReservedMetadata.validateOrderedShape("ordered_atomic", two)))
                .isEqualTo(ReSdkError.MULTI_STEP_NOT_SUPPORTED);
    }

    @Test
    void validateOrderedShape_sequenceCountNotOne_rejected() {
        assertThat(errorOf(() -> ReservedMetadata.validateOrderedShape("ordered_atomic",
                List.of(new Step(1, null))))).isEqualTo(ReSdkError.ORDERED_SINGLE_SEQUENCE_REQUIRED);
        assertThat(errorOf(() -> ReservedMetadata.validateOrderedShape("ordered_atomic",
                List.of(new Step(1, new ArrayList<>()))))).isEqualTo(ReSdkError.ORDERED_SINGLE_SEQUENCE_REQUIRED);
        assertThat(errorOf(() -> ReservedMetadata.validateOrderedShape("ordered_atomic",
                steps(seq(OK_RE), seq(OK_RE))))).isEqualTo(ReSdkError.ORDERED_SINGLE_SEQUENCE_REQUIRED);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"[1]", "{}", "{\"re\":3}", "{\"re\":{}}", "{\"re\":{\"ordinal\":5}}",
            "{\"re\":{\"partition\":null,\"ordinal\":5}}"})
    void validateOrderedShape_partitionMissing_rejected(String metadata) {
        assertThat(errorOf(() -> ReservedMetadata.validateOrderedShape("ordered_atomic", steps(seq(metadata)))))
                .isEqualTo(ReSdkError.ORDERED_PARTITION_REQUIRED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"re\":{\"partition\":3}}", "{\"re\":{\"partition\":3,\"ordinal\":null}}"})
    void validateOrderedShape_ordinalMissing_rejected(String metadata) {
        assertThat(errorOf(() -> ReservedMetadata.validateOrderedShape("ordered_atomic", steps(seq(metadata)))))
                .isEqualTo(ReSdkError.ORDERED_ORDINAL_REQUIRED);
    }

    @Test
    void validateOrderedShape_messageNamesDeliveryType() {
        assertThatThrownBy(() -> ReservedMetadata.validateOrderedShape("ordered_atomic", List.of()))
                .hasMessageContaining("ordered_atomic");
    }

    // ---- readers ----

    @Test
    void readPartition_absentOrNotInteger_null() {
        assertThat(ReservedMetadata.readPartition(null)).isNull();
        assertThat(ReservedMetadata.readPartition(List.of(new Step(1, null)))).isNull();
        assertThat(ReservedMetadata.readPartition(steps(seq(null)))).isNull();
        assertThat(ReservedMetadata.readPartition(steps(seq("{\"re\":3}")))).isNull();
        assertThat(ReservedMetadata.readPartition(re("{}"))).isNull();
        assertThat(ReservedMetadata.readPartition(re("{\"partition\":\"3\"}"))).isNull();
        assertThat(ReservedMetadata.readPartition(re("{\"partition\":123456789012345678901234567890}"))).isNull();
    }

    @Test
    void readPartition_firstCarrierWins() {
        List<Step> body = List.of(
                new Step(1, List.of(seq(null), seq("{\"re\":{\"partition\":7}}"))),
                new Step(2, List.of(seq("{\"re\":{\"partition\":8}}"))));
        assertThat(ReservedMetadata.readPartition(body)).isEqualTo(7L);
        assertThat(ReservedMetadata.readPartition(re("{\"partition\":0}"))).isZero();
    }

    @Test
    void readOrdinal_steps_firstCarrierWinsOrNull() {
        assertThat(ReservedMetadata.readOrdinal((List<Step>) null)).isNull();
        assertThat(ReservedMetadata.readOrdinal(re("{\"partition\":1}"))).isNull();
        List<Step> body = List.of(new Step(1, List.of(seq("{}"), seq("{\"re\":{\"ordinal\":42}}"))));
        assertThat(ReservedMetadata.readOrdinal(body)).isEqualTo(42L);
    }

    @Test
    void readOrdinal_metadataNode() {
        assertThat(ReservedMetadata.readOrdinal((JsonNode) null)).isNull();
        assertThat(ReservedMetadata.readOrdinal(json("[1]"))).isNull();
        assertThat(ReservedMetadata.readOrdinal(json("{\"re\":[1]}"))).isNull();
        assertThat(ReservedMetadata.readOrdinal(json("{\"re\":{\"ordinal\":\"5\"}}"))).isNull();
        assertThat(ReservedMetadata.readOrdinal(json("{\"re\":{\"ordinal\":5}}"))).isEqualTo(5L);
    }
}
