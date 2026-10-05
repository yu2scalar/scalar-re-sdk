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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Signs requests to ScalarRE's HMAC-authenticated APIs (notify / notify batch / pull / poll /
 * poll partitions) — signature version 2 (auth-license §1.2):
 *
 * <pre>
 * HMAC-SHA256(namespace key, METHOD + "\n" + path + "\n" + canonicalQuery + "\n" + timestamp + "\n" + body)
 * </pre>
 *
 * sent as {@value #SIGNATURE_HEADER} (hex), {@value #TIMESTAMP_HEADER} (epoch millis) and
 * {@value #VERSION_HEADER}: {@value #VERSION}. The timestamp, method and path are signed, so a
 * captured request cannot be re-sent with a new timestamp or to another API. The server builds the
 * string to sign with this same class.
 */
public final class ReRequestSigner {

    public static final String SIGNATURE_HEADER = "X-ScalarRE-Signature";
    public static final String TIMESTAMP_HEADER = "X-ScalarRE-Timestamp";
    public static final String VERSION_HEADER = "X-ScalarRE-Signature-Version";
    public static final String VERSION = "2";

    private static final String ALGORITHM = "HmacSHA256";

    private ReRequestSigner() {}

    /**
     * The raw {@code k=v} pairs of a query string, sorted and joined with {@code &}; empty for no
     * query. Pairs are compared as they are sent (no decoding).
     */
    public static String canonicalQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return "";
        }
        String[] pairs = rawQuery.split("&");
        Arrays.sort(pairs);
        return String.join("&", pairs);
    }

    /**
     * The string to sign.
     *
     * @param method   HTTP method (upper-cased here)
     * @param path     the request path, e.g. {@code /api/v1/re/notify}
     * @param rawQuery the raw query string without {@code ?}, or null
     * @param timestamp the {@value #TIMESTAMP_HEADER} value
     * @param body     the exact body sent (UTF-8); null or empty for none
     */
    public static String stringToSign(String method, String path, String rawQuery, String timestamp, String body) {
        return method.toUpperCase(Locale.ROOT) + "\n" + path + "\n" + canonicalQuery(rawQuery) + "\n"
                + timestamp + "\n" + (body == null ? "" : body);
    }

    /** HMAC-SHA256 of {@code payload} with {@code key} (UTF-8), raw bytes. */
    public static byte[] hmac(String key, String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute the HMAC signature", e);
        }
    }

    /** HMAC-SHA256 of {@code payload} as lower-case hex. */
    public static String sign(String key, String payload) {
        return HexFormat.of().formatHex(hmac(key, payload));
    }

    /**
     * The three headers of a version 2 signature, in send order.
     *
     * @param timestampMillis the time to sign (normally {@code System.currentTimeMillis()})
     */
    public static Map<String, String> headers(String key, String method, String path, String rawQuery,
                                              String body, long timestampMillis) {
        String timestamp = String.valueOf(timestampMillis);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(TIMESTAMP_HEADER, timestamp);
        headers.put(VERSION_HEADER, VERSION);
        headers.put(SIGNATURE_HEADER, sign(key, stringToSign(method, path, rawQuery, timestamp, body)));
        return headers;
    }

    /**
     * Whether a received hex signature equals the expected HMAC, in constant time. Upper- and
     * lower-case hex are accepted; a value that is not hex (or null) does not match.
     */
    public static boolean matches(byte[] expected, String providedHex) {
        if (expected == null || providedHex == null) {
            return false;
        }
        byte[] provided;
        try {
            provided = HexFormat.of().parseHex(providedHex);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return MessageDigest.isEqual(expected, provided);
    }
}
