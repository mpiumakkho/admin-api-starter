package com.adminstarter.accesscontrol.authentication.service;

import static com.adminstarter.accesscontrol.common.constant.ErrorCode.INVALID_TOKEN;

import com.adminstarter.accesscontrol.AccessControlProperties;
import com.adminstarter.accesscontrol.authentication.entity.ActionToken;
import com.adminstarter.accesscontrol.authentication.entity.ActionTokenType;
import com.adminstarter.accesscontrol.authentication.event.InvitationIssued;
import com.adminstarter.accesscontrol.authentication.event.PasswordResetIssued;
import com.adminstarter.accesscontrol.authentication.repository.ActionTokenRepository;
import com.adminstarter.accesscontrol.identity.service.CredentialService;
import com.adminstarter.accesscontrol.identity.service.UserService;
import com.adminstarter.accesscontrol.settings.service.SettingsService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The links in emails: an invitation to set a first password, and a password reset. Each works once and only until
 * it expires; making a new one retires the older ones of the same kind. The email itself goes out after the
 * transaction commits (notification module).
 */
@Service
@Transactional
public class ActionTokenService {

    private final ActionTokenRepository actionTokens;
    private final OpaqueTokenSupport tokens;
    private final UserService users;
    private final CredentialService credentials;
    private final SettingsService settings;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Duration resetLinkTtl;

    public ActionTokenService(ActionTokenRepository actionTokens, OpaqueTokenSupport tokens, UserService users,
            CredentialService credentials, SettingsService settings, ApplicationEventPublisher events, Clock clock,
            AccessControlProperties properties) {
        this.actionTokens = actionTokens;
        this.tokens = tokens;
        this.users = users;
        this.credentials = credentials;
        this.settings = settings;
        this.events = events;
        this.clock = clock;
        this.resetLinkTtl = properties.mail().resetLinkTtl();
    }

    /** Valid for the settings' inviteExpiryDays */
    public void issueInvitation(UUID userId, String email, String firstName) {
        var expiresAt = now().plus(Duration.ofDays(settings.current().inviteExpiryDays()));
        var token = issue(userId, ActionTokenType.INVITE, expiresAt);
        events.publishEvent(new InvitationIssued(userId, email, firstName, token, expiresAt));
    }

    public void issuePasswordReset(UUID userId, String email, String firstName) {
        var expiresAt = now().plus(resetLinkTtl);
        var token = issue(userId, ActionTokenType.PASSWORD_RESET, expiresAt);
        events.publishEvent(new PasswordResetIssued(userId, email, firstName, token, expiresAt));
    }

    /**
     * "Forgot password?": always answered the same way, so it reveals nothing. Someone active gets a reset link;
     * someone invited gets their invitation again; a suspended or unknown email gets nothing.
     */
    public void requestPasswordReset(String email) {
        users.findAccountByEmail(email).ifPresent(account -> {
            switch (account.status()) {
                case "active" -> issuePasswordReset(account.id(), account.email(), account.firstName());
                case "invited" -> issueInvitation(account.id(), account.email(), account.firstName());
                default -> {}
            }
        });
    }

    /** Sets the first password and makes the account active */
    public void acceptInvitation(String token, String password) {
        var userId = use(token, ActionTokenType.INVITE);
        credentials.setPassword(userId, password);
        users.acceptInvitation(userId);
    }

    /** Sets the new password; every session of the user ends */
    public void confirmPasswordReset(String token, String password) {
        credentials.setPassword(use(token, ActionTokenType.PASSWORD_RESET), password);
    }

    private String issue(UUID userId, ActionTokenType type, Instant expiresAt) {
        var now = now();
        actionTokens.findByUserIdAndTypeAndUsedAtIsNull(userId, type).forEach(older -> older.use(now));
        var value = tokens.newToken();
        actionTokens.save(new ActionToken(userId, type, tokens.hash(value), now, expiresAt));
        return value;
    }

    /** The user the link is for; invalid_token when it is unknown, used, expired or of another kind */
    private UUID use(String token, ActionTokenType type) {
        var now = now();
        var actionToken = actionTokens.findByTokenHash(tokens.hash(token))
                .filter(candidate -> candidate.getType() == type && candidate.usableAt(now))
                .orElseThrow(INVALID_TOKEN::exception);
        actionToken.use(now);
        return actionToken.getUserId();
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
