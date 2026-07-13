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
package com.scalar.re.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ReEventBody {

    private List<Step> steps;

    public ReEventBody() {
    }

    public ReEventBody(List<Step> steps) {
        this.steps = steps;
    }

    public List<Step> getSteps() {
        return steps;
    }

    public void setSteps(List<Step> steps) {
        this.steps = steps;
    }

    @JsonIgnore
    public Set<String> getFirstStepRoutingNamespaces() {
        if (steps == null || steps.isEmpty()) return Set.of();
        Step firstStep = steps.get(0);
        if (firstStep.getSequences() == null) return Set.of();
        return firstStep.getSequences().stream()
                .filter(seq -> seq.getRouting() != null)
                .flatMap(seq -> seq.getRouting().stream())
                .map(RoutingDestination::getDestination)
                .collect(Collectors.toSet());
    }
}
