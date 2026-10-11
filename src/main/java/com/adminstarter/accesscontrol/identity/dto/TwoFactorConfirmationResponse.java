package com.adminstarter.accesscontrol.identity.dto;

import java.util.List;

/** TwoFactorConfirmation: two-step sign-in is on, and these are the first recovery codes, shown this once (P-025) */
public record TwoFactorConfirmationResponse(UserResponse user, List<String> recoveryCodes) {}
