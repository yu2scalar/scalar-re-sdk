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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scalar.re.sdk.model.InboxBody;
import com.scalar.re.sdk.model.ReEventBody;
import com.scalar.re.sdk.model.Sequence;
import com.scalar.re.sdk.parser.ReEventBodyParser;

/**
 * Build the simplified inbox body form ({payload, metadata}) used by re_inbox.body.
 *
 * <p>Columns step_id / seq / namespace / ack_required are all duplicated
 * by re_inbox columns and are therefore omitted from the body itself.
 */
public class InboxBodyBuilder {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static String fromSequence(Sequence sequence) throws JsonProcessingException {
        InboxBody body = new InboxBody(sequence.getPayload(), sequence.getMetadata());
        return objectMapper.writeValueAsString(body);
    }

    /**
     * Convert an InboxEntryProcessor sub-body (single-step + single-seq full ReEventBody form)
     * into the simplified inbox.body form. Used at re_inbox write sites so that
     * InboxEntryProcessor itself can stay unchanged (queue still consumes the full sub-body).
     */
    public static String fromSubBody(String fullSubBodyJson) throws JsonProcessingException {
        ReEventBody parsed = ReEventBodyParser.parse(fullSubBodyJson);
        if (parsed == null || parsed.getSteps() == null || parsed.getSteps().isEmpty()) {
            throw new IllegalArgumentException("sub-body has no steps: " + fullSubBodyJson);
        }
        var sequences = parsed.getSteps().get(0).getSequences();
        if (sequences == null || sequences.isEmpty()) {
            throw new IllegalArgumentException("sub-body step has no sequences: " + fullSubBodyJson);
        }
        return fromSequence(sequences.get(0));
    }
}
