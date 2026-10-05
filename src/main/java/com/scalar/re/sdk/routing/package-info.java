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
 * Body routing (data-model §4.3.1 / §4.3.2): reading the destinations a transfer writes and the
 * body rules on steps, destinations and relay decisions. Shared by the SDK builder and the RE core
 * so both apply the same rules.
 */
package com.scalar.re.sdk.routing;
