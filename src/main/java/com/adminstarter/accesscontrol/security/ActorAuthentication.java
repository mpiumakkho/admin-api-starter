package com.adminstarter.accesscontrol.security;

import com.adminstarter.accesscontrol.common.context.Actor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** A request with a valid access token: the principal is the Actor, the authorities its permission codes */
public class ActorAuthentication extends AbstractAuthenticationToken {

    private final Actor actor;
    private final Jwt token;

    public ActorAuthentication(Actor actor, Jwt token) {
        super(actor.permissions().stream().map(SimpleGrantedAuthority::new).toList());
        this.actor = actor;
        this.token = token;
        setAuthenticated(true);
    }

    @Override
    public Actor getPrincipal() {
        return actor;
    }

    @Override
    public Jwt getCredentials() {
        return token;
    }

    @Override
    public String getName() {
        return actor.userId().toString();
    }
}
