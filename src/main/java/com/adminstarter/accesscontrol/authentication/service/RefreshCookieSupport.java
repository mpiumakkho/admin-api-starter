package com.adminstarter.accesscontrol.authentication.service;

import static com.adminstarter.accesscontrol.common.constant.ErrorCode.FORBIDDEN;

import com.adminstarter.accesscontrol.AccessControlProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * The refresh cookie: HttpOnly so scripts never see it, SameSite=Strict so other sites cannot send it, Secure in
 * production, and only sent to /api/auth. Requests that use it must also come from the dashboard's own origin.
 */
@Component
public class RefreshCookieSupport {

    private final AccessControlProperties.Cookie cookie;
    private final List<String> allowedOrigins;

    public RefreshCookieSupport(AccessControlProperties properties) {
        this.cookie = properties.cookie();
        this.allowedOrigins = properties.cors().allowedOrigins();
    }

    /** @param maxAge null for a cookie that ends with the browser */
    public ResponseCookie issue(String token, @Nullable Duration maxAge) {
        var builder = base(token);
        if (maxAge != null) {
            builder.maxAge(maxAge);
        }
        return builder.build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    public Optional<String> read(HttpServletRequest request) {
        return Arrays.stream(Optional.ofNullable(request.getCookies()).orElse(new Cookie[0]))
                .filter(candidate -> candidate.getName().equals(cookie.name()))
                .map(Cookie::getValue)
                .filter(value -> !value.isBlank())
                .findFirst();
    }

    /**
     * Refuses a request whose Origin header names another site. SameSite=Strict already keeps the cookie from
     * cross-site requests; this covers browsers and proxies that get that wrong.
     */
    public void checkOrigin(HttpServletRequest request) {
        var origin = request.getHeader("Origin");
        if (origin == null || allowedOrigins.contains(origin) || origin.equals(ownOrigin(request))) {
            return;
        }
        throw FORBIDDEN.exception();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(cookie.name(), value)
                .httpOnly(true)
                .secure(cookie.secure())
                .sameSite("Strict")
                .path(cookie.path());
    }

    private static String ownOrigin(HttpServletRequest request) {
        var scheme = request.getScheme();
        var port = request.getServerPort();
        var defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        return scheme + "://" + request.getServerName() + (defaultPort ? "" : ":" + port);
    }
}
