package com.adminstarter.accesscontrol.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.adminstarter.accesscontrol.common.context.Actor;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import tools.jackson.databind.json.JsonMapper;

class TwoFactorSetupFilterTest {

    private final TwoFactorSetupFilter filter = new TwoFactorSetupFilter(new SecurityProblemHandler(JsonMapper.builder().build()));

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void someone_who_must_turn_it_on_gets_only_what_turning_it_on_needs() throws Exception {
        signIn(true);
        for (var allowed : List.of("GET /api/me", "GET /api/settings", "GET /api/roles", "POST /api/me/two-factor",
                "POST /api/me/two-factor/confirm")) {
            assertThat(passes(allowed)).as(allowed).isTrue();
        }
        for (var held : List.of("GET /api/users", "GET /api/activity", "GET /api/events", "POST /api/me/password",
                "POST /api/me/two-factor/disable", "PATCH /api/settings", "GET /api/me/two-factor")) {
            assertThat(passes(held)).as(held).isFalse();
        }
    }

    @Test
    void everyone_else_passes_and_so_does_a_request_without_a_token() throws Exception {
        assertThat(passes("GET /api/users")).isTrue();
        signIn(false);
        assertThat(passes("GET /api/users")).isTrue();
    }

    private static void signIn(boolean twoFactorSetupRequired) {
        var actor = new Actor(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Set.of(), twoFactorSetupRequired);
        var token = Jwt.withTokenValue("token").header("alg", "RS256").subject(actor.userId().toString()).build();
        SecurityContextHolder.getContext().setAuthentication(new ActorAuthentication(actor, token));
    }

    /** Whether the request went on down the chain; held back, it is answered 403 two_factor_mandatory */
    private boolean passes(String request) throws Exception {
        var parts = request.split(" ");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest(parts[0], parts[1]), response, chain);
        if (chain.getRequest() != null) {
            return true;
        }
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("two_factor_mandatory");
        return false;
    }
}
