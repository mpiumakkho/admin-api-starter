package com.adminstarter.accesscontrol.security.jwt;

import com.adminstarter.accesscontrol.AccessControlProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The RS256 keys access tokens are signed with. Tokens are signed with the newest key and checked against all of
 * them, so a new key can be added before the old one is retired (access-control.token.keys, newest last; generate
 * a pair with tools/gen-keys.sh). With no keys configured, one is made at startup: fine for development, but every
 * token stops working on restart and each server instance would have its own.
 */
@Component
public class JwtKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyProvider.class);

    private final List<RSAKey> keys;

    public JwtKeyProvider(AccessControlProperties properties) {
        var configured = properties.token().keys();
        if (configured.isEmpty()) {
            log.warn("No access-control.token.keys configured: signing access tokens with a key made at startup");
            this.keys = List.of(generate());
        } else {
            this.keys = configured.stream().map(JwtKeyProvider::parse).toList();
        }
    }

    public RSAKey signingKey() {
        return keys.getLast();
    }

    public List<JWK> verificationKeys() {
        return keys.stream().<JWK>map(RSAKey::toPublicJWK).toList();
    }

    private static RSAKey generate() {
        try {
            return new RSAKeyGenerator(2048).keyID("dev-" + UUID.randomUUID()).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not make an RSA key", e);
        }
    }

    private static RSAKey parse(AccessControlProperties.Key key) {
        try {
            var factory = KeyFactory.getInstance("RSA");
            var publicKey = (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(pem(key.publicKey())));
            var privateKey = (RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(pem(key.privateKey())));
            return new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(key.kid()).build();
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("access-control.token.keys: key " + key.kid() + " is not an RSA PEM pair", e);
        }
    }

    /** The bytes of a PEM block, or of bare base64 */
    private static byte[] pem(String text) {
        return Base64.getMimeDecoder().decode(text.replaceAll("-----(BEGIN|END) [A-Z ]+-----", ""));
    }
}
