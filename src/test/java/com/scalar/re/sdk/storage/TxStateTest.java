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

import static org.assertj.core.api.Assertions.assertThat;

import com.scalar.db.api.TransactionState;
import org.junit.jupiter.api.Test;

/** B-15: the one tx_state definition must match ScalarDB's {@link TransactionState} ids. */
class TxStateTest {

    @Test
    void valuesMatchScalarDbTransactionState() {
        assertThat(TxState.PREPARED).isEqualTo(TransactionState.PREPARED.get());
        assertThat(TxState.DELETED).isEqualTo(TransactionState.DELETED.get());
        assertThat(TxState.COMMITTED).isEqualTo(TransactionState.COMMITTED.get());
        assertThat(TxState.ABORTED).isEqualTo(TransactionState.ABORTED.get());
    }

    @Test
    void inDoubt_isPreparedAndDeletedOnly() {
        assertThat(TxState.inDoubt()).containsExactly(TxState.PREPARED, TxState.DELETED);
        assertThat(TxState.inDoubt()).doesNotContain(TxState.COMMITTED, TxState.ABORTED);
    }

    @Test
    void inDoubt_returnsAFreshArray() {
        int[] first = TxState.inDoubt();
        first[0] = -1;
        assertThat(TxState.inDoubt()[0]).isEqualTo(TxState.PREPARED);
    }

    @Test
    void columnNames() {
        assertThat(TxState.COLUMN).isEqualTo("tx_state");
        assertThat(TxState.PREPARED_AT_COLUMN).isEqualTo("tx_prepared_at");
    }
}
