package com.adminstarter.accesscontrol.security;

import com.adminstarter.accesscontrol.AccessControlProperties;
import com.adminstarter.accesscontrol.common.constant.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Refuses a request body larger than {@code access-control.requests} allows with 400 invalid_request, before anything
 * reads it (P-039): at once by its Content-Length, or, sent in chunks without one, as soon as more has come. Jackson
 * reads a body before the access token or the permissions are looked at, so without this anyone could have the server
 * hold a body of any size.
 */
public class RequestSizeFilter extends OncePerRequestFilter {

    /** The one request that may bring more: up to 1,000 people */
    static final String IMPORT = "/api/users/import";

    private final long maxBody;
    private final long maxImportBody;
    private final SecurityProblemHandler problems;

    public RequestSizeFilter(AccessControlProperties properties, SecurityProblemHandler problems) {
        this.maxBody = properties.requests().maxBody().toBytes();
        this.maxImportBody = properties.requests().maxImportBody().toBytes();
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var limit = IMPORT.equals(request.getRequestURI()) ? maxImportBody : maxBody;
        if (request.getContentLengthLong() > limit) {
            problems.write(response, ErrorCode.INVALID_REQUEST);
            return;
        }
        chain.doFilter(new Limited(request, limit), response);
    }

    /** The request, with a body that fails once more than the limit has been read */
    private static final class Limited extends HttpServletRequestWrapper {

        private final long limit;
        private @Nullable ServletInputStream body;

        Limited(HttpServletRequest request, long limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (body == null) {
                body = new Counted(super.getInputStream(), limit);
            }
            return body;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            var encoding = getCharacterEncoding();
            var charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }

    private static final class Counted extends ServletInputStream {

        private final ServletInputStream body;
        private final long limit;
        private long read;

        Counted(ServletInputStream body, long limit) {
            this.body = body;
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            var next = body.read();
            if (next != -1) {
                counted(1);
            }
            return next;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            var count = body.read(buffer, offset, length);
            if (count > 0) {
                counted(count);
            }
            return count;
        }

        private void counted(int count) throws IOException {
            read += count;
            if (read > limit) {
                throw new IOException("Request body over " + limit + " bytes");
            }
        }

        @Override
        public boolean isFinished() {
            return body.isFinished();
        }

        @Override
        public boolean isReady() {
            return body.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            body.setReadListener(listener);
        }
    }
}
