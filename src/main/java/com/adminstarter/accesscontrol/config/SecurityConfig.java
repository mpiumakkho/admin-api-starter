package com.adminstarter.accesscontrol.config;

import com.adminstarter.accesscontrol.AccessControlProperties;
import com.adminstarter.accesscontrol.security.PermissionAuthenticationConverter;
import com.adminstarter.accesscontrol.security.RateLimitFilter;
import com.adminstarter.accesscontrol.security.RequestContextFilter;
import com.adminstarter.accesscontrol.security.SecurityProblemHandler;
import jakarta.servlet.DispatcherType;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.session.DisableEncodeUrlFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Who may call what. Every /api request needs a valid access token except /api/auth/**, which works from the
 * refresh cookie or nothing at all; what each endpoint further needs is on its @PreAuthorize. No HTTP session is
 * ever created. CSRF tokens are off: the API reads bearer tokens, never cookies, apart from /api/auth, whose cookie
 * is SameSite=Strict and whose endpoints check the Origin header.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, PermissionAuthenticationConverter converter,
            SecurityProblemHandler problems, AccessControlProperties properties) throws Exception {
        var bearerTokens = new DefaultBearerTokenResolver();
        var requestContext = new RequestContextFilter();
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        // The event stream's later dispatches and error pages run after the request was let in
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        // An old token sent along to /api/auth must not get a sign-out or a refresh refused
                        .bearerTokenResolver(request -> request.getRequestURI().startsWith("/api/auth/")
                                ? null : bearerTokens.resolve(request))
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems))
                .addFilterBefore(requestContext, DisableEncodeUrlFilter.class)
                .addFilterAfter(new RateLimitFilter(properties, problems), RequestContextFilter.class);
        return http.build();
    }

    /** The dashboard's origins may call the API, with credentials so the refresh cookie travels */
    @Bean
    CorsConfigurationSource corsConfigurationSource(AccessControlProperties properties) {
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.cors().allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", RequestContextFilter.HEADER));
        cors.setExposedHeaders(List.of(RequestContextFilter.HEADER));
        cors.setAllowCredentials(true);
        cors.setMaxAge(Duration.ofHours(1));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }

    /** Argon2id for new hashes; BCrypt hashes, if any are imported, still match */
    @Bean
    PasswordEncoder passwordEncoder() {
        var argon2 = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        return new DelegatingPasswordEncoder("argon2", Map.of("argon2", argon2, "bcrypt", new BCryptPasswordEncoder(12)));
    }
}
