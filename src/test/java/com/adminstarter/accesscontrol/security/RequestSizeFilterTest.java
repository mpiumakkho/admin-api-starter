package com.adminstarter.accesscontrol.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adminstarter.accesscontrol.AccessControlProperties;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/** A body sent in chunks, with no Content-Length to refuse it by, fails once it has brought too much (P-039) */
class RequestSizeFilterTest {

    private final AccessControlProperties properties = new Binder(new MapConfigurationPropertySource(
            java.util.Map.of("access-control.requests.max-body", "1KB")))
            .bindOrCreate("access-control", Bindable.of(AccessControlProperties.class));
    private final RequestSizeFilter filter = new RequestSizeFilter(properties, new SecurityProblemHandler(JsonMapper.builder().build()));

    @Test
    void a_body_without_its_length_fails_once_it_brings_more_than_the_limit() throws Exception {
        var request = chunked(new byte[1025]);
        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), (in, out) -> in.getInputStream().readAllBytes()))
                .isInstanceOf(IOException.class);
    }

    @Test
    void a_body_within_the_limit_is_read_whole() throws Exception {
        var request = chunked(new byte[1024]);
        var read = new int[1];
        filter.doFilter(request, new MockHttpServletResponse(), (in, out) -> read[0] = in.getInputStream().readAllBytes().length);
        assertThat(read[0]).isEqualTo(1024);
    }

    @Test
    void a_body_whose_length_is_over_the_limit_is_refused_unread() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/users");
        request.setContent(new byte[2048]);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (in, out) -> {
            throw new AssertionError("read");
        });
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("invalid_request");
    }

    /** As a body sent in chunks arrives: no Content-Length */
    private static MockHttpServletRequest chunked(byte[] body) {
        var request = new MockHttpServletRequest("POST", "/api/users") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }

            @Override
            public int getContentLength() {
                return -1;
            }
        };
        request.setContent(body);
        return request;
    }
}
