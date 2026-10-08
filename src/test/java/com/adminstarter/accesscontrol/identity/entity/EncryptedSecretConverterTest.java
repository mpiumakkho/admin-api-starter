package com.adminstarter.accesscontrol.identity.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.encrypt.Encryptors;

class EncryptedSecretConverterTest {

    private final EncryptedSecretConverter converter =
            new EncryptedSecretConverter(Encryptors.delux("test-password", "9f2d6c1a84e03b57"));

    @Test
    void stores_a_secret_encrypted_and_reads_it_back() {
        var column = converter.convertToDatabaseColumn("JBSWY3DPEHPK3PXP");
        assertThat(column).startsWith(EncryptedSecretConverter.PREFIX).doesNotContain("JBSWY3DPEHPK3PXP");
        assertThat(converter.convertToEntityAttribute(column)).isEqualTo("JBSWY3DPEHPK3PXP");
    }

    @Test
    void the_same_secret_encrypts_differently_each_time() {
        assertThat(converter.convertToDatabaseColumn("JBSWY3DPEHPK3PXP"))
                .isNotEqualTo(converter.convertToDatabaseColumn("JBSWY3DPEHPK3PXP"));
    }

    @Test
    void reads_a_value_written_before_encryption_as_it_is() {
        assertThat(converter.convertToEntityAttribute("JBSWY3DPEHPK3PXP")).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(converter.convertToEntityAttribute(null)).isNull();
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void another_key_cannot_read_it() {
        var column = converter.convertToDatabaseColumn("JBSWY3DPEHPK3PXP");
        var other = new EncryptedSecretConverter(Encryptors.delux("other-password", "9f2d6c1a84e03b57"));
        assertThatThrownBy(() -> other.convertToEntityAttribute(column)).isInstanceOf(IllegalStateException.class);
    }
}
