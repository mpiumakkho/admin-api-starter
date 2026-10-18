package com.adminstarter.accesscontrol.config;

import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * In the prod profile the database is reached over TLS, its certificate and host name checked (P-041):
 * spring.datasource.url (DB_URL) must ask for sslmode=verify-full, or the application does not start. The PostgreSQL
 * driver's own default, prefer, falls back to a connection in the clear without a word, and checks no certificate.
 */
@Configuration
public class DatabaseTlsConfig {

    private static final Pattern VERIFY_FULL = Pattern.compile("[?&]sslmode=verify-full(&|$)");

    DatabaseTlsConfig(Environment environment) {
        check(environment.getProperty("spring.datasource.url"), environment.matchesProfiles("prod"));
    }

    /** A database address that asks for TLS with the certificate checked, in production; the start fails otherwise */
    static void check(@Nullable String url, boolean production) {
        if (production && (url == null || !VERIFY_FULL.matcher(url).find())) {
            throw new IllegalStateException("The prod profile reaches the database over TLS only: give DB_URL "
                    + "sslmode=verify-full, such as jdbc:postgresql://db.example:5432/admin?sslmode=verify-full, and "
                    + "sslrootcert when its certificate is not from a public authority");
        }
    }
}
