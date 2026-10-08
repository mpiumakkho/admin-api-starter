package com.adminstarter.accesscontrol.identity.policy;

import static com.adminstarter.accesscontrol.common.constant.ErrorCode.WEAK_PASSWORD;

import com.adminstarter.accesscontrol.AccessControlProperties;
import org.springframework.stereotype.Component;

/** The shortest password someone can set: 8 characters, as NEW_PASSWORD_LENGTH in admin-ng-starter */
@Component
public class PasswordPolicy {

    private final int minLength;

    public PasswordPolicy(AccessControlProperties properties) {
        this.minLength = properties.password().minLength();
    }

    public void check(String password) {
        if (password.length() < minLength) {
            throw WEAK_PASSWORD.exception();
        }
    }
}
