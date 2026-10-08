package com.adminstarter.accesscontrol.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adminstarter.accesscontrol.AccessControlProperties;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class JwtKeyProviderTest {

    @Test
    void reads_pem_keys_signs_with_the_last_and_verifies_with_all() throws NoSuchAlgorithmException {
        var properties = new HashMap<String, String>();
        addKey(properties, 0, "old", true);
        // As tools/gen-keys.sh prints it: one line, no PEM header
        addKey(properties, 1, "new", false);
        var keys = new JwtKeyProvider(bind(properties));

        assertThat(keys.signingKey().getKeyID()).isEqualTo("new");
        assertThat(keys.signingKey().isPrivate()).isTrue();
        assertThat(keys.verificationKeys()).extracting("keyID").containsExactly("old", "new");
        assertThat(keys.verificationKeys()).noneMatch(key -> key.isPrivate());
    }

    @Test
    void without_keys_one_is_made_for_development() {
        var keys = new JwtKeyProvider(bind(new HashMap<>()));
        assertThat(keys.signingKey().getKeyID()).startsWith("dev-");
    }

    @Test
    void a_key_that_is_not_rsa_pem_stops_the_start() {
        var properties = new HashMap<String, String>();
        properties.put("access-control.token.keys[0].kid", "broken");
        properties.put("access-control.token.keys[0].private-key", "bm90IGEga2V5");
        properties.put("access-control.token.keys[0].public-key", "bm90IGEga2V5");
        assertThatThrownBy(() -> new JwtKeyProvider(bind(properties))).hasMessageContaining("broken");
    }

    private static void addKey(HashMap<String, String> properties, int index, String kid, boolean pemHeaders)
            throws NoSuchAlgorithmException {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var prefix = "access-control.token.keys[" + index + "].";
        properties.put(prefix + "kid", kid);
        properties.put(prefix + "private-key", pem("PRIVATE KEY", pair.getPrivate().getEncoded(), pemHeaders));
        properties.put(prefix + "public-key", pem("PUBLIC KEY", pair.getPublic().getEncoded(), pemHeaders));
    }

    private static String pem(String type, byte[] der, boolean headers) {
        var body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
        return headers ? "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n" : body.replace("\n", "");
    }

    private static AccessControlProperties bind(HashMap<String, String> properties) {
        return new Binder(new MapConfigurationPropertySource(properties)).bindOrCreate("access-control", AccessControlProperties.class);
    }
}
