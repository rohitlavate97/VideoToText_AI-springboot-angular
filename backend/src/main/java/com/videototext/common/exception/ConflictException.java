package com.videototext.common.exception;

public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(ErrorCode.DUPLICATE_RESOURCE, message);
    }
}
