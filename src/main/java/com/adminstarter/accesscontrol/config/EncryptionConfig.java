package com.adminstarter.accesscontrol.config;

import com.adminstarter.accesscontrol.AccessControlProperties;
import java.util.HexFormat;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;

/**
 * Encrypts secrets kept in the database (P-014): AES-256-GCM, a new random IV each time, the key derived from
 * access-control.encryption.password and salt. Without them the application does not start: a missing key must not
 * quietly leave secrets readable. Nor does it start in the prod profile with the development key, which
 * application.yml publishes to everyone who can read this repository (P-034).
 */
@Configuration
public class EncryptionConfig {

    private static final Logger log = LoggerFactory.getLogger(EncryptionConfig.class);

    /** The development key in application.yml */
    static final String DEVELOPMENT_PASSWORD = "development-only-encryption-password";
    static final String DEVELOPMENT_SALT = "9f2d6c1a84e03b57d1c6a2e9f0b47c38";

    @Bean
    TextEncryptor secretEncryptor(AccessControlProperties properties, Environment environment) {
        var encryption = properties.encryption();
        check(encryption.password(), encryption.salt(), environment.matchesProfiles("prod"));
        return Encryptors.delux(encryption.password(), encryption.salt());
    }

    /** A key to encrypt with: given, and in production not the development one; the start fails otherwise */
    static void check(@Nullable String password, @Nullable String salt, boolean production) {
        if (password == null || password.isBlank() || salt == null || !isHex(salt)) {
            throw new IllegalStateException("Set access-control.encryption.password and a hex salt "
                    + "(AC_ENCRYPTION_PASSWORD, AC_ENCRYPTION_SALT; tools/gen-keys.sh makes them)");
        }
        var development = DEVELOPMENT_PASSWORD.equals(password) || DEVELOPMENT_SALT.equalsIgnoreCase(salt);
        if (development && production) {
            throw new IllegalStateException("The prod profile is set to the development encryption key from "
                    + "application.yml: set AC_ENCRYPTION_PASSWORD and AC_ENCRYPTION_SALT (tools/gen-keys.sh makes them)");
        }
        if (development) {
            log.warn("Encrypting with the development key from application.yml: run production with "
                    + "SPRING_PROFILES_ACTIVE=prod and keys of its own");
        }
    }

    private static boolean isHex(String text) {
        try {
            return !text.isEmpty() && HexFormat.of().parseHex(text).length >= 8;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
