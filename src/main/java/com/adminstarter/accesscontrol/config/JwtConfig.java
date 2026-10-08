package com.adminstarter.accesscontrol.config;

import com.adminstarter.accesscontrol.AccessControlProperties;
import com.adminstarter.accesscontrol.security.jwt.JwtKeyProvider;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Nimbus encoder and decoder over JwtKeyProvider's keys: sign with the newest, accept any of them by kid */
@Configuration
public class JwtConfig {

    @Bean
    JwtEncoder jwtEncoder(JwtKeyProvider keys) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(keys.signingKey())));
    }

    @Bean
    JwtDecoder jwtDecoder(JwtKeyProvider keys, AccessControlProperties properties) {
        var processor = new DefaultJWTProcessor<SecurityContext>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256,
                new ImmutableJWKSet<>(new JWKSet(keys.verificationKeys()))));
        // Spring's validators below check the time and issuer claims
        processor.setJWTClaimsSetVerifier((claims, context) -> {});
        var decoder = new NimbusJwtDecoder(processor);
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }
}
