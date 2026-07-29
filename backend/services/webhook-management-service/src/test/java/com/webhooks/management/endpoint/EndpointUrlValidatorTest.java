package com.webhooks.management.endpoint;

import com.webhooks.management.common.InvalidRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EndpointUrlValidatorTest {
    private final EndpointUrlValidator validator = new EndpointUrlValidator();

    @Test
    void rejectsLocalhost() {
        assertThrows(InvalidRequestException.class, () -> validator.validate("https://localhost/webhook"));
    }

    @Test
    void rejectsLoopbackAddress() {
        assertThrows(InvalidRequestException.class, () -> validator.validate("https://127.0.0.1/webhook"));
    }

    @Test
    void rejectsPrivateAddress() {
        assertThrows(InvalidRequestException.class, () -> validator.validate("https://10.0.0.5/webhook"));
    }

    @Test
    void rejectsNonHttpsUrl() {
        assertThrows(InvalidRequestException.class, () -> validator.validate("http://example.com/webhook"));
    }

    @Test
    void acceptsPublicHttpsHost() {
        assertDoesNotThrow(() -> validator.validate("https://example.com/webhook"));
    }
}
