package com.oskott.dogtrainerbackend.storage;

import com.oskott.dogtrainerbackend.common.exception.InvalidFileException;
import com.oskott.dogtrainerbackend.storage.config.R2Properties;
import com.oskott.dogtrainerbackend.storage.dto.UploadUrlRequest;
import com.oskott.dogtrainerbackend.storage.dto.UploadUrlResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private S3Client s3Client;

    @Mock
    private ImageProcessingService imageProcessingService;

    private R2Properties properties;
    private StorageService storageService;

    @BeforeEach
    void setUp() {
        properties = new R2Properties(
                "https://dummy.r2.cloudflarestorage.com",
                "access-key",
                "secret-key",
                "test-bucket",
                "https://media.example.com/",
                600
        );
        storageService = new StorageService(s3Presigner, s3Client, properties, imageProcessingService);
    }

    @Test
    void createUploadUrlReturnsPresignedUrlAndNamespacedKeyForValidImage() throws MalformedURLException {
        PresignedPutObjectRequest presignedRequest = mock(PresignedPutObjectRequest.class);
        when(presignedRequest.url()).thenReturn(URI.create("https://dummy.r2.cloudflarestorage.com/test-bucket/signed").toURL());
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedRequest);

        UploadUrlResponse response = storageService.createUploadUrl(
                "avatars/owner-1", MediaCategory.IMAGE, new UploadUrlRequest("image/png", 1024));

        assertThat(response.uploadUrl()).isEqualTo("https://dummy.r2.cloudflarestorage.com/test-bucket/signed");
        assertThat(response.objectKey()).startsWith("avatars/owner-1/").endsWith(".png");
    }

    @Test
    void createUploadUrlRejectsOversizedFile() {
        UploadUrlRequest request = new UploadUrlRequest("image/png", MediaCategory.IMAGE.maxSizeBytes() + 1);

        assertThatThrownBy(() -> storageService.createUploadUrl("avatars/owner-1", MediaCategory.IMAGE, request))
                .isInstanceOf(InvalidFileException.class);
    }

    @Test
    void createUploadUrlRejectsDisallowedContentType() {
        UploadUrlRequest request = new UploadUrlRequest("application/pdf", 1024);

        assertThatThrownBy(() -> storageService.createUploadUrl("avatars/owner-1", MediaCategory.IMAGE, request))
                .isInstanceOf(InvalidFileException.class);
    }

    @Test
    void objectExistsReturnsTrueWhenHeadObjectSucceeds() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().build());

        assertThat(storageService.objectExists("avatars/owner-1/file.png")).isTrue();
    }

    @Test
    void objectExistsReturnsFalseOnNoSuchKey() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(NoSuchKeyException.builder().build());

        assertThat(storageService.objectExists("avatars/owner-1/missing.png")).isFalse();
    }

    @Test
    void objectExistsReturnsFalseOn404StatusS3Exception() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).build());

        assertThat(storageService.objectExists("avatars/owner-1/missing.png")).isFalse();
    }

    @Test
    void buildPublicUrlJoinsBaseAndKeyWithoutDoubleSlash() {
        assertThat(storageService.buildPublicUrl("avatars/owner-1/file.png"))
                .isEqualTo("https://media.example.com/avatars/owner-1/file.png");
    }

    @Test
    void buildPublicUrlPrependsHttpsSchemeWhenPublicBaseUrlIsMissingIt() {
        R2Properties schemeLessProperties = new R2Properties(
                "https://dummy.r2.cloudflarestorage.com",
                "access-key",
                "secret-key",
                "test-bucket",
                "media.example.com",
                600
        );
        StorageService schemeLessStorageService =
                new StorageService(s3Presigner, s3Client, schemeLessProperties, imageProcessingService);

        assertThat(schemeLessStorageService.buildPublicUrl("avatars/owner-1/file.png"))
                .isEqualTo("https://media.example.com/avatars/owner-1/file.png");
    }

    @Test
    void extractObjectKeyReversesBuildPublicUrl() {
        String publicUrl = storageService.buildPublicUrl("avatars/owner-1/file.png");

        assertThat(storageService.extractObjectKey(publicUrl)).isEqualTo("avatars/owner-1/file.png");
    }

    @Test
    void extractObjectKeyReturnsNullForNullOrUnrelatedUrl() {
        assertThat(storageService.extractObjectKey(null)).isNull();
        assertThat(storageService.extractObjectKey("https://unrelated.example.com/file.png")).isNull();
    }

    @Test
    void resizeStoredImageUploadsAsJpegAndDeletesOriginalWhenExtensionChanges() {
        byte[] originalBytes = {1, 2, 3};
        byte[] resizedBytes = {4, 5, 6};
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), originalBytes));
        when(imageProcessingService.resizeToJpeg(originalBytes, 400, 400)).thenReturn(resizedBytes);

        String resizedKey = storageService.resizeStoredImage("avatars/owner-1/file.png", 400);

        assertThat(resizedKey).isEqualTo("avatars/owner-1/file.jpg");
        verify(s3Client).putObject(
                argThat((PutObjectRequest req) -> req.key().equals("avatars/owner-1/file.jpg")
                        && req.contentType().equals("image/jpeg")),
                any(RequestBody.class));
        verify(s3Client).deleteObject(
                argThat((DeleteObjectRequest req) -> req.key().equals("avatars/owner-1/file.png")));
    }

    @Test
    void resizeStoredImageDoesNotDeleteOriginalWhenKeyUnchanged() {
        byte[] originalBytes = {1, 2, 3};
        byte[] resizedBytes = {4, 5, 6};
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), originalBytes));
        when(imageProcessingService.resizeToJpeg(originalBytes, 400, 400)).thenReturn(resizedBytes);

        String resizedKey = storageService.resizeStoredImage("avatars/owner-1/file.jpg", 400);

        assertThat(resizedKey).isEqualTo("avatars/owner-1/file.jpg");
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void resizeStoredImageDeletesOriginalAndPropagatesFailureOnCorruptImage() {
        byte[] originalBytes = {1, 2, 3};
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), originalBytes));
        when(imageProcessingService.resizeToJpeg(eq(originalBytes), any(Integer.class), any(Integer.class)))
                .thenThrow(new InvalidFileException("corrupt image"));

        assertThatThrownBy(() -> storageService.resizeStoredImage("avatars/owner-1/file.png", 400))
                .isInstanceOf(InvalidFileException.class);

        verify(s3Client).deleteObject(
                argThat((DeleteObjectRequest req) -> req.key().equals("avatars/owner-1/file.png")));
        verify(s3Client, times(1)).deleteObject(any(DeleteObjectRequest.class));
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
}
