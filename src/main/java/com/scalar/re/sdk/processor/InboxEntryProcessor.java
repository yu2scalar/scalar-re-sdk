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
package com.scalar.re.sdk.processor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scalar.re.sdk.model.ReEventBody;
import com.scalar.re.sdk.model.RoutingDestination;
import com.scalar.re.sdk.model.Sequence;
import com.scalar.re.sdk.model.Step;

import java.util.ArrayList;
import java.util.List;

/**
 * Processes a ReEventBody and produces InboxEntry records.
 *
 * Processing flow:
 *   1. Parse body JSON
 *   2. Process each step
 *   3. Produce 1 inbox record per sequence per routing destination
 *      (step_id=actual, seq=actual)
 */
public class InboxEntryProcessor {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Process a ReEventBody and produce inbox entries.
     *
     * @param eventBody parsed event body
     * @return list of InboxEntry records to write to re_inbox
     */
    public static List<InboxEntry> process(ReEventBody eventBody) throws JsonProcessingException {
        List<InboxEntry> entries = new ArrayList<>();
        if (eventBody.getSteps() == null) {
            return entries;
        }

        // A null steps / sequences / routing list yields no entries (instead of an NPE). The RE core
        // rejects such a body before the transfer (data-model §4.3.1), so nothing is lost here.
        for (Step step : eventBody.getSteps()) {
            if (step.getSequences() == null) continue;
            for (Sequence sequence : step.getSequences()) {
                if (sequence.getRouting() == null) continue;
                for (RoutingDestination rd : sequence.getRouting()) {
                    String body = buildInboxBody(eventBody, step, List.of(sequence));
                    entries.add(new InboxEntry(rd.getDestination(), step.getStepId(), sequence.getSeq(),
                            rd.isAckRequired(), body));
                }
            }
        }

        return entries;
    }

    /**
     * Build the re-constructed body JSON for an inbox record.
     * Contains a single step with the specified sequences.
     */
    private static String buildInboxBody(ReEventBody eventBody, Step step,
                                          List<Sequence> sequences) throws JsonProcessingException {
        Step inboxStep = new Step(
                step.getStepId(),
                sequences
        );

        ReEventBody inboxBody = new ReEventBody(List.of(inboxStep));

        return objectMapper.writeValueAsString(inboxBody);
    }
}
