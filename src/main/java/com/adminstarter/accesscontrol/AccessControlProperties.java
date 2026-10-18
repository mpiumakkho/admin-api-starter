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
 * @param timeZone the organization's time zone, such as "Asia/Bangkok": the times in emails and the hour the nightly
 *     jobs run at (P-030)
 */
@ConfigurationProperties("access-control")
public record AccessControlProperties(
        @DefaultValue("admin-api-starter") String issuer,
        @DefaultValue("Admin") String productName,
        @DefaultValue("Asia/Bangkok") String timeZone,
        @DefaultValue Token token,
        @DefaultValue Cookie cookie,
        @DefaultValue Lockout lockout,
        @DefaultValue Cors cors,
        @DefaultValue RateLimit rateLimit,
        @DefaultValue("60s") Duration permissionCacheTtl,
        @DefaultValue Audit audit,
        @DefaultValue Mail mail,
        @DefaultValue Encryption encryption,
        @DefaultValue Bootstrap bootstrap) {

    /**
     * For {@code @Scheduled(cron = …, zone = TIME_ZONE)}: a job runs at the hour its cron says in {@link #timeZone()},
     * not in the server's time zone, which is UTC in a container (P-030)
     */
    public static final String TIME_ZONE = "${access-control.time-zone:Asia/Bangkok}";

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

    /**
     * After {@code maxAttempts} wrong passwords in a row, lock for baseDuration, doubling up to maxDuration; counted apart
     * for the addresses the person signed in from before and for any other, and starting again after resetAfter with
     * nothing new (P-035)
     */
    public record Lockout(
            @DefaultValue("5") int maxAttempts,
            @DefaultValue("1m") Duration baseDuration,
            @DefaultValue("30m") Duration maxDuration,
            @DefaultValue("24h") Duration resetAfter) {}

    /** Origins of the dashboard, for CORS and for the Origin check on the cookie endpoints */
    public record Cors(@DefaultValue List<String> allowedOrigins) {}

    /** Requests per minute from one IP address */
    public record RateLimit(
            @DefaultValue("10") int login,
            @DefaultValue("30") int refresh,
            @DefaultValue("5") int emailLinks) {}

    /**
     * How long activity is kept is a setting (activityRetentionDays, P-018), not configuration.
     *
     * @param listLimit the most entries GET /activity answers with, newest first
     */
    public record Audit(@DefaultValue("10000") int listLimit) {}

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
     * @param adminPassword the first administrator's password, at least the password rules' length; required while there
     *     are no users, and never written to the log (P-033)
     * @param defaultRole the code of the role new users get, until someone changes it on the settings page
     */
    public record Bootstrap(
            @DefaultValue("admin@example.com") String adminEmail,
            @Nullable String adminPassword,
            @DefaultValue("VIEWER") String defaultRole) {}
}
