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
package com.scalar.re.sdk.error;

/**
 * The single source of truth for all {@code RE-SDK-} error / message codes
 * (scalar-re-sdk component).
 *
 * <p>One component = one enum file (ADR D9). Entries are grouped by {@link Category} in banner
 * sections; the {@code NNN} sequence resets per category and is append-only (never reused).
 * Modules reference these constants and never inline message strings (principle P5).
 *
 * <p>Uniqueness of {@code buildCode()} across all values is enforced by
 * {@code ReSdkErrorTest}. See {@code docs/design/common/error-handling.md} §2.10.
 */
public enum ReSdkError implements ScalarReError {

    // ---------------------------------------------------------------------------------------------
    // INPUT (3) — body builder contract violations
    // ---------------------------------------------------------------------------------------------

    DELIVERY_TYPE_REQUIRED(
            Category.INPUT,
            "001",
            Severity.ERROR,
            "Delivery type must be set",
            "deliveryType(...) was explicitly set to null on the body builder",
            "Pass a non-null delivery type, or omit deliveryType(...) to use the default"),

    UNKNOWN_DELIVERY_TYPE(
            Category.INPUT,
            "002",
            Severity.ERROR,
            "Unknown delivery type: %s",
            "The delivery type passed to the body builder is not a recognized value",
            "Use one of: relay, atomic, partial, pull, qpull, spull, ordered_atomic"),

    MULTI_STEP_NOT_SUPPORTED(
            Category.INPUT,
            "003",
            Severity.ERROR,
            "Delivery type '%s' does not support multiple steps (got %s)",
            "A single-step delivery type was built with more than one step",
            "Use a single step for this delivery type, or switch to relay/atomic for multi-step"),

    FIELD_NOT_SUPPORTED(
            Category.INPUT,
            "004",
            Severity.ERROR,
            "Delivery type '%s' does not support '%s'",
            "A relay-only step field (decision / on_failure / on_failure_payload) was set on a "
                    + "delivery type that does not accept it",
            "Remove the unsupported field, or switch to a relay delivery type"),

    SPULL_ROUTING_NOT_ACCEPTED(
            Category.INPUT,
            "005",
            Severity.ERROR,
            "Delivery type 'spull' does not accept body routing (seq=%s has %s destination(s))",
            "SPull resolves destinations from the subscription cache, so the body must not carry routing",
            "Omit routing(...) calls on the sequence builder for spull bodies"),

    RESERVED_FIELD_SET(
            Category.INPUT,
            "006",
            Severity.ERROR,
            "Field '%s' is managed by ScalarRE and must not be set by the producer",
            "A ScalarRE-managed step field (e.g. ack_status) was set on the producer body; the core "
                    + "populates it and ignores any producer-supplied value",
            "Remove the field from the body builder"),

    PULL_ROUTING_NOT_ACCEPTED(
            Category.INPUT,
            "007",
            Severity.ERROR,
            "Delivery type 'pull' does not accept body routing (seq=%s has %s destination(s))",
            "Pull delivers to the single fixed destination configured on the EventType "
                    + "(config 'destination'), so the body must not carry routing",
            "Omit routing(...) calls on the sequence builder for pull bodies; the destination "
                    + "is taken from the EventType config"),

    RE_PARTITION_OUT_OF_RANGE(
            Category.INPUT,
            "008",
            Severity.ERROR,
            "metadata.re.partition must be an integer in [0, %s], got %s",
            "The reserved key metadata.re.partition (producer-specified partition number) was set "
                    + "to a non-integer or a value outside the normal range [0, 9999]; values "
                    + ">= 10000 are the Replay-reserved range and must not be produced",
            "Set metadata.re.partition to an integer in [0, 9999], or omit it to let ScalarRE "
                    + "load-balance by hash(event_id) % partitionCount"),

    RE_ORDINAL_INVALID(
            Category.INPUT,
            "009",
            Severity.ERROR,
            "metadata.re.ordinal must be a non-negative integer, got %s",
            "The reserved key metadata.re.ordinal (ordered per-partition gapless sequence) was set "
                    + "to a non-integer or negative value",
            "Set metadata.re.ordinal to a non-negative long (producer-assigned, gapless per "
                    + "partition), or omit it for non-ordered delivery types"),

    ORDERED_PARTITION_REQUIRED(
            Category.INPUT,
            "010",
            Severity.ERROR,
            "Delivery type '%s' requires metadata.re.partition on every sequence",
            "An ordered delivery type was built without metadata.re.partition; ordering is defined "
                    + "per partition number, so the producer must specify it (data-model §4.5)",
            "Set metadata.re.partition (via SequenceBuilder.rePartition(...)) in [0, 9999] on every "
                    + "sequence of an ordered body"),

    ORDERED_ORDINAL_REQUIRED(
            Category.INPUT,
            "011",
            Severity.ERROR,
            "Delivery type '%s' requires metadata.re.ordinal on every sequence",
            "An ordered delivery type was built without metadata.re.ordinal; the consumer detects "
                    + "gaps by the per-partition gapless ordinal value, so the producer must assign it",
            "Set metadata.re.ordinal (via SequenceBuilder.reOrdinal(...)) to a non-negative, "
                    + "per-partition gapless long on every sequence of an ordered body"),

    ORDERED_ROUTING_REQUIRED(
            Category.INPUT,
            "012",
            Severity.ERROR,
            "Delivery type '%s' requires body routing (seq=%s has none)",
            "An atomic-base ordered delivery type was built without body routing; ordered carries "
                    + "its fixed destination(s) in the body routing[] (single or broadcast)",
            "Add routing(...) (single or multiple destinations) on every sequence of an "
                    + "ordered_atomic body"),

    ORDERED_SINGLE_SEQUENCE_REQUIRED(
            Category.INPUT,
            "013",
            Severity.ERROR,
            "Delivery type '%s' requires exactly one sequence per step (got %s)",
            "An ordered delivery type was built with a step holding zero or multiple sequences; "
                    + "ordered simplifies the ordering unit to one sequence per step",
            "Use exactly one sequence per step for an ordered body"),

    STEPS_REQUIRED(
            Category.INPUT,
            "014",
            Severity.ERROR,
            "The body must have at least one step",
            "The body builder was given no step; the RE core treats a body without steps as a "
                    + "poison message and moves it to the DLQ",
            "Add at least one step (addStep(...)) before building the body"),

    ROUTING_DESTINATION_BLANK(
            Category.INPUT,
            "015",
            Severity.ERROR,
            "Routing destination must not be null or blank (step=%s, seq=%s)",
            "A routing entry has a null, empty or blank destination; it names no namespace to "
                    + "deliver to",
            "Set a destination namespace on every routing entry"),

    INVALID_DECISION(
            Category.INPUT,
            "016",
            Severity.ERROR,
            "Unknown relay decision '%s' (step=%s)",
            "A relay step decision is not one of the allowed values (case-sensitive); an unknown "
                    + "value would otherwise be silently treated as ALL_SUCCESS",
            "Use ALL_SUCCESS, ANY_SUCCESS (RelayProtocol constants), or leave the decision unset "
                    + "(= ALL_SUCCESS)"),
    ;

    private final Category category;
    private final String id;
    private final Severity severity;
    private final String message;
    private final String cause;
    private final String solution;

    ReSdkError(
            Category category,
            String id,
            Severity severity,
            String message,
            String cause,
            String solution) {
        validate(getComponentName(), category, id, severity, message, cause, solution);
        this.category = category;
        this.id = id;
        this.severity = severity;
        this.message = message;
        this.cause = cause;
        this.solution = solution;
    }

    @Override
    public String getComponentName() {
        return "RE-SDK";
    }

    @Override
    public Category getCategory() {
        return category;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Severity getSeverity() {
        return severity;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public String getCause() {
        return cause;
    }

    @Override
    public String getSolution() {
        return solution;
    }
}
