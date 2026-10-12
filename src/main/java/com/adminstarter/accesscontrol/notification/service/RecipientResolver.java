package com.adminstarter.accesscontrol.notification.service;

import com.adminstarter.accesscontrol.identity.service.UserService;
import com.adminstarter.accesscontrol.notification.service.NotificationService.Recipient;
import com.adminstarter.accesscontrol.settings.service.SettingsService;
import java.util.Locale;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Who an email goes to (P-029): the person's address, first name and language, the one they last used the app in, or,
 * until it is known, the settings' defaultLanguage
 */
@Service
public class RecipientResolver {

    private final UserService users;
    private final SettingsService settings;

    public RecipientResolver(UserService users, SettingsService settings) {
        this.users = users;
        this.settings = settings;
    }

    /** At the address they have now */
    public Recipient of(UUID userId) {
        var user = users.get(userId);
        return new Recipient(user.email(), user.firstName(), locale(user.language()));
    }

    /** At another address of theirs, such as the one their email was changed from */
    public Recipient of(UUID userId, String email) {
        var user = users.get(userId);
        return new Recipient(email, user.firstName(), locale(user.language()));
    }

    private Locale locale(@Nullable String language) {
        return Locale.forLanguageTag(language != null ? language : settings.current().defaultLanguage());
    }
}
