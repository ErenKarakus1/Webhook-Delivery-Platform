package com.webhooks.gateway.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.webhooks.gateway.ratelimit.RateLimitResult;
import com.webhooks.gateway.ratelimit.RateLimiter;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiKeyAuthenticationFilterTest {
    private final ApiKeyHasher apiKeyHasher = new ApiKeyHasher();
    private final ApiKeyRepository apiKeyRepository = Mockito.mock(ApiKeyRepository.class);
    private final RateLimiter rateLimiter = Mockito.mock(RateLimiter.class);
    private final ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(
            new AdminAuthProperties("admin-secret"),
            apiKeyHasher,
            apiKeyRepository,
            rateLimiter
    );

    @BeforeEach
    void setUp() {
        Mockito.reset(apiKeyRepository, rateLimiter);
    }

    @Test
    void allowsAdminTenantCreationWithAdminKey() throws Exception {
        MockHttpServletRequest request = request("POST", "/tenants");
        request.addHeader("X-Admin-Key", "admin-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
        verify(apiKeyRepository, never()).findByKeyHashAndRevokedAtIsNull(Mockito.anyString());
        verify(rateLimiter, never()).check(Mockito.any());
    }

    @Test
    void rejectsAdminRouteWithoutAdminKey() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("POST", "/tenants"), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Missing or invalid X-Admin-Key header");
    }

    @Test
    void allowsTenantRouteWithMatchingApiKeyAndRateLimit() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String apiKey = "tenant-key";
        when(apiKeyRepository.findByKeyHashAndRevokedAtIsNull(apiKeyHasher.hash(apiKey))).thenReturn(Optional.of(apiKey(tenantId)));
        when(rateLimiter.check(tenantId)).thenReturn(new RateLimitResult(true, 60, 59));
        MockHttpServletRequest request = request("GET", "/tenants/" + tenantId + "/events");
        request.addHeader("X-API-Key", apiKey);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("60");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("59");
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void rejectsTenantRouteWithoutApiKey() throws Exception {
        UUID tenantId = UUID.randomUUID();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("GET", "/tenants/" + tenantId + "/events"), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Missing X-API-Key header");
        verify(rateLimiter, never()).check(Mockito.any());
    }

    @Test
    void rejectsApiKeyForDifferentTenant() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String apiKey = "tenant-key";
        when(apiKeyRepository.findByKeyHashAndRevokedAtIsNull(apiKeyHasher.hash(apiKey))).thenReturn(Optional.of(apiKey(UUID.randomUUID())));
        MockHttpServletRequest request = request("GET", "/tenants/" + tenantId + "/events");
        request.addHeader("X-API-Key", apiKey);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("API key is not valid for this tenant");
        verify(rateLimiter, never()).check(Mockito.any());
    }

    @Test
    void rejectsWhenRateLimitIsExceeded() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String apiKey = "tenant-key";
        when(apiKeyRepository.findByKeyHashAndRevokedAtIsNull(apiKeyHasher.hash(apiKey))).thenReturn(Optional.of(apiKey(tenantId)));
        when(rateLimiter.check(tenantId)).thenReturn(new RateLimitResult(false, 60, 0));
        MockHttpServletRequest request = request("GET", "/tenants/" + tenantId + "/events");
        request.addHeader("X-API-Key", apiKey);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("60");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
        assertThat(response.getContentAsString()).contains("Rate limit exceeded");
    }

    private MockHttpServletRequest request(String method, String uri) {
        return new MockHttpServletRequest(method, uri);
    }

    private ApiKey apiKey(UUID tenantId) throws Exception {
        ApiKey apiKey = new ApiKey();
        Field tenantIdField = ApiKey.class.getDeclaredField("tenantId");
        tenantIdField.setAccessible(true);
        tenantIdField.set(apiKey, tenantId);
        return apiKey;
    }
}
