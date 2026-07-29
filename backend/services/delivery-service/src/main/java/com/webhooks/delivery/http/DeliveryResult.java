package com.webhooks.delivery.http;

public record DeliveryResult(
        Integer statusCode,
        String responseBody,
        String errorMessage
) {
    public static DeliveryResult http(int statusCode, String responseBody) {
        return new DeliveryResult(statusCode, responseBody, null);
    }

    public static DeliveryResult failure(String errorMessage) {
        return new DeliveryResult(null, null, errorMessage);
    }

    public boolean successful() {
        return statusCode != null && statusCode >= 200 && statusCode < 300;
    }
}
