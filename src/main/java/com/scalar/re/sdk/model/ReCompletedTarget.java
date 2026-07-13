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

/**
 * The {@code target} column of {@code re_completed} (v2) is a JSON value with one of three shapes,
 * each carrying a list of destination/subscriber names.
 *
 * <p>See {@code docs/plan-cluster-l-completed-per-tx.md} for the full shape semantics:
 * <ul>
 *   <li>{@link ReCompletedTargetArray} — {@code ["destA","destB"]}: delivered to listed destinations
 *       in this TX (used by pull / atomic / partial / relay step / QPull-TX2 / SPull-TX2)</li>
 *   <li>{@link ReCompletedTargetQueue} — {@code {"queue":[...]}}: queued for listed destinations,
 *       not yet delivered (used by QPull/SPull TX1 = outbox→queue)</li>
 * </ul>
 *
 * <p><b>Cluster L</b>: the former {@code step object} shape ({@code {"step":k,"to":[...]}}) was
 * removed — relay's step is now carried by the dedicated {@code step_id} CK column on
 * {@code re_completed}, so a relay step writes a plain {@code array} target plus its {@code step_id}.
 */
public sealed interface ReCompletedTarget
        permits ReCompletedTargetArray, ReCompletedTargetQueue {

    /** The destinations / subscribers carried by this target row. Never null. */
    List<String> destinations();
}
