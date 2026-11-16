package com.videototext.common.exception;

import com.videototext.common.dto.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
        log.warn("API Exception [{}]: {}", ex.getErrorCode().getCode(), ex.getMessage());
        ErrorResponse response = ErrorResponse.of(ex.getErrorCode().getCode(), ex.getMessage());
        return new ResponseEntity<>(response, ex.getHttpStatus());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        List<ErrorResponse.ValidationError> details = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            details.add(ErrorResponse.ValidationError.builder()
                    .field(fieldError.getField())
                    .rejectedValue(fieldError.getRejectedValue())
                    .reason(fieldError.getDefaultMessage())
                    .build());
        }
        log.warn("Validation failed for {} fields", details.size());
        ErrorResponse response = ErrorResponse.of(
                ErrorCode.VALIDATION_ERROR.getCode(),
                "Validation failed for one or more fields",
                details
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        List<ErrorResponse.ValidationError> details = new ArrayList<>();
        ex.getConstraintViolations().forEach(violation -> {
            details.add(ErrorResponse.ValidationError.builder()
                    .field(violation.getPropertyPath().toString())
                    .rejectedValue(violation.getInvalidValue())
                    .reason(violation.getMessage())
                    .build());
        });
        log.warn("Constraint violation: {}", ex.getMessage());
        ErrorResponse response = ErrorResponse.of(
                ErrorCode.VALIDATION_ERROR.getCode(),
                "Request constraint violation",
                details
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        log.warn("Upload size exceeded: {}", ex.getMessage());
        ErrorResponse response = ErrorResponse.of(
                ErrorCode.FILE_TOO_LARGE.getCode(),
                "File upload exceeds the maximum allowed size"
        );
        return new ResponseEntity<>(response, HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        ErrorResponse response = ErrorResponse.of(
                ErrorCode.ACCESS_DENIED.getCode(),
                "You do not have permission to access this resource"
        );
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex) {
        log.warn("Authentication required: {}", ex.getMessage());
        ErrorResponse response = ErrorResponse.of(
                ErrorCode.AUTHENTICATION_REQUIRED.getCode(),
                "Authentication is required to access this resource"
        );
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        ErrorResponse response = ErrorResponse.of(
                ErrorCode.RESOURCE_NOT_FOUND.getCode(),
                "The requested resource endpoint does not exist"
        );
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        ErrorResponse response = ErrorResponse.of(
                ErrorCode.VALIDATION_ERROR.getCode(),
                String.format("HTTP method '%s' is not supported for this endpoint", ex.getMethod())
        );
        return new ResponseEntity<>(response, HttpStatus.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unhandled internal server error: ", ex);
        ErrorResponse response = ErrorResponse.of(
                ErrorCode.PROCESSING_ERROR.getCode(),
                "An unexpected internal error occurred. Please contact system support."
        );
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
