package com.adminstarter.accesscontrol.security;

import com.adminstarter.accesscontrol.AccessControlProperties;
import com.adminstarter.accesscontrol.common.constant.ErrorCode;
import com.adminstarter.accesscontrol.common.context.RequestContext;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limits the requests that guess at secrets (passwords, refresh cookies, email links) to so many a minute from one
 * IP address, answering 429 too_many_requests beyond that; confirming the password again (P-023) counts as signing in. Wrong passwords for one account are limited separately,
 * by the lockout. Buckets live in memory: each server instance counts on its own.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Integer> perMinute;
    private final SecurityProblemHandler problems;
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumSize(100_000)
            .build();

    public RateLimitFilter(AccessControlProperties properties, SecurityProblemHandler problems) {
        var limits = properties.rateLimit();
        this.perMinute = Map.of(
                "/api/auth/login", limits.login(),
                "/api/me/reauthenticate", limits.login(),
                "/api/auth/refresh", limits.refresh(),
                "/api/auth/activate", limits.emailLinks(),
                "/api/auth/password-reset/request", limits.emailLinks(),
                "/api/auth/password-reset/confirm", limits.emailLinks());
        this.problems = problems;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !perMinute.containsKey(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var path = request.getRequestURI();
        var ip = RequestContext.current().map(RequestContext::ip).orElse(request.getRemoteAddr());
        var bucket = buckets.get(path + " " + ip, key -> newBucket(perMinute.get(path)));
        if (!bucket.tryConsume(1)) {
            problems.write(response, ErrorCode.TOO_MANY_REQUESTS);
            return;
        }
        chain.doFilter(request, response);
    }

    private static Bucket newBucket(int perMinute) {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(perMinute).refillGreedy(perMinute, Duration.ofMinutes(1)))
                .build();
    }
}
