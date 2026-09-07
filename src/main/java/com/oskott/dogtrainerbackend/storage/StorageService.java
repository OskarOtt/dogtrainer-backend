package com.oskott.dogtrainerbackend.storage;

import com.oskott.dogtrainerbackend.common.exception.InvalidFileException;
import com.oskott.dogtrainerbackend.storage.config.R2Properties;
import com.oskott.dogtrainerbackend.storage.dto.UploadUrlRequest;
import com.oskott.dogtrainerbackend.storage.dto.UploadUrlResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * The only class that talks to the R2 (S3-compatible) client/presigner. Object keys and their
 * extensions are always derived server-side, never trusted from client input.
 */
@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
    private final R2Properties properties;

    public StorageService(S3Presigner s3Presigner, S3Client s3Client, R2Properties properties) {
        this.s3Presigner = s3Presigner;
        this.s3Client = s3Client;
        this.properties = properties;
    }

    public UploadUrlResponse createUploadUrl(String keyPrefix, MediaCategory category, UploadUrlRequest request) {
        if (!category.supportsContentType(request.contentType())) {
            throw new InvalidFileException("Unsupported content type for " + category + ": " + request.contentType());
        }
        if (request.fileSizeBytes() > category.maxSizeBytes()) {
            throw new InvalidFileException(
                    "File exceeds the maximum allowed size of " + category.maxSizeBytes() + " bytes for " + category);
        }

        String extension = category.extensionFor(request.contentType());
        String objectKey = keyPrefix + "/" + UUID.randomUUID() + "." + extension;
        Duration expiry = Duration.ofSeconds(properties.presignExpirySeconds());

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .contentType(request.contentType())
                .contentLength(request.fileSizeBytes())
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(expiry)
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

        return new UploadUrlResponse(presignedRequest.url().toString(), objectKey, Instant.now().plus(expiry));
    }

    public boolean objectExists(String objectKey) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(objectKey)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    public String buildPublicUrl(String objectKey) {
        return normalizedPublicBaseUrl() + "/" + objectKey;
    }

    private String normalizedPublicBaseUrl() {
        String base = properties.publicBaseUrl();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /**
     * Reverses {@link #buildPublicUrl}, so the previous object can be cleaned up on replacement
     * without persisting a separate raw-key column alongside the public URL.
     */
    public String extractObjectKey(String publicUrl) {
        if (publicUrl == null) {
            return null;
        }
        String prefix = normalizedPublicBaseUrl() + "/";
        return publicUrl.startsWith(prefix) ? publicUrl.substring(prefix.length()) : null;
    }

    public void deleteObjectIfPresent(String objectKey) {
        if (objectKey == null) {
            return;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(objectKey)
                    .build());
        } catch (SdkException e) {
            log.warn("Failed to delete R2 object {}: {}", objectKey, e.getMessage());
        }
    }
}
