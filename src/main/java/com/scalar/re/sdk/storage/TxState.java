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
package com.scalar.re.sdk.storage;

/**
 * ScalarDB Consensus Commit transaction metadata as RE reads and writes it: the {@code tx_state}
 * column and its values ({@code com.scalar.db.api.TransactionState}). The single definition used by
 * the RE core (recovery scans, gap diagnosis, schema index) and by the SDK writers that store rows
 * outside a transaction (Dynamo / Cosmos write {@link #COMMITTED}).
 */
public final class TxState {

    /** Column holding the transaction state of a record. */
    public static final String COLUMN = "tx_state";
    /** Column holding when the record's transaction prepared (epoch ms). */
    public static final String PREPARED_AT_COLUMN = "tx_prepared_at";

    /** A put prepared, commit outcome not yet known (in-doubt). */
    public static final int PREPARED = 1;
    /** A delete prepared, commit outcome not yet known (in-doubt). */
    public static final int DELETED = 2;
    /** Committed. */
    public static final int COMMITTED = 3;
    /** Aborted. */
    public static final int ABORTED = 4;

    private TxState() {}

    /** The in-doubt states ({@link #PREPARED}, {@link #DELETED}) a recovery scan looks for. */
    public static int[] inDoubt() {
        return new int[] {PREPARED, DELETED};
    }
}
