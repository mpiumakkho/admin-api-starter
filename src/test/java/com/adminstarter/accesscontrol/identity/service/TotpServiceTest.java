package com.adminstarter.accesscontrol.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TotpServiceTest {

    /** The RFC 6238 test secret, "12345678901234567890" */
    private static final String SECRET = TotpService.toBase32("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

    @Test
    void codes_match_the_rfc_6238_test_vectors() {
        var totp = at(Instant.EPOCH);
        // Appendix B, SHA-1, last six of the eight digits
        assertThat(totp.codeAt(SECRET, Instant.ofEpochSecond(59))).isEqualTo("287082");
        assertThat(totp.codeAt(SECRET, Instant.ofEpochSecond(1111111109))).isEqualTo("081804");
        assertThat(totp.codeAt(SECRET, Instant.ofEpochSecond(1234567890))).isEqualTo("005924");
        assertThat(totp.codeAt(SECRET, Instant.ofEpochSecond(2000000000))).isEqualTo("279037");
    }

    @Test
    void a_code_works_one_step_either_side_for_clock_drift() {
        var now = Instant.ofEpochSecond(1234567890);
        var totp = at(now);
        assertThat(totp.verify(SECRET, totp.codeAt(SECRET, now))).isTrue();
        assertThat(totp.verify(SECRET, totp.codeAt(SECRET, now.minusSeconds(30)))).isTrue();
        assertThat(totp.verify(SECRET, totp.codeAt(SECRET, now.plusSeconds(30)))).isTrue();
        assertThat(totp.verify(SECRET, totp.codeAt(SECRET, now.minusSeconds(90)))).isFalse();
    }

    @Test
    void spaces_are_ignored_and_anything_but_six_digits_is_refused() {
        var now = Instant.ofEpochSecond(1234567890);
        var totp = at(now);
        var code = totp.codeAt(SECRET, now);
        assertThat(totp.verify(SECRET, code.substring(0, 3) + " " + code.substring(3))).isTrue();
        assertThat(totp.verify(SECRET, "12345")).isFalse();
        assertThat(totp.verify(SECRET, "abcdef")).isFalse();
    }

    @Test
    void secrets_are_160_bit_base32_and_survive_a_round_trip() {
        var secret = at(Instant.EPOCH).createSecret();
        assertThat(secret).hasSize(32).matches("[A-Z2-7]+");
        assertThat(TotpService.toBase32(TotpService.fromBase32(secret))).isEqualTo(secret);
    }

    @Test
    void the_uri_is_what_authenticator_apps_read() {
        assertThat(at(Instant.EPOCH).uri("JBSWY3DPEHPK3PXP", "ann@example.com", "Admin")).isEqualTo(
                "otpauth://totp/Admin%3Aann%40example.com?secret=JBSWY3DPEHPK3PXP&issuer=Admin&algorithm=SHA1&digits=6&period=30");
    }

    private static TotpService at(Instant now) {
        return new TotpService(Clock.fixed(now, ZoneOffset.UTC));
    }
}
