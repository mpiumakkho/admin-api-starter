package com.adminstarter.accesscontrol.authentication.service;

import static com.adminstarter.accesscontrol.common.constant.ErrorCode.ACCOUNT_SUSPENDED;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.INVALID_CODE;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.INVALID_CREDENTIALS;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.INVITE_PENDING;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.TWO_FACTOR_REQUIRED;
import static com.adminstarter.accesscontrol.common.constant.ErrorCode.UNAUTHENTICATED;

import com.adminstarter.accesscontrol.AccessControlProperties;
import com.adminstarter.accesscontrol.authentication.dto.LoginRequest;
import com.adminstarter.accesscontrol.authentication.dto.ReauthenticateRequest;
import com.adminstarter.accesscontrol.authentication.dto.TokenResponse;
import com.adminstarter.accesscontrol.authentication.entity.RefreshToken;
import com.adminstarter.accesscontrol.authentication.entity.UserSession;
import com.adminstarter.accesscontrol.authentication.event.SignInFailed;
import com.adminstarter.accesscontrol.authentication.event.SignedIn;
import com.adminstarter.accesscontrol.authentication.repository.RefreshTokenRepository;
import com.adminstarter.accesscontrol.authentication.repository.UserSessionRepository;
import com.adminstarter.accesscontrol.common.context.Actor;
import com.adminstarter.accesscontrol.common.context.RequestContext;
import com.adminstarter.accesscontrol.common.exception.AccessControlException;
import com.adminstarter.accesscontrol.identity.dto.AccountSummary;
import com.adminstarter.accesscontrol.identity.service.CredentialService;
import com.adminstarter.accesscontrol.identity.service.UserService;
import com.adminstarter.accesscontrol.security.jwt.JwtIssuer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sign-in, refresh, sign-out, and confirming the password again (P-023). Refusals still commit what they changed (a
 * wrong password counts, a reused refresh token ends its session), hence noRollbackFor.
 */
@Service
@Transactional(noRollbackFor = AccessControlException.class)
public class LoginService {

    private static final Logger log = LoggerFactory.getLogger(LoginService.class);

    private final UserService users;
    private final CredentialService credentials;
    private final UserSessionRepository sessions;
    private final RefreshTokenRepository refreshTokens;
    private final OpaqueTokenSupport tokens;
    private final JwtIssuer jwt;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final AccessControlProperties.Token ttl;

    public LoginService(UserService users, CredentialService credentials, UserSessionRepository sessions,
            RefreshTokenRepository refreshTokens, OpaqueTokenSupport tokens, JwtIssuer jwt,
            ApplicationEventPublisher events, Clock clock, AccessControlProperties properties) {
        this.users = users;
        this.credentials = credentials;
        this.sessions = sessions;
        this.refreshTokens = refreshTokens;
        this.tokens = tokens;
        this.jwt = jwt;
        this.events = events;
        this.clock = clock;
        this.ttl = properties.token();
    }

    /**
     * What a sign-in or refresh answers: the body, and the refresh cookie to set.
     *
     * @param refreshToken null when the browser should keep the cookie it has
     * @param cookieMaxAge null for a cookie that ends with the browser
     */
    public record SignIn(TokenResponse body, @Nullable String refreshToken, @Nullable Duration cookieMaxAge) {}

    public SignIn login(LoginRequest request) {
        var account = users.findAccountByEmail(request.email()).orElse(null);
        if (account == null) {
            credentials.checkNoPassword(request.password());
            throw INVALID_CREDENTIALS.exception();
        }
        var locked = credentials.lockedNow(account.id());
        if (!credentials.checkPassword(account.id(), request.password())) {
            failed(account, locked ? "locked" : "password");
            throw INVALID_CREDENTIALS.exception();
        }
        if (!account.active()) {
            var suspended = "suspended".equals(account.status());
            failed(account, suspended ? "suspended" : "invited");
            throw (suspended ? ACCOUNT_SUSPENDED : INVITE_PENDING).exception();
        }
        checkCode(account, request.code());
        credentials.signInSucceeded(account.id());
        users.recordSignIn(account.id());

        var now = clock.instant();
        var context = RequestContext.currentOrNone();
        var session = sessions.save(new UserSession(account.id(), request.remember(), context.ip(), context.browser(),
                context.os(), now, now.plus(lifetime(request.remember()))));
        var refreshToken = newRefreshToken(session, now);
        events.publishEvent(new SignedIn(account.id(), account.fullName()));
        return answer(account, session, refreshToken);
    }

    public SignIn refresh(@Nullable String presented) {
        var now = clock.instant();
        var token = presented == null ? null : refreshTokens.findByTokenHash(tokens.hash(presented)).orElse(null);
        var session = token == null ? null : sessions.findById(token.getSessionId()).orElse(null);
        if (token == null || session == null || token.expiredAt(now) || !session.openAt(now)) {
            throw UNAUTHENTICATED.exception();
        }
        var account = users.findAccount(session.getUserId()).filter(AccountSummary::active)
                .orElseThrow(UNAUTHENTICATED::exception);
        var replacedAt = token.getReplacedAt();
        if (replacedAt != null) {
            if (now.isBefore(replacedAt.plus(ttl.reuseGrace()))) {
                // Two tabs refreshing with the same cookie at once: answer, and leave the newer cookie in place
                return answer(account, session, null);
            }
            log.warn("A replaced refresh token was used again; ending session {} of user {}", session.getId(), account.id());
            session.end(now);
            throw UNAUTHENTICATED.exception();
        }
        token.replace(now);
        session.extend(now.plus(lifetime(session.isRemember())), now);
        return answer(account, session, newRefreshToken(session, now));
    }

    /**
     * The signed-in person proves who they are again, for the risky changes (P-023): their password, and the code with
     * two-step sign-in on. Wrong answers count towards the lockout, as at sign-in. Notes the time on the session of the
     * access token.
     */
    public void reauthenticate(Actor actor, ReauthenticateRequest request) {
        var account = users.findAccount(actor.userId()).filter(AccountSummary::active).orElseThrow(UNAUTHENTICATED::exception);
        var locked = credentials.lockedNow(account.id());
        if (!credentials.checkPassword(account.id(), request.password())) {
            failed(account, locked ? "locked" : "password");
            throw INVALID_CREDENTIALS.exception();
        }
        checkCode(account, request.code());
        credentials.signInSucceeded(account.id());
        sessions.findById(actor.sessionId()).orElseThrow(UNAUTHENTICATED::exception).verified(clock.instant());
    }

    /** Ends the cookie's session, if there is one */
    public void logout(@Nullable String presented) {
        if (presented == null) {
            return;
        }
        refreshTokens.findByTokenHash(tokens.hash(presented))
                .flatMap(token -> sessions.findById(token.getSessionId()))
                .ifPresent(session -> session.end(clock.instant()));
    }

    /**
     * With two-step sign-in on: two_factor_required without a code, invalid_code for a wrong one, which counts and is
     * logged
     */
    private void checkCode(AccountSummary account, @Nullable String code) {
        if (!account.twoFactor()) {
            return;
        }
        if (code == null || code.isBlank()) {
            throw TWO_FACTOR_REQUIRED.exception();
        }
        if (!credentials.checkTwoFactorCode(account.id(), code)) {
            failed(account, "code");
            throw INVALID_CODE.exception();
        }
    }

    /**
     * A refused sign-in, or password confirmation, of an account that exists, for the activity log (P-026): password,
     * code, locked, suspended or invited. The refusal still commits it (noRollbackFor).
     */
    private void failed(AccountSummary account, String cause) {
        events.publishEvent(new SignInFailed(account.id(), account.fullName(), cause));
    }

    private String newRefreshToken(UserSession session, Instant now) {
        var value = tokens.newToken();
        var expiresAt = now.plus(lifetime(session.isRemember()));
        refreshTokens.save(new RefreshToken(session.getId(), tokens.hash(value), now, expiresAt));
        return value;
    }

    private SignIn answer(AccountSummary account, UserSession session, @Nullable String refreshToken) {
        var access = jwt.issue(account.id(), session.getId());
        var body = new TokenResponse(access.value(), access.expiresIn(), users.get(account.id()));
        return new SignIn(body, refreshToken, session.isRemember() ? ttl.rememberTtl() : null);
    }

    private Duration lifetime(boolean remember) {
        return remember ? ttl.rememberTtl() : ttl.refreshTtl();
    }

}
