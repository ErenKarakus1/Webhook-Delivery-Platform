package com.webhooks.delivery.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class RequestIdFilterTest {
    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void preservesIncomingRequestId() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/health")
                .header(RequestIdFilter.REQUEST_ID_HEADER, "request-123"));

        filter.filter(exchange, currentExchange -> Mono.empty()).block();

        String requestId = exchange.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        assertThat(requestId).isEqualTo("request-123");
        assertThat(exchange.getResponse().getHeaders().getFirst(RequestIdFilter.REQUEST_ID_HEADER)).isEqualTo("request-123");
        assertThat(MDC.get(RequestIdFilter.REQUEST_ID_ATTRIBUTE)).isNull();
    }

    @Test
    void generatesRequestIdWhenMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/health"));

        filter.filter(exchange, currentExchange -> Mono.empty()).block();

        String requestId = exchange.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        assertThat(requestId).isNotNull();
        assertThat(exchange.getResponse().getHeaders().getFirst(RequestIdFilter.REQUEST_ID_HEADER))
                .isEqualTo(requestId);
        assertThat(MDC.get(RequestIdFilter.REQUEST_ID_ATTRIBUTE)).isNull();
    }
}
