package com.adminstarter.accesscontrol.identity.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adminstarter.accesscontrol.common.constant.ErrorCode;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    @Test
    void a_password_must_be_as_long_as_the_settings_say() {
        assertThatThrownBy(() -> PasswordPolicy.check("1234567", 8)).extracting("code").isEqualTo(ErrorCode.WEAK_PASSWORD);
        assertThatCode(() -> PasswordPolicy.check("12345678", 8)).doesNotThrowAnyException();
        assertThatThrownBy(() -> PasswordPolicy.check("12345678901", 12)).extracting("code").isEqualTo(ErrorCode.WEAK_PASSWORD);
    }

    @Test
    void the_recent_passwords_are_the_newest_of_the_current_and_the_earlier_ones() {
        var earlier = List.of("b", "c", "d");
        assertThat(PasswordPolicy.recentHashes("a", earlier, 0)).isEmpty();
        assertThat(PasswordPolicy.recentHashes("a", earlier, 1)).containsExactly("a");
        assertThat(PasswordPolicy.recentHashes("a", earlier, 3)).containsExactly("a", "b", "c");
        assertThat(PasswordPolicy.recentHashes("a", earlier, 24)).containsExactly("a", "b", "c", "d");
        // After an administrator's reset there is no current password: the one it replaced counts first
        assertThat(PasswordPolicy.recentHashes(null, earlier, 2)).containsExactly("b", "c");
        assertThat(PasswordPolicy.recentHashes(null, List.of(), 5)).isEmpty();
    }

    @Test
    void a_password_matching_a_recent_one_is_refused() {
        var recent = List.of("hash:old", "hash:older");
        assertThatThrownBy(() -> PasswordPolicy.checkNotReused("older", recent, (password, hash) -> hash.equals("hash:" + password)))
                .extracting("code").isEqualTo(ErrorCode.PASSWORD_REUSED);
        assertThatCode(() -> PasswordPolicy.checkNotReused("new", recent, (password, hash) -> hash.equals("hash:" + password)))
                .doesNotThrowAnyException();
        assertThatCode(() -> PasswordPolicy.checkNotReused("old", List.of(), (password, hash) -> true)).doesNotThrowAnyException();
    }

    @Test
    void at_most_twenty_four_earlier_passwords_are_kept() {
        assertThat(PasswordPolicy.beyondKept(numbers(10))).isEmpty();
        assertThat(PasswordPolicy.beyondKept(numbers(23))).isEmpty();
        // One more is about to be added: the oldest goes
        assertThat(PasswordPolicy.beyondKept(numbers(24))).containsExactly(23);
        assertThat(PasswordPolicy.beyondKept(numbers(26))).containsExactly(23, 24, 25);
    }

    /** 0 (newest) to count − 1 (oldest) */
    private static List<Integer> numbers(int count) {
        return IntStream.range(0, count).boxed().toList();
    }
}
