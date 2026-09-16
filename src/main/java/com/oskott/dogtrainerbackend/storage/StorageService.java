package com.oskott.dogtrainerbackend.storage;

import com.oskott.dogtrainerbackend.common.exception.InvalidFileException;
import com.oskott.dogtrainerbackend.storage.config.R2Properties;
import com.oskott.dogtrainerbackend.storage.dto.UploadUrlRequest;
import com.oskott.dogtrainerbackend.storage.dto.UploadUrlResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
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
    private static final String JPEG_CONTENT_TYPE = "image/jpeg";

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
    private final R2Properties properties;
    private final ImageProcessingService imageProcessingService;

    public StorageService(
            S3Presigner s3Presigner,
            S3Client s3Client,
            R2Properties properties,
            ImageProcessingService imageProcessingService) {
        this.s3Presigner = s3Presigner;
        this.s3Client = s3Client;
        this.properties = properties;
        this.imageProcessingService = imageProcessingService;
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

    /**
     * Downloads a client-uploaded image, downscales it to fit within {@code maxDimension} x
     * {@code maxDimension} (keeping aspect ratio, never upscaling), and re-uploads it as a JPEG,
     * replacing the original object. Returns the resulting object key (always {@code .jpg}).
     *
     * <p>If resizing fails (e.g. corrupt/unsupported image bytes), the original upload is deleted
     * so no orphaned object is left in R2, and the failure propagates to the caller.
     */
    public String resizeStoredImage(String objectKey, int maxDimension) {
        String resizedKey = withJpgExtension(objectKey);
        try {
            byte[] original = downloadObject(objectKey);
            byte[] resized = imageProcessingService.resizeToJpeg(original, maxDimension, maxDimension);
            uploadBytes(resizedKey, JPEG_CONTENT_TYPE, resized);
        } catch (RuntimeException e) {
            deleteObjectIfPresent(objectKey);
            throw e;
        }
        if (!resizedKey.equals(objectKey)) {
            deleteObjectIfPresent(objectKey);
        }
        return resizedKey;
    }

    private String withJpgExtension(String objectKey) {
        int lastDot = objectKey.lastIndexOf('.');
        String withoutExtension = lastDot >= 0 ? objectKey.substring(0, lastDot) : objectKey;
        return withoutExtension + ".jpg";
    }

    private byte[] downloadObject(String objectKey) {
        return s3Client.getObjectAsBytes(GetObjectRequest.builder()
                        .bucket(properties.bucket())
                        .key(objectKey)
                        .build())
                .asByteArray();
    }

    private void uploadBytes(String objectKey, String contentType, byte[] bytes) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(properties.bucket())
                        .key(objectKey)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(bytes));
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
        String withScheme = base.matches("(?i)^https?://.*") ? base : "https://" + base;
        return withScheme.endsWith("/") ? withScheme.substring(0, withScheme.length() - 1) : withScheme;
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
