package com.adminstarter.accesscontrol.identity.service;

import java.util.UUID;

/**
 * Passwords and authenticator codes, for sign-in and the links in emails. Wrong answers count: after too many in a
 * row the account is locked for a while (LockoutPolicy), and while locked even the right password is refused. They
 * count apart where the person signed in from before ({@code familiar}) and anywhere else, so a stranger guessing
 * from elsewhere does not lock the person out where they usually sign in (P-035); a signed-in person is familiar.
 */
public interface CredentialService {

    boolean checkPassword(UUID userId, String password, boolean familiar);

    default boolean checkPassword(UUID userId, String password) {
        return checkPassword(userId, password, true);
    }

    /** Locked by too many wrong answers in a row: even the right password is refused for now (LockoutPolicy) */
    boolean lockedNow(UUID userId, boolean familiar);

    default boolean lockedNow(UUID userId) {
        return lockedNow(userId, true);
    }

    /** Takes as long as checking a real password, so an unknown email cannot be told apart by timing */
    void checkNoPassword(String password);

    /** A code from the app, or an unused recovery code, which is then used up (P-025); a wrong one counts like a wrong password */
    boolean checkTwoFactorCode(UUID userId, String code, boolean familiar);

    default boolean checkTwoFactorCode(UUID userId, String code) {
        return checkTwoFactorCode(userId, code, true);
    }

    /** Sign-in went through: the count of wrong attempts from there starts again */
    void signInSucceeded(UUID userId, boolean familiar);

    default void signInSucceeded(UUID userId) {
        signInSucceeded(userId, true);
    }

    /**
     * Sets a password from an email link; weak_password or password_reused when it breaks the password rules of the
     * settings (P-017). Ends the user's sessions.
     */
    void setPassword(UUID userId, String password);
}
