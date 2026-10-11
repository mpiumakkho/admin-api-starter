package com.adminstarter.accesscontrol.common.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.adminstarter.accesscontrol.common.constant.ErrorCode;
import com.adminstarter.accesscontrol.common.exception.AccessControlException;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActorTest {

    private final UUID chiangMai = UUID.randomUUID();
    private final UUID phuket = UUID.randomUUID();
    private final Actor actor = new Actor(UUID.randomUUID(), UUID.randomUUID(), Set.of(), Set.of("users.view"),
            Map.of(chiangMai, Set.of("users.edit")), false, true);

    @Test
    void a_role_for_a_branch_allows_its_permissions_there_only() {
        assertThat(actor.can("users.edit", chiangMai)).isTrue();
        assertThat(actor.can("users.edit", phuket)).isFalse();
        assertThat(actor.can("users.edit", null)).isFalse();
        // What user management checks: the whole organization
        assertThat(actor.has("users.edit")).isFalse();
    }

    @Test
    void a_role_for_the_whole_organization_allows_its_permissions_in_every_branch() {
        assertThat(actor.can("users.view", phuket)).isTrue();
        assertThat(actor.can("users.view", null)).isTrue();
        assertThat(new Actor(UUID.randomUUID(), UUID.randomUUID(), Set.of(), Set.of(), false).branchPermissions()).isEmpty();
    }

    @Test
    void a_risky_change_needs_a_recent_sign_in() {
        assertThatCode(actor::requireRecentSignIn).doesNotThrowAnyException();
        var away = new Actor(actor.userId(), actor.sessionId(), Set.of(), Set.of("users.view"), Map.of(), false, false);
        assertThatCode(away::requireRecentSignIn).isInstanceOfSatisfying(AccessControlException.class,
                refused -> assertThat(refused.code()).isEqualTo(ErrorCode.REAUTH_REQUIRED));
    }
}
