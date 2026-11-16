package com.videototext.common.exception;

public class FileProcessingException extends ApiException {

    public FileProcessingException(String message) {
        super(ErrorCode.PROCESSING_ERROR, message);
    }

    public FileProcessingException(String message, Throwable cause) {
        super(ErrorCode.PROCESSING_ERROR, message, cause);
    }

    public FileProcessingException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
