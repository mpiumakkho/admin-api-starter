package com.adminstarter.accesscontrol;

import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Every setting of the foundation, under {@code access-control.*}. The defaults are the ones API.md describes;
 * application.yml holds the development values and application-prod.yml what production changes.
 *
 * @param issuer the JWT {@code iss} claim
 * @param productName the name in emails and in authenticator apps next to the codes
 */
@ConfigurationProperties("access-control")
public record AccessControlProperties(
        @DefaultValue("admin-api-starter") String issuer,
        @DefaultValue("Admin") String productName,
        @DefaultValue Token token,
        @DefaultValue Cookie cookie,
        @DefaultValue Password password,
        @DefaultValue Lockout lockout,
        @DefaultValue Cors cors,
        @DefaultValue RateLimit rateLimit,
        @DefaultValue("60s") Duration permissionCacheTtl,
        @DefaultValue Audit audit,
        @DefaultValue Mail mail,
        @DefaultValue Encryption encryption,
        @DefaultValue Bootstrap bootstrap) {

    /**
     * @param accessTtl how long an access token works
     * @param refreshTtl how long a session lasts without a refresh, when not remembered
     * @param rememberTtl how long a remembered session lasts without a refresh
     * @param reuseGrace how long a replaced refresh token still gets an access token, for two tabs refreshing at once;
     *     after that, using it ends the session as stolen
     * @param keys RS256 key pairs in PEM, newest last; tokens are signed with the last and checked against all.
     *     Empty: a key made at startup, so tokens stop working on restart (development only)
     */
    public record Token(
            @DefaultValue("15m") Duration accessTtl,
            @DefaultValue("12h") Duration refreshTtl,
            @DefaultValue("30d") Duration rememberTtl,
            @DefaultValue("30s") Duration reuseGrace,
            @DefaultValue List<Key> keys) {}

    public record Key(String kid, String privateKey, String publicKey) {}

    /** The refresh cookie; {@code __Host-} names cannot be used because the cookie is limited to {@code path} */
    public record Cookie(
            @DefaultValue("__Secure-ac_rt") String name,
            @DefaultValue("/api/auth") String path,
            @DefaultValue("true") boolean secure) {}

    public record Password(@DefaultValue("8") int minLength) {}

    /** After {@code maxAttempts} wrong passwords in a row, lock for baseDuration, doubling up to maxDuration */
    public record Lockout(
            @DefaultValue("5") int maxAttempts,
            @DefaultValue("1m") Duration baseDuration,
            @DefaultValue("30m") Duration maxDuration) {}

    /** Origins of the dashboard, for CORS and for the Origin check on the cookie endpoints */
    public record Cors(@DefaultValue List<String> allowedOrigins) {}

    /** Requests per minute from one IP address */
    public record RateLimit(
            @DefaultValue("10") int login,
            @DefaultValue("30") int refresh,
            @DefaultValue("5") int emailLinks) {}

    /**
     * @param retentionDays entries older than this are deleted every night
     * @param listLimit the most entries GET /activity answers with, newest first
     */
    public record Audit(@DefaultValue("730") int retentionDays, @DefaultValue("10000") int listLimit) {}

    /**
     * @param baseUrl where the dashboard is, for the links in emails
     * @param resetLinkTtl how long a password reset link works; invitations follow the settings page
     */
    public record Mail(
            @DefaultValue("no-reply@localhost") String from,
            @DefaultValue("http://localhost:4200") String baseUrl,
            @DefaultValue("1h") Duration resetLinkTtl) {}

    /**
     * The key for secrets kept in the database, such as two-step sign-in secrets: AES-256-GCM with a key derived from
     * the password and salt. tools/gen-keys.sh makes a pair. Changing either makes the stored secrets unreadable.
     *
     * @param salt hex
     */
    public record Encryption(@Nullable String password, @Nullable String salt) {}

    /**
     * The first administrator, created when there are no users yet.
     *
     * @param adminPassword empty: a random one, written to the log once
     * @param defaultRole the code of the role new users get, until someone changes it on the settings page
     */
    public record Bootstrap(
            @DefaultValue("admin@example.com") String adminEmail,
            @Nullable String adminPassword,
            @DefaultValue("VIEWER") String defaultRole) {}
}
