package com.adminstarter.accesscontrol.settings.dto;

/**
 * PasswordPolicy in admin-ng-starter's models.ts: what a new password must be, readable before signing in
 *
 * @param minLength the settings' passwordMinLength
 * @param history the settings' passwordHistory: how many recent passwords, the current one included, are refused
 */
public record PasswordPolicyResponse(int minLength, int history) {}
