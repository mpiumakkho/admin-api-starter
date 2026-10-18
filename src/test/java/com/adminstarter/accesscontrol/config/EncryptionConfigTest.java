package com.adminstarter.accesscontrol.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adminstarter.accesscontrol.AccessControlProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.mock.env.MockEnvironment;

class EncryptionConfigTest {

    private static final String SALT = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";

    @Test
    void does_not_start_without_a_key() {
        assertThatThrownBy(() -> new EncryptionConfig().secretEncryptor(properties(Map.of()), new MockEnvironment()))
                .hasMessageContaining("access-control.encryption.password");
        assertThatThrownBy(() -> new EncryptionConfig().secretEncryptor(properties(Map.of(
                "access-control.encryption.password", "secret", "access-control.encryption.salt", "not hex")), new MockEnvironment()))
                .hasMessageContaining("hex salt");
    }

    @Test
    void encrypts_with_the_configured_key() {
        var encryptor = new EncryptionConfig().secretEncryptor(properties(Map.of(
                "access-control.encryption.password", "secret", "access-control.encryption.salt", "9f2d6c1a84e03b57")), new MockEnvironment());
        assertThat(encryptor.decrypt(encryptor.encrypt("JBSWY3DPEHPK3PXP"))).isEqualTo("JBSWY3DPEHPK3PXP");
    }

    /** The development key in application.yml is public: refused in the prod profile, allowed elsewhere (P-034) */
    @Test
    void the_development_key_does_not_start_in_production() {
        var production = new MockEnvironment();
        production.setActiveProfiles("prod");
        assertThatThrownBy(() -> new EncryptionConfig().secretEncryptor(properties(Map.of(
                "access-control.encryption.password", EncryptionConfig.DEVELOPMENT_PASSWORD,
                "access-control.encryption.salt", EncryptionConfig.DEVELOPMENT_SALT)), production))
                .hasMessageContaining("AC_ENCRYPTION_PASSWORD");

        for (var development : new String[][] {
                {EncryptionConfig.DEVELOPMENT_PASSWORD, SALT}, {"a-password-of-its-own", EncryptionConfig.DEVELOPMENT_SALT}}) {
            assertThatThrownBy(() -> EncryptionConfig.check(development[0], development[1], true))
                    .isInstanceOf(IllegalStateException.class);
            assertThatCode(() -> EncryptionConfig.check(development[0], development[1], false)).doesNotThrowAnyException();
        }
        assertThatCode(() -> EncryptionConfig.check("a-password-of-its-own", SALT, true)).doesNotThrowAnyException();
    }

    private static AccessControlProperties properties(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values)).bindOrCreate("access-control", AccessControlProperties.class);
    }
}
