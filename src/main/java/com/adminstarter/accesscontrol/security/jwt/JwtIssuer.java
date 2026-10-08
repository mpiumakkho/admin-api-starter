package com.adminstarter.accesscontrol.security.jwt;

import com.adminstarter.accesscontrol.AccessControlProperties;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Issues access tokens. They say who (sub) and which sign-in (sid), and nothing about permissions, roles or email:
 * those are looked up on every request, so a change takes effect at once rather than when the token runs out.
 */
@Component
public class JwtIssuer {

    /** The session (one sign-in) a token belongs to */
    public static final String SESSION_CLAIM = "sid";

    private final JwtEncoder encoder;
    private final JwtKeyProvider keys;
    private final Clock clock;
    private final String issuer;
    private final Duration ttl;

    public JwtIssuer(JwtEncoder encoder, JwtKeyProvider keys, Clock clock, AccessControlProperties properties) {
        this.encoder = encoder;
        this.keys = keys;
        this.clock = clock;
        this.issuer = properties.issuer();
        this.ttl = properties.token().accessTtl();
    }

    public IssuedToken issue(UUID userId, UUID sessionId) {
        var now = clock.instant();
        var claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .id(UUID.randomUUID().toString())
                .claim(SESSION_CLAIM, sessionId.toString())
                .build();
        var header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(keys.signingKey().getKeyID()).build();
        var token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, ttl.toSeconds());
    }

    /** @param expiresIn seconds */
    public record IssuedToken(String value, long expiresIn) {}
}
