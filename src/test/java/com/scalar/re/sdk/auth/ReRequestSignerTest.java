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
package com.scalar.re.sdk.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link ReRequestSigner} — signature version 2 (auth-license §1.2): the string to sign, the HMAC,
 * the headers and the constant-time match (both hex cases, non-hex never matches).
 */
class ReRequestSignerTest {

    private static String independentHmacHex(String key, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    // ---- canonical query

    @ParameterizedTest
    @NullAndEmptySource
    void canonicalQuery_noQuery_empty(String q) {
        assertThat(ReRequestSigner.canonicalQuery(q)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "a=1|a=1",
            "b=2&a=1|a=1&b=2",
            "c=3&a=1&b=2|a=1&b=2&c=3",
            "a=2&a=1|a=1&a=2",
            "x=%2F&a=1|a=1&x=%2F"})
    void canonicalQuery_rawPairsSorted(String raw, String expected) {
        assertThat(ReRequestSigner.canonicalQuery(raw)).isEqualTo(expected);
    }

    // ---- string to sign

    @Test
    void stringToSign_layout() {
        assertThat(ReRequestSigner.stringToSign("POST", "/api/v1/re/notify", null, "1700000000000", "{\"a\":1}"))
                .isEqualTo("POST\n/api/v1/re/notify\n\n1700000000000\n{\"a\":1}");
    }

    @Test
    void stringToSign_methodUpperCased_queryCanonical_nullBodyEmpty() {
        assertThat(ReRequestSigner.stringToSign("get", "/api/v1/re/poll/partitions", "event_type=E&destination=d", "1", null))
                .isEqualTo("GET\n/api/v1/re/poll/partitions\ndestination=d&event_type=E\n1\n");
    }

    @Test
    void stringToSign_emptyBody_sameAsNull() {
        assertThat(ReRequestSigner.stringToSign("GET", "/p", null, "1", ""))
                .isEqualTo(ReRequestSigner.stringToSign("GET", "/p", null, "1", null));
    }

    @Test
    void stringToSign_eachPartChangesTheResult() {
        String base = ReRequestSigner.stringToSign("POST", "/p", "a=1", "1", "b");
        assertThat(ReRequestSigner.stringToSign("PUT", "/p", "a=1", "1", "b")).isNotEqualTo(base);
        assertThat(ReRequestSigner.stringToSign("POST", "/q", "a=1", "1", "b")).isNotEqualTo(base);
        assertThat(ReRequestSigner.stringToSign("POST", "/p", "a=2", "1", "b")).isNotEqualTo(base);
        assertThat(ReRequestSigner.stringToSign("POST", "/p", "a=1", "2", "b")).isNotEqualTo(base);
        assertThat(ReRequestSigner.stringToSign("POST", "/p", "a=1", "1", "c")).isNotEqualTo(base);
    }

    // ---- HMAC

    @Test
    void sign_isHmacSha256LowerHex() throws Exception {
        String payload = ReRequestSigner.stringToSign("POST", "/p", null, "1", "body");
        String signature = ReRequestSigner.sign("k", payload);
        assertThat(signature).isEqualTo(independentHmacHex("k", payload)).hasSize(64)
                .isEqualTo(signature.toLowerCase(Locale.ROOT));
    }

    @Test
    void sign_multiByteKeyAndBody_utf8() throws Exception {
        assertThat(ReRequestSigner.sign("鍵", "本文")).isEqualTo(independentHmacHex("鍵", "本文"));
    }

    @Test
    void headers_threeHeadersInOrder_signatureOverTheStringToSign() throws Exception {
        Map<String, String> h = ReRequestSigner.headers("k", "POST", "/api/v1/re/notify", null, "{}", 1234L);

        assertThat(h.keySet()).containsExactly(ReRequestSigner.TIMESTAMP_HEADER, ReRequestSigner.VERSION_HEADER,
                ReRequestSigner.SIGNATURE_HEADER);
        assertThat(h.get(ReRequestSigner.TIMESTAMP_HEADER)).isEqualTo("1234");
        assertThat(h.get(ReRequestSigner.VERSION_HEADER)).isEqualTo("2");
        assertThat(h.get(ReRequestSigner.SIGNATURE_HEADER))
                .isEqualTo(independentHmacHex("k", "POST\n/api/v1/re/notify\n\n1234\n{}"));
    }

    // ---- match (constant time, both cases)

    @Test
    void matches_lowerAndUpperHex() {
        byte[] mac = ReRequestSigner.hmac("k", "p");
        String hex = HexFormat.of().formatHex(mac);
        assertThat(ReRequestSigner.matches(mac, hex)).isTrue();
        assertThat(ReRequestSigner.matches(mac, hex.toUpperCase(Locale.ROOT))).isTrue();
    }

    @Test
    void matches_oneCharDifferent_false() {
        byte[] mac = ReRequestSigner.hmac("k", "p");
        String hex = HexFormat.of().formatHex(mac);
        char last = hex.charAt(hex.length() - 1);
        String changed = hex.substring(0, hex.length() - 1) + (last == '0' ? '1' : '0');
        assertThat(ReRequestSigner.matches(mac, changed)).isFalse();
    }

    @Test
    void matches_shorterOrLonger_false() {
        byte[] mac = ReRequestSigner.hmac("k", "p");
        String hex = HexFormat.of().formatHex(mac);
        assertThat(ReRequestSigner.matches(mac, hex.substring(0, hex.length() - 2))).isFalse();
        assertThat(ReRequestSigner.matches(mac, hex + "00")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "zz", "abc", "0x00", " "})
    void matches_notHexOrOddLength_false(String provided) {
        assertThat(ReRequestSigner.matches(ReRequestSigner.hmac("k", "p"), provided)).isFalse();
    }

    @Test
    void matches_nulls_false() {
        assertThat(ReRequestSigner.matches(ReRequestSigner.hmac("k", "p"), null)).isFalse();
        assertThat(ReRequestSigner.matches(null, "00")).isFalse();
    }

    @Test
    void matches_anotherKey_false() {
        String hex = ReRequestSigner.sign("k1", "p");
        assertThat(ReRequestSigner.matches(ReRequestSigner.hmac("k2", "p"), hex)).isFalse();
    }
}
