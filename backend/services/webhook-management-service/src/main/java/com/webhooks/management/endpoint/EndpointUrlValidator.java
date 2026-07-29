package com.webhooks.management.endpoint;

import com.webhooks.management.common.InvalidRequestException;
import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import org.springframework.stereotype.Component;

@Component
public class EndpointUrlValidator {
    public void validate(String value) {
        URI uri = parse(value);
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new InvalidRequestException("Webhook endpoint URL must use HTTPS");
        }
        if (uri.getUserInfo() != null) {
            throw new InvalidRequestException("Webhook endpoint URL must not include user info");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new InvalidRequestException("Webhook endpoint URL must include a host");
        }

        String host = IDN.toASCII(uri.getHost()).toLowerCase();
        if (host.equals("localhost") || host.endsWith(".localhost")) {
            throw new InvalidRequestException("Webhook endpoint URL must not target localhost");
        }

        InetAddress address = resolve(host);
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            throw new InvalidRequestException("Webhook endpoint URL must not target private or local networks");
        }
    }

    private URI parse(String value) {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("Webhook endpoint URL is invalid");
        }
    }

    private InetAddress resolve(String host) {
        try {
            return InetAddress.getByName(host);
        } catch (Exception exception) {
            throw new InvalidRequestException("Webhook endpoint host could not be resolved");
        }
    }
}
