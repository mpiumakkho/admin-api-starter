package com.adminstarter.accesscontrol.authorization.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GrantPolicyTest {

    private static final Set<String> MANAGER = Set.of("users.view", "users.create", "users.edit", "roles.view");

    @Test
    void someone_can_give_out_only_what_they_hold() {
        assertThat(GrantPolicy.holdsAll(MANAGER, List.of("users.view", "users.edit"))).isTrue();
        assertThat(GrantPolicy.holdsAll(MANAGER, List.of("users.view", "users.delete"))).isFalse();
    }

    @Test
    void giving_out_nothing_is_always_allowed() {
        assertThat(GrantPolicy.holdsAll(Set.of(), List.of())).isTrue();
    }

    @Test
    void someone_manages_only_roles_that_allow_no_more_than_their_own() {
        assertThat(GrantPolicy.canManageRole(MANAGER, Set.of("users.view", "roles.view"))).isTrue();
        assertThat(GrantPolicy.canManageRole(MANAGER, MANAGER)).isTrue();
        assertThat(GrantPolicy.canManageRole(MANAGER, Set.of("users.view", "settings.manage"))).isFalse();
    }
}
