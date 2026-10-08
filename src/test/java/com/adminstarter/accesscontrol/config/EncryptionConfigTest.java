package com.adminstarter.accesscontrol.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adminstarter.accesscontrol.AccessControlProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class EncryptionConfigTest {

    @Test
    void does_not_start_without_a_key() {
        assertThatThrownBy(() -> new EncryptionConfig().secretEncryptor(properties(Map.of())))
                .hasMessageContaining("access-control.encryption.password");
        assertThatThrownBy(() -> new EncryptionConfig().secretEncryptor(properties(Map.of(
                "access-control.encryption.password", "secret", "access-control.encryption.salt", "not hex"))))
                .hasMessageContaining("hex salt");
    }

    @Test
    void encrypts_with_the_configured_key() {
        var encryptor = new EncryptionConfig().secretEncryptor(properties(Map.of(
                "access-control.encryption.password", "secret", "access-control.encryption.salt", "9f2d6c1a84e03b57")));
        assertThat(encryptor.decrypt(encryptor.encrypt("JBSWY3DPEHPK3PXP"))).isEqualTo("JBSWY3DPEHPK3PXP");
    }

    private static AccessControlProperties properties(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values)).bindOrCreate("access-control", AccessControlProperties.class);
    }
}
