package com.webhooks.delivery.observability;

import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter implements WebFilter {
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_ATTRIBUTE = "requestId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = requestId(exchange.getRequest());
        exchange.getAttributes().put(REQUEST_ID_ATTRIBUTE, requestId);
        exchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);

        return chain.filter(exchange)
                .doFirst(() -> MDC.put(REQUEST_ID_ATTRIBUTE, requestId))
                .doFinally(signalType -> MDC.remove(REQUEST_ID_ATTRIBUTE));
    }

    private String requestId(ServerHttpRequest request) {
        String incomingRequestId = request.getHeaders().getFirst(REQUEST_ID_HEADER);
        if (incomingRequestId != null && !incomingRequestId.isBlank()) {
            return incomingRequestId.trim();
        }
        return UUID.randomUUID().toString();
    }
}
