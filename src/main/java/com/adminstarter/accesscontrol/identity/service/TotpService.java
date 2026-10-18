package com.adminstarter.accesscontrol.identity.service;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * Time-based one-time codes (RFC 6238), as authenticator apps such as Google Authenticator make them: a shared
 * secret, HMAC-SHA-1, a 30-second step and 6 digits. Same algorithm as totp.ts in admin-ng-starter.
 */
@Service
public class TotpService {

    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int STEP_SECONDS = 30;
    private static final int DIGITS = 6;
    private static final int SECRET_BYTES = 20;

    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public TotpService(Clock clock) {
        this.clock = clock;
    }

    /** A new random secret, in the base32 form people type into an authenticator app */
    public String createSecret() {
        var bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return toBase32(bytes);
    }

    /** The address an authenticator app reads from the QR code */
    public String uri(String secret, String account, String issuer) {
        var label = encode(issuer + ":" + account);
        return "otpauth://totp/%s?secret=%s&issuer=%s&algorithm=SHA1&digits=%d&period=%d"
                .formatted(label, secret, encode(issuer), DIGITS, STEP_SECONDS);
    }

    /** Whether a code matches now, or one step either side, since phone clocks drift */
    public boolean verify(String secret, String code) {
        return matchedStep(secret, code).isPresent();
    }

    /**
     * The 30-second step a code matches, now or one step either side, since phone clocks drift; to accept each step
     * once (P-036)
     */
    public OptionalLong matchedStep(String secret, String code) {
        var typed = code.replaceAll("\\s", "");
        if (!typed.matches("\\d{" + DIGITS + "}")) {
            return OptionalLong.empty();
        }
        var counter = clock.instant().getEpochSecond() / STEP_SECONDS;
        var key = fromBase32(secret);
        var matched = OptionalLong.empty();
        for (var step = -1; step <= 1; step++) {
            // Compare every step in constant time, so timing says nothing about which one matched
            if (MessageDigest.isEqual(code(key, counter + step).getBytes(StandardCharsets.US_ASCII),
                    typed.getBytes(StandardCharsets.US_ASCII))) {
                matched = OptionalLong.of(counter + step);
            }
        }
        return matched;
    }

    /** The code for a moment */
    public String codeAt(String secret, Instant time) {
        return code(fromBase32(secret), time.getEpochSecond() / STEP_SECONDS);
    }

    private static String code(byte[] key, long counter) {
        try {
            var mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            var hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(counter).array());
            // Dynamic truncation: 31 bits from an offset the last byte picks
            var offset = hash[hash.length - 1] & 0x0f;
            var value = ByteBuffer.wrap(hash, offset, Integer.BYTES).getInt() & 0x7fffffff;
            return String.format("%0" + DIGITS + "d", value % (int) Math.pow(10, DIGITS));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 is not available", e);
        }
    }

    static String toBase32(byte[] bytes) {
        var out = new StringBuilder();
        int bits = 0;
        int value = 0;
        for (var b : bytes) {
            value = (value << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32.charAt((value >>> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(BASE32.charAt((value << (5 - bits)) & 31));
        }
        return out.toString();
    }

    static byte[] fromBase32(String text) {
        var out = new ByteArrayOutputStream();
        int bits = 0;
        int value = 0;
        for (var c : text.toUpperCase().replaceAll("[\\s=]", "").toCharArray()) {
            var index = BASE32.indexOf(c);
            if (index < 0) {
                throw new IllegalArgumentException("Not base32");
            }
            value = (value << 5) | index;
            bits += 5;
            if (bits >= 8) {
                out.write((value >>> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
