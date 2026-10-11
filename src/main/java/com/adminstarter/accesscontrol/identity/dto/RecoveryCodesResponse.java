package com.adminstarter.accesscontrol.identity.dto;

import java.util.List;

/** New recovery codes, which replaced the old ones; shown this once (P-025) */
public record RecoveryCodesResponse(List<String> codes) {}
