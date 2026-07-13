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
package com.scalar.re.sdk.error;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * §2.2.1 UT for the previously-uncovered ScalarRE exception subclasses
 * ({@link ReAuthException}, {@link ReConfigException}, {@link ReUnavailableException},
 * {@link ReIndeterminateException}, {@link ReInternalException}) and the shared
 * {@link ReException} base behaviour (code/category/severity/args accessors, cause chaining,
 * and the {@code requireCategory} guard — both the matching and mismatching branch).
 */
class ReExceptionHierarchyTest {

    /** Minimal ScalarReError test double with a configurable category. */
    private static ScalarReError err(Category category) {
        return new ScalarReError() {
            @Override public String getComponentName() { return "RE-SDK"; }
            @Override public Category getCategory() { return category; }
            @Override public String getId() { return "999"; }
            @Override public Severity getSeverity() { return Severity.ERROR; }
            @Override public String getMessage() { return "boom %s"; }
            @Override public String getCause() { return "test cause"; }
            @Override public String getSolution() { return "test solution"; }
        };
    }

    @Test
    void reAuthException_matchingCategory_exposesCodeCategorySeverityArgs() {
        Throwable cause = new IllegalStateException("db down");
        ReAuthException e = new ReAuthException(err(Category.AUTH), cause, "detail");

        assertThat(e.getCategory()).isEqualTo(Category.AUTH);
        assertThat(e.getCode()).isEqualTo("RE-SDK-2999"); // component-<catId=2><id=999>
        assertThat(e.getSeverity()).isEqualTo(Severity.ERROR);
        assertThat(e.getMessage()).contains("RE-SDK-2999").contains("boom detail");
        assertThat(e.getArgs()).containsExactly("detail");
        assertThat(e.getCause()).isSameAs(cause);
        assertThat(e.getError()).isNotNull();
    }

    @Test
    void reAuthException_noCauseCtor_hasNullCauseAndCopiesArgsDefensively() {
        ReAuthException e = new ReAuthException(err(Category.AUTH), "x");
        assertThat(e.getCause()).isNull();
        // getArgs returns a clone — mutating the returned array must not affect the exception.
        Object[] a = e.getArgs();
        a[0] = "mutated";
        assertThat(e.getArgs()).containsExactly("x");
    }

    @Test
    void eachSubclass_acceptsItsOwnCategory() {
        assertThat(new ReConfigException(err(Category.CONFIG)).getCategory()).isEqualTo(Category.CONFIG);
        assertThat(new ReUnavailableException(err(Category.UNAVAILABLE)).getCategory()).isEqualTo(Category.UNAVAILABLE);
        assertThat(new ReIndeterminateException(err(Category.INDETERMINATE)).getCategory()).isEqualTo(Category.INDETERMINATE);
        assertThat(new ReInternalException(err(Category.INTERNAL)).getCategory()).isEqualTo(Category.INTERNAL);
    }

    @Test
    void mismatchedCategory_isRejectedByRequireCategory() {
        // Each subclass asserts its category; feeding the wrong one must fail fast.
        assertThatThrownBy(() -> new ReAuthException(err(Category.CONFIG)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AUTH");
        assertThatThrownBy(() -> new ReConfigException(err(Category.AUTH)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CONFIG");
        assertThatThrownBy(() -> new ReUnavailableException(err(Category.INTERNAL)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReIndeterminateException(err(Category.CONFIG)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReInternalException(err(Category.AUTH)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
