package com.adminstarter.accesscontrol.identity.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.encrypt.TextEncryptor;

/**
 * Keeps a secret encrypted in its column and plain in the entity (P-014). Stored values carry a version prefix, so
 * the scheme can change later; a value without it was written before encryption and is read as it is, then
 * encrypted the next time it is saved. Hibernate gets this converter from Spring, which supplies the encryptor.
 */
@Converter
public class EncryptedSecretConverter implements AttributeConverter<@Nullable String, @Nullable String> {

    static final String PREFIX = "enc:v1:";

    private final TextEncryptor encryptor;

    public EncryptedSecretConverter(TextEncryptor encryptor) {
        this.encryptor = encryptor;
    }

    @Override
    public @Nullable String convertToDatabaseColumn(@Nullable String secret) {
        return secret == null ? null : PREFIX + encryptor.encrypt(secret);
    }

    @Override
    public @Nullable String convertToEntityAttribute(@Nullable String column) {
        if (column == null || !column.startsWith(PREFIX)) {
            return column;
        }
        return encryptor.decrypt(column.substring(PREFIX.length()));
    }
}
