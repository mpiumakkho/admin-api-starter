package com.adminstarter.accesscontrol.identity.service;

import java.util.UUID;

/**
 * Passwords and authenticator codes, for sign-in and the links in emails. Wrong answers count: after too many in a
 * row the account is locked for a while (LockoutPolicy), and while locked even the right password is refused.
 */
public interface CredentialService {

    boolean checkPassword(UUID userId, String password);

    /** Takes as long as checking a real password, so an unknown email cannot be told apart by timing */
    void checkNoPassword(String password);

    /** A code from the app, or an unused recovery code, which is then used up (P-025); a wrong one counts like a wrong password */
    boolean checkTwoFactorCode(UUID userId, String code);

    /** Sign-in went through: the count of wrong attempts starts again */
    void signInSucceeded(UUID userId);

    /**
     * Sets a password from an email link; weak_password or password_reused when it breaks the password rules of the
     * settings (P-017). Ends the user's sessions.
     */
    void setPassword(UUID userId, String password);
}
