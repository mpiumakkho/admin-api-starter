package com.adminstarter.accesscontrol.identity.policy;

import static com.adminstarter.accesscontrol.common.constant.ErrorCode.PASSWORD_REUSED;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.WEAK_PASSWORD;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * The rules a new password must follow wherever it is set: changing your own, accepting an invitation, or a reset
 * link (API.md, "Password rules"; P-017). The numbers come from the settings: passwordMinLength (8 to 64) and
 * passwordHistory (0 to 24), as checkNewPassword() in local-admin-api.ts uses them.
 */
public final class PasswordPolicy {

    /**
     * The most earlier passwords kept per user: enough for the largest passwordHistory, even after an administrator's
     * reset moved the current password into the history.
     */
    public static final int KEPT = 24;

    private PasswordPolicy() {}

    /** weak_password when shorter than {@code minLength} */
    public static void check(String password, int minLength) {
        if (password.length() < minLength) {
            throw WEAK_PASSWORD.exception();
        }
    }

    /**
     * The hashes a new password must not match: the newest {@code history} of the user's passwords, the current one
     * first when there is one (after an administrator's reset there is none, and the password it replaced is the
     * newest earlier one). None when {@code history} is 0.
     *
     * @param earlierHashes the user's earlier passwords, newest first
     */
    public static List<String> recentHashes(@Nullable String currentHash, List<String> earlierHashes, int history) {
        return Stream.concat(Stream.ofNullable(currentHash), earlierHashes.stream()).limit(history).toList();
    }

    /** password_reused when {@code password} matches one of {@code recentHashes} */
    public static void checkNotReused(String password, List<String> recentHashes, BiPredicate<String, String> matches) {
        if (recentHashes.stream().anyMatch(hash -> matches.test(password, hash))) {
            throw PASSWORD_REUSED.exception();
        }
    }

    /** Of the earlier passwords, newest first, the ones to drop before adding another, so that at most KEPT remain */
    public static <T> List<T> beyondKept(List<T> earlier) {
        return earlier.size() < KEPT ? List.of() : List.copyOf(earlier.subList(KEPT - 1, earlier.size()));
    }
}
