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
/**
 * Event id generators: plain UUIDv7 for unordered delivery types and the ordered variant
 * that packs the producer ordinal into the id as a same-millisecond tiebreaker. For
 * {@code ordered_*} delivery types the ordered variant (or an equivalent scheme) is
 * required by contract: event_id lexicographic order must equal ordinal order within a
 * partition lane (delivery-ordered.md ADR D6).
 */
package com.scalar.re.sdk.generator;
