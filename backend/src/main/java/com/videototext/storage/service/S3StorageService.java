package com.videototext.storage.service;

import com.videototext.common.exception.FileProcessingException;
import com.videototext.storage.config.StorageProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3StorageService implements StorageService {

    private final StorageProperties properties;
    private S3Client s3Client;

    @PostConstruct
    public void init() {
        try {
            S3Configuration serviceConfiguration = S3Configuration.builder()
                    .pathStyleAccessEnabled(properties.isPathStyleAccess())
                    .build();

            var builder = S3Client.builder()
                    .region(Region.of(properties.getRegion()))
                    .serviceConfiguration(serviceConfiguration)
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())
                    ));

            if (properties.getEndpoint() != null && !properties.getEndpoint().isBlank()) {
                builder.endpointOverride(URI.create(properties.getEndpoint()));
            }

            this.s3Client = builder.build();
            ensureBucketExists();
            log.info("S3StorageService initialized successfully with bucket: {}", properties.getBucket());
        } catch (Exception e) {
            log.warn("S3 client initialization warning: {}. S3 calls may fail if endpoint is unreachable.", e.getMessage());
        }
    }

    private void ensureBucketExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(properties.getBucket()).build());
        } catch (NoSuchBucketException e) {
            log.info("Bucket '{}' does not exist. Creating bucket...", properties.getBucket());
            s3Client.createBucket(CreateBucketRequest.builder().bucket(properties.getBucket()).build());
        } catch (Exception e) {
            log.warn("Could not verify bucket existence on startup: {}", e.getMessage());
        }
    }

    @Override
    public void uploadFile(String key, InputStream inputStream, long contentLength, String contentType) {
        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .contentType(contentType)
                    .contentLength(contentLength)
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromInputStream(inputStream, contentLength));
            log.debug("Successfully uploaded object to S3: {}", key);
        } catch (Exception e) {
            log.error("Failed to upload file to S3 [{}]: {}", key, e.getMessage());
            throw new FileProcessingException("Failed to upload file to object storage: " + e.getMessage(), e);
        }
    }

    @Override
    public String initiateMultipartUpload(String key, String contentType) {
        try {
            CreateMultipartUploadRequest createRequest = CreateMultipartUploadRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .contentType(contentType)
                    .build();

            CreateMultipartUploadResponse response = s3Client.createMultipartUpload(createRequest);
            log.info("Initiated multipart upload for key: {}, uploadId: {}", key, response.uploadId());
            return response.uploadId();
        } catch (Exception e) {
            log.error("Failed to initiate multipart upload for [{}]: {}", key, e.getMessage());
            throw new FileProcessingException("Failed to initiate multipart upload: " + e.getMessage(), e);
        }
    }

    @Override
    public String uploadPart(String key, String uploadId, int partNumber, InputStream inputStream, long partSize) {
        try {
            UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .uploadId(uploadId)
                    .partNumber(partNumber)
                    .contentLength(partSize)
                    .build();

            UploadPartResponse response = s3Client.uploadPart(uploadPartRequest, RequestBody.fromInputStream(inputStream, partSize));
            log.debug("Uploaded part {} for uploadId: {}, ETag: {}", partNumber, uploadId, response.eTag());
            return response.eTag();
        } catch (Exception e) {
            log.error("Failed to upload part {} for [{}]: {}", partNumber, key, e.getMessage());
            throw new FileProcessingException("Failed to upload file part " + partNumber + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void completeMultipartUpload(String key, String uploadId, List<CompletedPartDto> parts) {
        try {
            List<CompletedPart> s3Parts = parts.stream()
                    .map(p -> CompletedPart.builder()
                            .partNumber(p.getPartNumber())
                            .eTag(p.getETag())
                            .build())
                    .collect(Collectors.toList());

            CompletedMultipartUpload completedMultipartUpload = CompletedMultipartUpload.builder()
                    .parts(s3Parts)
                    .build();

            CompleteMultipartUploadRequest completeRequest = CompleteMultipartUploadRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .uploadId(uploadId)
                    .multipartUpload(completedMultipartUpload)
                    .build();

            s3Client.completeMultipartUpload(completeRequest);
            log.info("Completed multipart upload for key: {}", key);
        } catch (Exception e) {
            log.error("Failed to complete multipart upload for [{}]: {}", key, e.getMessage());
            throw new FileProcessingException("Failed to complete multipart upload: " + e.getMessage(), e);
        }
    }

    @Override
    public void abortMultipartUpload(String key, String uploadId) {
        try {
            AbortMultipartUploadRequest abortRequest = AbortMultipartUploadRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .uploadId(uploadId)
                    .build();

            s3Client.abortMultipartUpload(abortRequest);
            log.info("Aborted multipart upload for key: {}, uploadId: {}", key, uploadId);
        } catch (Exception e) {
            log.warn("Failed to abort multipart upload for [{}]: {}", key, e.getMessage());
        }
    }

    @Override
    public InputStream downloadFile(String key) {
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .build();

            return s3Client.getObject(getRequest);
        } catch (NoSuchKeyException e) {
            throw new FileProcessingException("File not found in object storage: " + key, e);
        } catch (Exception e) {
            throw new FileProcessingException("Failed to download file from object storage: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteFile(String key) {
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteRequest);
            log.info("Deleted object from S3: {}", key);
        } catch (Exception e) {
            log.warn("Failed to delete file from S3 [{}]: {}", key, e.getMessage());
        }
    }

    @Override
    public boolean fileExists(String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(properties.getBucket()).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String generatePresignedUrl(String key, Duration duration) {
        // Return public or direct URL path
        return String.format("%s/%s/%s", properties.getEndpoint(), properties.getBucket(), key);
    }
}
