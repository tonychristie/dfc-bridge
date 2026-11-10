package com.spirecentral.dfcbridge.exception;

import com.spirecentral.dfcbridge.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

/**
 * Global exception handler for REST controllers.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DfcBridgeException.class)
    public ResponseEntity<ErrorResponse> handleDfcBridgeException(
            DfcBridgeException ex, HttpServletRequest request) {
        HttpStatus status = ex.getStatus();
        if (status.is5xxServerError()) {
            log.error("{} {}: {}", status.value(), ex.getCode(), ex.getMessage(), ex);
        } else {
            log.warn("{} {}: {}", status.value(), ex.getCode(), ex.getMessage());
        }
        return jsonError(status, ErrorResponse.builder()
                .code(ex.getCode())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));

        return jsonError(HttpStatus.BAD_REQUEST, ErrorResponse.builder()
                .code("VALIDATION_ERROR")
                .message("Validation failed: " + errors)
                .path(request.getRequestURI())
                .build());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        return requestError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Missing request parameter: " + ex.getParameterName(), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return requestError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Invalid value for parameter " + ex.getName() + ": " + ex.getValue(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return requestError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Request body is missing or is not valid JSON", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return requestError(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "Method " + ex.getMethod() + " is not supported for " + request.getRequestURI(), request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        MediaType contentType = ex.getContentType();
        // Null when the Content-Type header cannot be parsed
        String type = contentType != null
                ? contentType.getType() + "/" + contentType.getSubtype()
                : request.getContentType();
        return requestError(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "Content type " + type + " is not supported", request);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotAcceptable(
            HttpMediaTypeNotAcceptableException ex, HttpServletRequest request) {
        return requestError(HttpStatus.NOT_ACCEPTABLE, "NOT_ACCEPTABLE",
                "Accept " + request.getHeader(HttpHeaders.ACCEPT) + " is not supported, use application/json",
                request);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandlerFound(
            NoHandlerFoundException ex, HttpServletRequest request) {
        return requestError(HttpStatus.NOT_FOUND, "NOT_FOUND",
                "No endpoint " + ex.getHttpMethod() + " " + ex.getRequestURL(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return jsonError(HttpStatus.INTERNAL_SERVER_ERROR, ErrorResponse.builder()
                .code("INTERNAL_ERROR")
                .message("An unexpected error occurred")
                .details(ex.getMessage())
                .path(request.getRequestURI())
                .build());
    }

    private ResponseEntity<ErrorResponse> requestError(
            HttpStatus status, String code, String message, HttpServletRequest request) {
        log.warn("{} {}: {}", status.value(), code, message);
        return jsonError(status, ErrorResponse.builder()
                .code(code)
                .message(message)
                .path(request.getRequestURI())
                .build());
    }

    // A preset content type makes Spring skip the Accept check, so a client that refuses JSON still gets the body
    private ResponseEntity<ErrorResponse> jsonError(HttpStatus status, ErrorResponse body) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
