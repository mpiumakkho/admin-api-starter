package com.adminstarter.accesscontrol.config;

import com.adminstarter.accesscontrol.AccessControlProperties;
import java.util.HexFormat;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;

/**
 * Encrypts secrets kept in the database (P-014): AES-256-GCM, a new random IV each time, the key derived from
 * access-control.encryption.password and salt. Without them the application does not start: a missing key must not
 * quietly leave secrets readable.
 */
@Configuration
public class EncryptionConfig {

    @Bean
    TextEncryptor secretEncryptor(AccessControlProperties properties) {
        var encryption = properties.encryption();
        var password = encryption.password();
        var salt = encryption.salt();
        if (password == null || password.isBlank() || salt == null || !isHex(salt)) {
            throw new IllegalStateException("Set access-control.encryption.password and a hex salt "
                    + "(AC_ENCRYPTION_PASSWORD, AC_ENCRYPTION_SALT; tools/gen-keys.sh makes them)");
        }
        return Encryptors.delux(password, salt);
    }

    private static boolean isHex(String text) {
        try {
            return !text.isEmpty() && HexFormat.of().parseHex(text).length >= 8;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
