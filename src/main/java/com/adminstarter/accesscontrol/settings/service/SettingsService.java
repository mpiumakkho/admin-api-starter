package com.adminstarter.accesscontrol.settings.service;

import com.adminstarter.accesscontrol.settings.dto.SettingsResponse;

/** What other modules may read of the settings */
public interface SettingsService {

    SettingsResponse current();
}
