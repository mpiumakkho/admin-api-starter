package com.adminstarter.accesscontrol.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** The prod profile does not start unless the database is reached over TLS with its certificate checked (P-041) */
class DatabaseTlsConfigTest {

    @Test
    void production_needs_sslmode_verify_full() {
        for (var url : new String[] {null, "jdbc:postgresql://db:5432/admin", "jdbc:postgresql://db:5432/admin?sslmode=prefer",
                "jdbc:postgresql://db:5432/admin?sslmode=require", "jdbc:postgresql://db:5432/admin?sslmode=verify-ca",
                "jdbc:postgresql://db:5432/admin?ssl=true&sslmode=verify-fullish"}) {
            assertThatThrownBy(() -> DatabaseTlsConfig.check(url, true)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("sslmode=verify-full");
        }
        assertThatCode(() -> DatabaseTlsConfig.check("jdbc:postgresql://db:5432/admin?sslmode=verify-full", true))
                .doesNotThrowAnyException();
        assertThatCode(() -> DatabaseTlsConfig.check(
                "jdbc:postgresql://db:5432/admin?sslmode=verify-full&sslrootcert=/certs/root.crt", true))
                .doesNotThrowAnyException();
    }

    @Test
    void development_takes_the_local_database_as_it_is() {
        assertThatCode(() -> DatabaseTlsConfig.check("jdbc:postgresql://localhost:5433/admin", false)).doesNotThrowAnyException();
    }
}
