package com.videototext.storage.service;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;

public interface StorageService {

    void uploadFile(String key, InputStream inputStream, long contentLength, String contentType);

    String initiateMultipartUpload(String key, String contentType);

    String uploadPart(String key, String uploadId, int partNumber, InputStream inputStream, long partSize);

    void completeMultipartUpload(String key, String uploadId, List<CompletedPartDto> parts);

    void abortMultipartUpload(String key, String uploadId);

    InputStream downloadFile(String key);

    void deleteFile(String key);

    boolean fileExists(String key);

    String generatePresignedUrl(String key, Duration duration);
}
