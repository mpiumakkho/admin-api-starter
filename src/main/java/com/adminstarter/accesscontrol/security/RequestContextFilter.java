package com.adminstarter.accesscontrol.security;

import com.adminstarter.accesscontrol.common.context.Device;
import com.adminstarter.accesscontrol.common.context.RequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Binds the RequestContext (request id, IP address, browser, OS) for the rest of the request, first in the security
 * chain, and puts the request id in the log lines and the X-Request-Id answer header. Behind a proxy, set
 * server.forward-headers-strategy so the IP address is the client's, as told only by the proxies in AC_TRUSTED_PROXIES
 * (P-040).
 */
public class RequestContextFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var given = request.getHeader(HEADER);
        var requestId = given != null && SAFE_ID.matcher(given).matches() ? given : UUID.randomUUID().toString();
        var device = Device.of(request.getHeader("User-Agent"));
        var context = new RequestContext(requestId, request.getRemoteAddr(), device.browser(), device.os());
        response.setHeader(HEADER, requestId);
        MDC.put("request_id", requestId);
        try {
            context.run(() -> {
                chain.doFilter(request, response);
                return null;
            });
        } catch (IOException | ServletException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new ServletException(e);
        } finally {
            MDC.remove("request_id");
        }
    }
}
