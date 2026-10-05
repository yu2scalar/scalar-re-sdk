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
 * Reserved metadata namespace {@code metadata.re.*} (data-model §4.5): the single place that
 * validates and reads {@code re.partition} / {@code re.ordinal}. Shared by the SDK builder and the
 * RE core so both apply the same rules.
 */
package com.scalar.re.sdk.metadata;
