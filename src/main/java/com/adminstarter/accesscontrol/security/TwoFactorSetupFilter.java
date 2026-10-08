package com.adminstarter.accesscontrol.security;

import com.adminstarter.accesscontrol.common.constant.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Someone whose role requires two-step sign-in and who has not turned it on (Actor.twoFactorSetupRequired) is answered
 * 403 two_factor_mandatory for everything but what turning it on needs (API.md, "Roles that must use two-step
 * sign-in"; P-016). Runs right after the access token was read, before any permission check; requests without an
 * Actor, such as /api/auth/** and those with no token, pass on to the rules after it.
 */
public class TwoFactorSetupFilter extends OncePerRequestFilter {

    /** Seeing who you are, the settings and the roles, and turning two-step sign-in on */
    private static final Set<String> SETUP = Set.of(
            "GET /api/me",
            "GET /api/settings",
            "GET /api/roles",
            "POST /api/me/two-factor",
            "POST /api/me/two-factor/confirm");

    private final SecurityProblemHandler problems;

    public TwoFactorSetupFilter(SecurityProblemHandler problems) {
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof ActorAuthentication authentication
                && authentication.getPrincipal().twoFactorSetupRequired()
                && !SETUP.contains(request.getMethod() + " " + request.getRequestURI())) {
            problems.write(response, ErrorCode.TWO_FACTOR_MANDATORY);
            return;
        }
        chain.doFilter(request, response);
    }
}
