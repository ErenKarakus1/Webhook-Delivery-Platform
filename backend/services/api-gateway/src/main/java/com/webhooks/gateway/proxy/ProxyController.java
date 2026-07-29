package com.webhooks.gateway.proxy;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Collections;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import org.springframework.web.reactive.function.client.WebClient;

@RestController
public class ProxyController {
    private final GatewayRoutesProperties routes;
    private final WebClient webClient;

    public ProxyController(GatewayRoutesProperties routes, WebClient.Builder webClientBuilder) {
        this.routes = routes;
        this.webClient = webClientBuilder.build();
    }

    @RequestMapping({
            "/tenants",
            "/tenants/**"
    })
    ResponseEntity<byte[]> proxyTenantApis(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body
    ) {
        String path = request.getRequestURI();
        if (path.matches("^/tenants/[^/]+/events.*$")) {
            return proxy(routes.eventIngestion().baseUrl(), request, body);
        }
        if (path.matches("^/tenants/[^/]+/attempts.*$")) {
            return proxy(routes.delivery().baseUrl(), request, body);
        }
        if (path.matches("^/tenants/[^/]+/retries.*$")) {
            return proxy(routes.scheduler().baseUrl(), request, body);
        }
        return proxy(routes.webhookManagement().baseUrl(), request, body);
    }

    private ResponseEntity<byte[]> proxy(String baseUrl, HttpServletRequest request, byte[] body) {
        URI uri = URI.create(baseUrl + request.getRequestURI() + queryString(request));
        HttpMethod method = HttpMethod.valueOf(request.getMethod());

        RequestBodySpec requestSpec = webClient.method(method)
                .uri(uri)
                .headers(headers -> copyHeaders(request, headers));

        RequestHeadersSpec<?> headersSpec = body == null ? requestSpec : requestSpec.bodyValue(body);
        return headersSpec
                .exchangeToMono(response -> response.toEntity(byte[].class))
                .block();
    }

    private void copyHeaders(HttpServletRequest request, HttpHeaders headers) {
        Collections.list(request.getHeaderNames()).stream()
                .filter(headerName -> !headerName.equalsIgnoreCase(HttpHeaders.HOST))
                .forEach(headerName -> headers.put(headerName, Collections.list(request.getHeaders(headerName))));
    }

    private String queryString(HttpServletRequest request) {
        String query = request.getQueryString();
        return query == null || query.isBlank() ? "" : "?" + query;
    }
}
