package com.oskott.dogtrainerbackend.user;

import com.oskott.dogtrainerbackend.auth.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the avatar upload flow against real R2ClientConfig-backed beans whose S3Client/
 * S3Presigner are swapped for mocks, so validation/ownership logic in StorageService/UserService
 * runs for real without ever making a network call to R2.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private S3Presigner s3Presigner;

    @MockitoBean
    private S3Client s3Client;

    @Test
    void avatarUploadConfirmAndDeleteFlow() throws Exception {
        String accessToken = registerAndGetAccessToken("avatar-" + System.nanoTime() + "@example.com");
        stubPresignedUrl("https://dummy.r2.cloudflarestorage.com/signed-avatar");

        JsonNode uploadUrlResponse = readBody(mockMvc.perform(post("/api/v1/users/me/avatar/upload-url")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/png\",\"fileSizeBytes\":1024}"))
                .andExpect(status().isOk()));
        assertThat(uploadUrlResponse.get("uploadUrl").asString()).isEqualTo("https://dummy.r2.cloudflarestorage.com/signed-avatar");
        String objectKey = uploadUrlResponse.get("objectKey").asString();
        assertThat(objectKey).endsWith(".png");

        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().build());
        JsonNode confirmed = readBody(mockMvc.perform(put("/api/v1/users/me/avatar")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectKey\":\"" + objectKey + "\"}"))
                .andExpect(status().isOk()));
        assertThat(confirmed.get("avatarUrl").asString()).isEqualTo("https://dev-only-dummy.example.com/" + objectKey);

        mockMvc.perform(delete("/api/v1/users/me/avatar")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void uploadUrlRejectsUnsupportedContentType() throws Exception {
        String accessToken = registerAndGetAccessToken("bad-content-type-" + System.nanoTime() + "@example.com");

        mockMvc.perform(post("/api/v1/users/me/avatar/upload-url")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"application/pdf\",\"fileSizeBytes\":1024}"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void uploadUrlRejectsOversizedFile() throws Exception {
        String accessToken = registerAndGetAccessToken("oversized-" + System.nanoTime() + "@example.com");

        mockMvc.perform(post("/api/v1/users/me/avatar/upload-url")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/png\",\"fileSizeBytes\":999999999}"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void confirmRejectsObjectKeyThatWasNeverUploaded() throws Exception {
        String accessToken = registerAndGetAccessToken("never-uploaded-" + System.nanoTime() + "@example.com");
        stubPresignedUrl("https://dummy.r2.cloudflarestorage.com/signed");

        JsonNode uploadUrlResponse = readBody(mockMvc.perform(post("/api/v1/users/me/avatar/upload-url")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/png\",\"fileSizeBytes\":1024}"))
                .andExpect(status().isOk()));
        String objectKey = uploadUrlResponse.get("objectKey").asString();

        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(NoSuchKeyException.builder().build());
        mockMvc.perform(put("/api/v1/users/me/avatar")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectKey\":\"" + objectKey + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void confirmRejectsObjectKeyOutsideOwnAvatarPrefix() throws Exception {
        String accessToken = registerAndGetAccessToken("wrong-prefix-" + System.nanoTime() + "@example.com");

        mockMvc.perform(put("/api/v1/users/me/avatar")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectKey\":\"avatars/someone-else/file.png\"}"))
                .andExpect(status().isForbidden());
    }

    private void stubPresignedUrl(String url) throws Exception {
        PresignedPutObjectRequest presignedRequest = mock(PresignedPutObjectRequest.class);
        when(presignedRequest.url()).thenReturn(URI.create(url).toURL());
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedRequest);
    }

    private String registerAndGetAccessToken(String email) throws Exception {
        RegisterRequest registerRequest = new RegisterRequest(email, "Test User", "SuperSecret123");
        JsonNode body = readBody(mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated()));
        return body.get("accessToken").asString();
    }

    private JsonNode readBody(ResultActions resultActions) throws Exception {
        String content = resultActions.andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(content);
    }
}
