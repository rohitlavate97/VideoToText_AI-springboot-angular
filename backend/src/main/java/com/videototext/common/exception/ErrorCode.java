package com.videototext.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    VALIDATION_ERROR("VALIDATION_ERROR", HttpStatus.BAD_REQUEST, "Validation failed for request parameters"),
    AUTHENTICATION_REQUIRED("AUTHENTICATION_REQUIRED", HttpStatus.UNAUTHORIZED, "Authentication is required to access this resource"),
    ACCESS_DENIED("ACCESS_DENIED", HttpStatus.FORBIDDEN, "You do not have permission to access this resource"),
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, "The requested resource was not found"),
    DUPLICATE_RESOURCE("DUPLICATE_RESOURCE", HttpStatus.CONFLICT, "A resource with these details already exists"),
    FILE_TOO_LARGE("FILE_TOO_LARGE", HttpStatus.PAYLOAD_TOO_LARGE, "Uploaded file exceeds maximum allowed size"),
    UNSUPPORTED_FORMAT("UNSUPPORTED_FORMAT", HttpStatus.UNSUPPORTED_MEDIA_TYPE, "The provided media format is not supported"),
    DURATION_EXCEEDED("DURATION_EXCEEDED", HttpStatus.UNPROCESSABLE_ENTITY, "Video duration exceeds maximum allowed duration (240 minutes)"),
    QUOTA_EXCEEDED("QUOTA_EXCEEDED", HttpStatus.BAD_REQUEST, "Storage or concurrent job quota exceeded"),
    RATE_LIMIT_EXCEEDED("RATE_LIMIT_EXCEEDED", HttpStatus.TOO_MANY_REQUESTS, "API rate limit exceeded. Please retry later"),
    PROCESSING_ERROR("PROCESSING_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, "An error occurred while processing your request"),
    SERVICE_UNAVAILABLE("SERVICE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE, "Downstream service is temporarily unavailable");

    private final String code;
    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(String code, HttpStatus httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }
}
