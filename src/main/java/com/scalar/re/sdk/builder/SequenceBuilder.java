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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scalar.re.sdk.model.RoutingDestination;
import com.scalar.re.sdk.model.Sequence;

import java.util.ArrayList;
import java.util.List;

public class SequenceBuilder {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private int seq;
    private final List<RoutingDestination> routing = new ArrayList<>();
    private JsonNode payload;
    private JsonNode metadata;

    public SequenceBuilder seq(int seq) {
        this.seq = seq;
        return this;
    }

    public SequenceBuilder routing(RoutingDestination... destinations) {
        for (RoutingDestination dest : destinations) {
            this.routing.add(dest);
        }
        return this;
    }

    public SequenceBuilder routing(String... destinations) {
        for (String dest : destinations) {
            this.routing.add(new RoutingDestination(dest));
        }
        return this;
    }

    public SequenceBuilder payload(JsonNode payload) {
        this.payload = payload;
        return this;
    }

    public SequenceBuilder payload(Object payload) {
        this.payload = objectMapper.valueToTree(payload);
        return this;
    }

    public SequenceBuilder metadata(JsonNode metadata) {
        this.metadata = metadata;
        return this;
    }

    public SequenceBuilder metadata(Object metadata) {
        this.metadata = objectMapper.valueToTree(metadata);
        return this;
    }

    /**
     * Set the producer-specified partition number under the reserved {@code metadata.re} namespace
     * (data-model §4.5). Writes {@code metadata.re.partition} (nested object). Must be in
     * {@code [0, 9999]} (validated at {@code build()}); {@code >= 10000} is Replay-reserved.
     * Delivery-type-agnostic: honored by all types as an explicit partition placement (else
     * ScalarRE load-balances by {@code hash(event_id) % partitionCount}).
     */
    public SequenceBuilder rePartition(long partition) {
        reNamespace().put("partition", partition);
        return this;
    }

    /**
     * Set the ordered per-partition gapless sequence number under {@code metadata.re.ordinal}
     * (data-model §4.5). Producer-assigned; used by ordered consumers for continuity / gap
     * detection. Ignored by non-ordered delivery types. Must be non-negative (validated at
     * {@code build()}).
     */
    public SequenceBuilder reOrdinal(long ordinal) {
        reNamespace().put("ordinal", ordinal);
        return this;
    }

    /** Get-or-create the {@code metadata.re} reserved-namespace object, preserving other metadata. */
    private ObjectNode reNamespace() {
        ObjectNode root;
        if (metadata != null && metadata.isObject()) {
            root = (ObjectNode) metadata;
        } else {
            root = objectMapper.createObjectNode();
            this.metadata = root;
        }
        JsonNode re = root.get("re");
        if (re != null && re.isObject()) {
            return (ObjectNode) re;
        }
        ObjectNode reNode = objectMapper.createObjectNode();
        root.set("re", reNode);
        return reNode;
    }

    public Sequence build() {
        return new Sequence(seq, List.copyOf(routing), payload, metadata);
    }
}
