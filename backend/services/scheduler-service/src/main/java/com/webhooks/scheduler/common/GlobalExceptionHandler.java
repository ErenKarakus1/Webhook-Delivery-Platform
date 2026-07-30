package com.webhooks.scheduler.common;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException exception) {
        String code = exception.getStatusCode().value() == 404 ? "resource_not_found" : "request_failed";
        return ResponseEntity.status(exception.getStatusCode())
                .body(ApiError.of(code, exception.getReason()));
    }
}
