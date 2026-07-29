package com.webhooks.gateway.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
    private static final Pattern TENANT_PATH = Pattern.compile("^/tenants/([^/]+)(?:/.*)?$");

    private final ApiKeyHasher apiKeyHasher;
    private final ApiKeyRepository apiKeyRepository;

    public ApiKeyAuthenticationFilter(ApiKeyHasher apiKeyHasher, ApiKeyRepository apiKeyRepository) {
        this.apiKeyHasher = apiKeyHasher;
        this.apiKeyRepository = apiKeyRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        UUID tenantId = tenantIdFromPath(request.getRequestURI());
        if (tenantId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = request.getHeader("X-API-Key");
        if (apiKey == null || apiKey.isBlank()) {
            reject(response, HttpStatus.UNAUTHORIZED, "Missing X-API-Key header");
            return;
        }

        boolean allowed = apiKeyRepository.findByKeyHashAndRevokedAtIsNull(apiKeyHasher.hash(apiKey.trim()))
                .map(key -> key.getTenantId().equals(tenantId))
                .orElse(false);
        if (!allowed) {
            reject(response, HttpStatus.FORBIDDEN, "API key is not valid for this tenant");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private UUID tenantIdFromPath(String path) {
        Matcher matcher = TENANT_PATH.matcher(path);
        if (!matcher.matches()) {
            return null;
        }
        try {
            return UUID.fromString(matcher.group(1));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void reject(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"api_key_auth_failed\",\"message\":\"" + message + "\"}");
    }
}
