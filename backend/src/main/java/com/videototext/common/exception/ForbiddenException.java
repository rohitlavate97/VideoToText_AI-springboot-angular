package com.videototext.common.exception;

public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super(ErrorCode.ACCESS_DENIED, message);
    }

    public ForbiddenException() {
        super(ErrorCode.ACCESS_DENIED);
    }
}
