package com.webhooks.management.observability;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {
    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void preservesIncomingRequestId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE)).isEqualTo("request-123");
        assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).isEqualTo("request-123");
        assertThat(MDC.get(RequestIdFilter.REQUEST_ID_ATTRIBUTE)).isNull();
    }

    @Test
    void generatesRequestIdWhenMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE)).isNotNull();
        assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER))
                .isEqualTo(request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE));
        assertThat(MDC.get(RequestIdFilter.REQUEST_ID_ATTRIBUTE)).isNull();
    }
}
