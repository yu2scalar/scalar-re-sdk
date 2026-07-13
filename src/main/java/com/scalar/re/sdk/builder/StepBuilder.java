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

import com.scalar.re.sdk.model.Sequence;
import com.scalar.re.sdk.model.Step;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class StepBuilder {

    private int stepId;
    private String decision;
    private List<String> onFailure;
    private Map<String, Object> onFailurePayload;
    private final List<Sequence> sequences = new ArrayList<>();

    public StepBuilder stepId(int stepId) {
        this.stepId = stepId;
        return this;
    }

    public StepBuilder decision(String decision) {
        this.decision = decision;
        return this;
    }

    public StepBuilder onFailure(List<String> onFailure) {
        this.onFailure = onFailure;
        return this;
    }

    public StepBuilder onFailurePayload(Map<String, Object> onFailurePayload) {
        this.onFailurePayload = onFailurePayload;
        return this;
    }

    public StepBuilder addSequence(Consumer<SequenceBuilder> configurator) {
        SequenceBuilder builder = new SequenceBuilder();
        configurator.accept(builder);
        sequences.add(builder.build());
        return this;
    }

    public StepBuilder addSequence(Sequence sequence) {
        sequences.add(sequence);
        return this;
    }

    public Step build() {
        return new Step(stepId, decision,
                onFailure, onFailurePayload, List.copyOf(sequences));
    }
}
