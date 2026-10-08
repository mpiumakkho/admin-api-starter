package com.adminstarter.accesscontrol.security;

import com.adminstarter.accesscontrol.authorization.service.RoleService;
import com.adminstarter.accesscontrol.common.context.Actor;
import com.adminstarter.accesscontrol.identity.dto.AccountSummary;
import com.adminstarter.accesscontrol.identity.service.UserService;
import com.adminstarter.accesscontrol.security.jwt.JwtIssuer;
import com.adminstarter.accesscontrol.settings.service.SettingsService;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

/**
 * Turns a verified access token into an Actor, checking on every request what the token does not say: that its
 * session is still open, that the account is still active, what the role allows now, and whether the role requires
 * two-step sign-in that is still off (TwoFactorSetupFilter then holds the request back). A token for an ended
 * session or a suspended account is refused with 401 at once, not when it runs out.
 */
@Component
public class PermissionAuthenticationConverter implements Converter<Jwt, ActorAuthentication> {

    private final SessionChecker sessions;
    private final UserService users;
    private final RoleService roles;
    private final SettingsService settings;

    public PermissionAuthenticationConverter(SessionChecker sessions, UserService users, RoleService roles,
            SettingsService settings) {
        this.sessions = sessions;
        this.users = users;
        this.roles = roles;
        this.settings = settings;
    }

    @Override
    public ActorAuthentication convert(Jwt jwt) {
        var userId = uuid(jwt.getSubject());
        var sessionId = uuid(jwt.getClaimAsString(JwtIssuer.SESSION_CLAIM));
        if (!sessions.isOpen(sessionId, userId)) {
            throw new InvalidBearerTokenException("The session has ended");
        }
        var account = users.findAccount(userId)
                .filter(AccountSummary::active)
                .orElseThrow(() -> new InvalidBearerTokenException("The account is not active"));
        var setupRequired = !account.twoFactor() && settings.requiresTwoFactor(account.roleId());
        var actor = new Actor(userId, sessionId, account.roleId(), roles.permissionsOf(account.roleId()), setupRequired);
        return new ActorAuthentication(actor, jwt);
    }

    private static UUID uuid(@Nullable String value) {
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException e) {
            throw new InvalidBearerTokenException("Malformed token");
        }
    }
}
