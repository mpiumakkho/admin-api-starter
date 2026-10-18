package com.adminstarter.accesscontrol.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** The first administrator's password: given, and within the password rules; never made up and logged (P-033) */
class AdminAccountInitializerTest {

    @Test
    void without_a_password_the_start_says_what_to_set() {
        for (var missing : new String[] {null, "", "   "}) {
            assertThatThrownBy(() -> AdminAccountInitializer.passwordFor(missing, 8))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ACCESS_CONTROL_BOOTSTRAP_ADMIN_PASSWORD");
        }
    }

    @Test
    void a_password_shorter_than_the_rules_ask_is_refused_without_showing_it() {
        assertThatThrownBy(() -> AdminAccountInitializer.passwordFor("short-1", 8))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("8 characters")
                .hasMessageNotContaining("short-1");
    }

    @Test
    void a_password_within_the_rules_is_used_as_given() {
        assertThat(AdminAccountInitializer.passwordFor("change-me-123", 8)).isEqualTo("change-me-123");
    }
}
