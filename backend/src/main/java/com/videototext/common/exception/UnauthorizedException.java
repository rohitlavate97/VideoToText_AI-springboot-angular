package com.videototext.common.exception;

public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String message) {
        super(ErrorCode.AUTHENTICATION_REQUIRED, message);
    }

    public UnauthorizedException() {
        super(ErrorCode.AUTHENTICATION_REQUIRED);
    }
}
