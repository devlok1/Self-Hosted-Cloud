package com.homecloud.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException e) {
        HttpStatus status = e.getMessage() != null && e.getMessage().contains("not found")
                ? HttpStatus.NOT_FOUND
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .body(Map.of("error", e.getMessage() == null ? "Request failed" : e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage() == null ? "Internal server error" : e.getMessage()));
    }
}
