package com.adminstarter.accesscontrol.identity.policy;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adminstarter.accesscontrol.common.constant.ErrorCode;
import com.adminstarter.accesscontrol.common.context.Actor;
import com.adminstarter.accesscontrol.common.exception.AccessControlException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SelfProtectionPolicyTest {

    private final Actor actor = new Actor(UUID.randomUUID(), UUID.randomUUID(), Set.of(UUID.randomUUID()), Set.of("users.view"), false);
    private final UUID someoneElse = UUID.randomUUID();

    @Test
    void nobody_suspends_or_deletes_a_list_that_includes_themselves() {
        assertThatThrownBy(() -> SelfProtectionPolicy.forbidSelf(actor, List.of(someoneElse, actor.userId())))
                .isInstanceOf(AccessControlException.class)
                .extracting("code").isEqualTo(ErrorCode.SELF_CHANGE);
        assertThatCode(() -> SelfProtectionPolicy.forbidSelf(actor, List.of(someoneElse))).doesNotThrowAnyException();
    }

    @Test
    void nobody_changes_their_own_status() {
        assertThatThrownBy(() -> SelfProtectionPolicy.forbidOwnStatus(actor, actor.userId(), true))
                .extracting("code").isEqualTo(ErrorCode.SELF_CHANGE);
        assertThatCode(() -> SelfProtectionPolicy.forbidOwnStatus(actor, actor.userId(), false))
                .doesNotThrowAnyException();
        assertThatCode(() -> SelfProtectionPolicy.forbidOwnStatus(actor, someoneElse, true))
                .doesNotThrowAnyException();
    }

    @Test
    void without_users_edit_people_change_only_their_own_name_phone_and_photo() {
        assertThatCode(() -> SelfProtectionPolicy.requireSelfEditable(actor, actor.userId(), List.of("firstName", "avatar")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> SelfProtectionPolicy.requireSelfEditable(actor, actor.userId(), List.of("email")))
                .extracting("code").isEqualTo(ErrorCode.FORBIDDEN);
        assertThatThrownBy(() -> SelfProtectionPolicy.requireSelfEditable(actor, someoneElse, List.of("phone")))
                .extracting("code").isEqualTo(ErrorCode.FORBIDDEN);
    }
}
