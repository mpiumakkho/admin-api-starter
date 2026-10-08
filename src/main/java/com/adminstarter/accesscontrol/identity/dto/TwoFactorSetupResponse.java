package com.adminstarter.accesscontrol.identity.dto;

/**
 * TwoFactorSetup in admin-ng-starter's models.ts
 *
 * @param secret base32, for typing in by hand
 * @param uri otpauth:// address, for the QR code
 */
public record TwoFactorSetupResponse(String secret, String uri) {}
