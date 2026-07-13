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
package com.scalar.re.sdk.builder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scalar.re.sdk.error.ReInputException;
import com.scalar.re.sdk.error.ReSdkError;
import com.scalar.re.sdk.model.ReEventBody;
import com.scalar.re.sdk.model.RoutingDestination;
import com.scalar.re.sdk.model.Sequence;
import com.scalar.re.sdk.model.Step;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ReEventBodyBuilder {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** Reserved metadata namespace object key (data-model §4.5). {@code metadata.re.{partition,ordinal}}. */
    private static final String RESERVED_METADATA_KEY = "re";
    /**
     * Upper bound of the normal partition range (data-model §3, {@code ReConstants.MAX_NORMAL_PARTITION}).
     * {@code re.partition} must be in {@code [0, 9999]}; {@code [10000, BIGINT_MAX]} is Replay-reserved.
     */
    private static final long MAX_NORMAL_PARTITION = 9999L;

    private String deliveryType = "relay";
    private final List<Step> steps = new ArrayList<>();

    private ReEventBodyBuilder() {
    }

    public static ReEventBodyBuilder create() {
        return new ReEventBodyBuilder();
    }

    /**
     * Set delivery type for validation purposes only.
     * This value is NOT written to the body JSON.
     * delivery-type is defined in EventType Properties.
     */
    public ReEventBodyBuilder deliveryType(String deliveryType) {
        this.deliveryType = deliveryType;
        return this;
    }

    public ReEventBodyBuilder addStep(Consumer<StepBuilder> configurator) {
        StepBuilder builder = new StepBuilder();
        configurator.accept(builder);
        steps.add(builder.build());
        return this;
    }

    public ReEventBodyBuilder addStep(Step step) {
        steps.add(step);
        return this;
    }

    public ReEventBody build() {
        validate();
        return new ReEventBody(List.copyOf(steps));
    }

    public String toJson() throws JsonProcessingException {
        return objectMapper.writeValueAsString(build());
    }

    public String toJsonPretty() throws JsonProcessingException {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(build());
    }

    public NotifyPayload toNotifyPayload() throws JsonProcessingException {
        ReEventBody built = build();
        String bodyJson = objectMapper.writeValueAsString(built);
        return new NotifyPayload(bodyJson, built.getFirstStepRoutingNamespaces());
    }

    private void validate() {
        if (deliveryType == null) {
            throw new ReInputException(ReSdkError.DELIVERY_TYPE_REQUIRED);
        }

        // Universal-reject (whitelist principle, §1.17): ScalarRE-managed step fields must never be
        // set on a producer body, regardless of delivery type. The core populates them and ignores
        // any producer-supplied value, so accepting them silently would be a dead field.
        validateNoReservedFields();

        // Reserved metadata namespace re.* (data-model §4.5): producer MAY set re.partition /
        // re.ordinal under metadata.re (a nested object, not dotted keys). These are accepted
        // (unlike the rejected step fields above) but value-validated regardless of delivery type:
        // re.partition is delivery-type-agnostic; re.ordinal is honored only for ordered but its
        // value is still checked when present.
        validateReservedMetadata();

        switch (deliveryType) {
            case "partial":
                validatePartial();
                break;
            case "pull":
                validatePull();
                break;
            case "qpull":
                validateQueuePull();
                break;
            case "spull":
                validateSpull();
                break;
            case "atomic":
                // atomic accepts only the common fields — relay-only fields are rejected.
                validateNoRelayFields("atomic");
                break;
            case "ordered_atomic":
                // ordered family (atomic base): ordered common contract + atomic body routing.
                validateOrderedAtomic();
                break;
            case "relay":
                // relay is the only type that accepts decision / on_failure / on_failure_payload.
                break;
            default:
                throw new ReInputException(ReSdkError.UNKNOWN_DELIVERY_TYPE, deliveryType);
        }
    }

    private void validateNoReservedFields() {
        for (Step step : steps) {
            if (step.getAckStatus() != null) {
                throw new ReInputException(ReSdkError.RESERVED_FIELD_SET, "ack_status");
            }
        }
    }

    /**
     * Validate the reserved {@code re.*} metadata namespace (data-model §4.5). The producer carries
     * partition / ordinal hints under {@code metadata.re} as a nested object
     * (e.g. {@code {"re":{"partition":3,"ordinal":5}}}) — not dotted keys. Validation runs over
     * every sequence's metadata regardless of delivery type:
     * <ul>
     *   <li>{@code re.partition} (delivery-type-agnostic): integer in {@code [0, MAX_NORMAL_PARTITION]}
     *       (= {@code [0, 9999]}). {@code >= 10000} is the Replay-reserved range and is rejected.</li>
     *   <li>{@code re.ordinal} (honored only for ordered, ignored otherwise): when present, a
     *       non-negative integer.</li>
     * </ul>
     * Absent {@code metadata} / absent {@code re} object = nothing to validate (passes).
     */
    private void validateReservedMetadata() {
        for (Step step : steps) {
            if (step.getSequences() == null) continue;
            for (Sequence seq : step.getSequences()) {
                JsonNode metadata = seq.getMetadata();
                if (metadata == null || !metadata.isObject()) continue;
                JsonNode re = metadata.get(RESERVED_METADATA_KEY);
                if (re == null || re.isNull()) continue;
                if (!re.isObject()) {
                    // metadata.re must be the reserved namespace object, not a scalar/array.
                    throw new ReInputException(ReSdkError.RE_PARTITION_OUT_OF_RANGE,
                            MAX_NORMAL_PARTITION, "metadata.re is not an object: " + re.getNodeType());
                }
                validateRePartition(re.get("partition"));
                validateReOrdinal(re.get("ordinal"));
            }
        }
    }

    private void validateRePartition(JsonNode partition) {
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

    private void validateReOrdinal(JsonNode ordinal) {
        if (ordinal == null || ordinal.isNull()) return;  // optional (ordered only)
        if (!ordinal.isIntegralNumber() || !ordinal.canConvertToLong() || ordinal.asLong() < 0) {
            throw new ReInputException(ReSdkError.RE_ORDINAL_INVALID, ordinal.asText());
        }
    }

    private void validatePartial() {
        if (steps.size() > 1) {
            throw new ReInputException(ReSdkError.MULTI_STEP_NOT_SUPPORTED, "partial", steps.size());
        }
        for (Step step : steps) {
            if (step.getDecision() != null) {
                throw new ReInputException(ReSdkError.FIELD_NOT_SUPPORTED, "partial", "decision");
            }
            if (step.getOnFailure() != null && !step.getOnFailure().isEmpty()) {
                throw new ReInputException(ReSdkError.FIELD_NOT_SUPPORTED, "partial", "on_failure");
            }
            if (step.getOnFailurePayload() != null && !step.getOnFailurePayload().isEmpty()) {
                throw new ReInputException(ReSdkError.FIELD_NOT_SUPPORTED, "partial", "on_failure_payload");
            }
        }
    }

    /**
     * Pull: single step, and the body MUST NOT carry any routing destinations.
     * Pull delivers to the single fixed destination configured on the EventType
     * (config {@code destination}); the core embeds it as the sole routing at
     * transfer time, the same way spull embeds the subscriber.
     */
    private void validatePull() {
        if (steps.size() > 1) {
            throw new ReInputException(ReSdkError.MULTI_STEP_NOT_SUPPORTED, "pull", steps.size());
        }
        validateNoRelayFields("pull");
        for (Step step : steps) {
            for (Sequence seq : step.getSequences()) {
                List<RoutingDestination> routing = seq.getRouting();
                if (routing != null && !routing.isEmpty()) {
                    throw new ReInputException(
                            ReSdkError.PULL_ROUTING_NOT_ACCEPTED, seq.getSeq(), routing.size());
                }
            }
        }
    }

    /**
     * QPull: multiple steps allowed, fan-out via body routing destinations.
     */
    private void validateQueuePull() {
        validateNoRelayFields("qpull");
    }

    /**
     * SPull: subscriber-based routing — body MUST NOT contain any routing destinations.
     * True destinations are resolved from the subscription cache at transfer time.
     * Any routing in the body is a design violation and is rejected here.
     */
    private void validateSpull() {
        validateNoRelayFields("spull");
        for (Step step : steps) {
            for (Sequence seq : step.getSequences()) {
                List<RoutingDestination> routing = seq.getRouting();
                if (routing != null && !routing.isEmpty()) {
                    throw new ReInputException(
                            ReSdkError.SPULL_ROUTING_NOT_ACCEPTED, seq.getSeq(), routing.size());
                }
            }
        }
    }

    /**
     * Ordered (atomic base) contract validation (delivery-ordered.md §2.1, D7/D8). The transfer
     * itself is identical to atomic — the ordered-specific surface is this thin producer contract
     * (plus RE-side Poll continuity, which is not a builder concern):
     * <ul>
     *   <li>ordered <b>common</b> ({@link #validateOrderedCommon}): exactly 1 step, exactly 1
     *       sequence per step, {@code metadata.re.partition} and {@code metadata.re.ordinal} present
     *       on the sequence, no relay-only fields. This keeps the invariant "1 event = 1 message =
     *       1 ordinal in 1 partition", which the consumer's continuity check relies on.</li>
     *   <li>atomic <b>base</b>: body routing required (single destination or broadcast). Future
     *       {@code ordered_partial} reuses {@link #validateOrderedCommon}; {@code ordered_spull}
     *       would instead forbid routing (subscription-resolved).</li>
     * </ul>
     */
    private void validateOrderedAtomic() {
        validateOrderedCommon("ordered_atomic");
        requireBodyRouting("ordered_atomic");
    }

    /**
     * Contract shared by every ordered_* variant: single step, single sequence per step, reserved
     * partition + ordinal present, no relay-only fields. The {@code re.partition} range
     * ([0, 9999]) and {@code re.ordinal} non-negativity are already enforced by
     * {@link #validateReservedMetadata}; here we enforce their <i>presence</i> (required for ordered).
     */
    private void validateOrderedCommon(String deliveryTypeName) {
        if (steps.size() != 1) {
            throw new ReInputException(ReSdkError.MULTI_STEP_NOT_SUPPORTED, deliveryTypeName, steps.size());
        }
        validateNoRelayFields(deliveryTypeName);
        for (Step step : steps) {
            List<Sequence> sequences = step.getSequences();
            if (sequences == null || sequences.size() != 1) {
                throw new ReInputException(ReSdkError.ORDERED_SINGLE_SEQUENCE_REQUIRED,
                        deliveryTypeName, sequences == null ? 0 : sequences.size());
            }
            for (Sequence seq : sequences) {
                JsonNode metadata = seq.getMetadata();
                JsonNode re = (metadata != null && metadata.isObject())
                        ? metadata.get(RESERVED_METADATA_KEY) : null;
                JsonNode partition = (re != null && re.isObject()) ? re.get("partition") : null;
                JsonNode ordinal = (re != null && re.isObject()) ? re.get("ordinal") : null;
                if (partition == null || partition.isNull()) {
                    throw new ReInputException(ReSdkError.ORDERED_PARTITION_REQUIRED, deliveryTypeName);
                }
                if (ordinal == null || ordinal.isNull()) {
                    throw new ReInputException(ReSdkError.ORDERED_ORDINAL_REQUIRED, deliveryTypeName);
                }
            }
        }
    }

    /** Atomic-base ordered carries its fixed destination(s) in body routing (single or broadcast). */
    private void requireBodyRouting(String deliveryTypeName) {
        for (Step step : steps) {
            for (Sequence seq : step.getSequences()) {
                List<RoutingDestination> routing = seq.getRouting();
                if (routing == null || routing.isEmpty()) {
                    throw new ReInputException(
                            ReSdkError.ORDERED_ROUTING_REQUIRED, deliveryTypeName, seq.getSeq());
                }
            }
        }
    }

    private void validateNoRelayFields(String deliveryTypeName) {
        for (Step step : steps) {
            if (step.getDecision() != null) {
                throw new ReInputException(ReSdkError.FIELD_NOT_SUPPORTED, deliveryTypeName, "decision");
            }
            if (step.getOnFailure() != null && !step.getOnFailure().isEmpty()) {
                throw new ReInputException(ReSdkError.FIELD_NOT_SUPPORTED, deliveryTypeName, "on_failure");
            }
            if (step.getOnFailurePayload() != null && !step.getOnFailurePayload().isEmpty()) {
                throw new ReInputException(
                        ReSdkError.FIELD_NOT_SUPPORTED, deliveryTypeName, "on_failure_payload");
            }
        }
    }
}
