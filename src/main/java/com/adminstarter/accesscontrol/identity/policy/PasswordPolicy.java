package com.adminstarter.accesscontrol.identity.policy;

import static com.adminstarter.accesscontrol.common.constant.ErrorCode.PASSWORD_REUSED;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.WEAK_PASSWORD;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * The rules a new password must follow wherever it is set: changing your own, accepting an invitation, or a reset
 * link (API.md, "Password rules"; P-017). The numbers come from the settings: passwordMinLength (8 to 64) and
 * passwordHistory (0 to 24), as checkNewPassword() in local-admin-api.ts uses them. Besides, as NIST SP 800-63B asks,
 * it is none of the 10,000 most used passwords and holds no word of the system or of the person (P-043).
 */
public final class PasswordPolicy {

    /**
     * The most earlier passwords kept per user: enough for the largest passwordHistory, even after an administrator's
     * reset moved the current password into the history.
     */
    public static final int KEPT = 24;

    /** The shortest word of the system or of the person that a password may not hold: shorter ones are in many */
    static final int SHORTEST_WORD = 4;

    private PasswordPolicy() {}

    /** weak_password when shorter than {@code minLength} or one of the most used passwords */
    public static void check(String password, int minLength) {
        check(password, minLength, List.of());
    }

    /**
     * weak_password when shorter than {@code minLength}, one of the most used passwords, or holding one of {@code words}
     * (from {@link #contextWords}), case aside
     */
    public static void check(String password, int minLength, Collection<String> words) {
        var lower = password.toLowerCase(Locale.ROOT);
        if (password.length() < minLength || Common.PASSWORDS.contains(lower) || words.stream().anyMatch(lower::contains)) {
            throw WEAK_PASSWORD.exception();
        }
    }

    /**
     * The words a password must not hold: the product's name and the person's names and email, each split at what is
     * not a letter or a digit ("somchai.k@acme.co.th" gives "somchai", "acme"), lower-cased, those of SHORTEST_WORD
     * characters or more
     */
    public static Set<String> contextWords(String productName, String firstName, String lastName, String email) {
        return Stream.of(productName, firstName, lastName, email)
                .filter(Objects::nonNull)
                .flatMap(text -> Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")))
                .filter(word -> word.length() >= SHORTEST_WORD)
                .collect(Collectors.toUnmodifiableSet());
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

    /** passwords/common.txt, read once, when a password is first checked */
    private static final class Common {

        static final Set<String> PASSWORDS = read();

        private static Set<String> read() {
            var file = PasswordPolicy.class.getResourceAsStream("/passwords/common.txt");
            if (file == null) {
                throw new IllegalStateException("passwords/common.txt is missing");
            }
            try (var lines = new BufferedReader(new InputStreamReader(file, StandardCharsets.UTF_8))) {
                return lines.lines().filter(line -> !line.isBlank() && !line.startsWith("#"))
                        .collect(Collectors.toUnmodifiableSet());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
