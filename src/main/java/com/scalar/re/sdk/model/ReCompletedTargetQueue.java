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

import java.util.List;
import java.util.Objects;

/**
 * Queue object shape: {@code {"queue":["destA","destB"]}}.
 *
 * <p>Used by QPull TX1 (outbox → re_queue) and SPull TX1 to indicate that the
 * destinations / subscribers have been queued but not yet delivered to the consumer's
 * inbox. The actual delivery is recorded as a separate row with array shape when
 * QPull/SPull TX2 (queue → inbox) commits per-destination.
 */
public record ReCompletedTargetQueue(List<String> destinations) implements ReCompletedTarget {

    public ReCompletedTargetQueue {
        Objects.requireNonNull(destinations, "destinations");
        destinations = List.copyOf(destinations);
    }
}
