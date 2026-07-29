package com.webhooks.management.apikey;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class ApiKeyGenerator {
    private static final int KEY_BYTES = 32;
    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        byte[] bytes = new byte[KEY_BYTES];
        secureRandom.nextBytes(bytes);
        return "wdp_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
