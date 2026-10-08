package com.adminstarter.accesscontrol.authentication.dto;

import com.adminstarter.accesscontrol.identity.dto.UserResponse;

/**
 * The answer to sign-in and refresh; the refresh cookie travels in a header beside it.
 *
 * @param expiresIn seconds until the access token runs out
 */
public record TokenResponse(String accessToken, long expiresIn, UserResponse user) {}
