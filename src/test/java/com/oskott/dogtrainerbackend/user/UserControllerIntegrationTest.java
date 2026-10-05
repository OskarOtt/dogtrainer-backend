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
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), samplePngBytes()));
        JsonNode confirmed = readBody(mockMvc.perform(put("/api/v1/users/me/avatar")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectKey\":\"" + objectKey + "\"}"))
                .andExpect(status().isOk()));
        String resizedObjectKey = objectKey.substring(0, objectKey.lastIndexOf('.')) + ".jpg";
        assertThat(confirmed.get("avatarUrl").asString()).isEqualTo("https://dev-only-dummy.example.com/" + resizedObjectKey);

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

    @Test
    void getUserReturnsPublicProfileWithoutEmailAndUnknownIdIs404() throws Exception {
        String viewedToken = registerAndGetAccessToken("viewed-" + System.nanoTime() + "@example.com");
        String viewerToken = registerAndGetAccessToken("viewer-" + System.nanoTime() + "@example.com");
        String viewedUserId = userIdFromToken(viewedToken);

        JsonNode profile = readBody(mockMvc.perform(get("/api/v1/users/" + viewedUserId)
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk()));
        assertThat(profile.get("id").asString()).isEqualTo(viewedUserId);
        assertThat(profile.get("name").asString()).isEqualTo("Test User");
        assertThat(profile.has("email")).isFalse();

        mockMvc.perform(get("/api/v1/users/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateDisplayNameTrimsAndPersistsValidName() throws Exception {
        String accessToken = registerAndGetAccessToken("rename-valid-" + System.nanoTime() + "@example.com");

        JsonNode updated = readBody(mockMvc.perform(put("/api/v1/users/me/username")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Anna Lee, Jr.\"}"))
                .andExpect(status().isOk()));
        assertThat(updated.get("name").asString()).isEqualTo("Anna Lee, Jr.");
    }

    @Test
    void updateDisplayNameRejectsNameOver30Characters() throws Exception {
        String accessToken = registerAndGetAccessToken("too-long-" + System.nanoTime() + "@example.com");
        String tooLong = "A".repeat(31);

        mockMvc.perform(put("/api/v1/users/me/username")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateDisplayNameRejectsSpecialCharactersAndEmoji() throws Exception {
        String accessToken = registerAndGetAccessToken("special-chars-" + System.nanoTime() + "@example.com");

        mockMvc.perform(put("/api/v1/users/me/username")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Anna@Lee!\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/users/me/username")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Anna \uD83D\uDE00\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateDisplayNameRejectsBlankName() throws Exception {
        String accessToken = registerAndGetAccessToken("blank-name-" + System.nanoTime() + "@example.com");

        mockMvc.perform(put("/api/v1/users/me/username")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchUsersMatchesByNameAndIsCaseInsensitive() throws Exception {
        String uniqueSuffix = System.nanoTime() + "";
        registerAndGetAccessToken("search-name-" + uniqueSuffix + "@example.com", "Zelda Trigram " + uniqueSuffix);
        String searcherToken = registerAndGetAccessToken("search-name-searcher-" + uniqueSuffix + "@example.com");

        JsonNode results = readBody(mockMvc.perform(get("/api/v1/users/search")
                        .header("Authorization", "Bearer " + searcherToken)
                        .param("q", "zelda trigram " + uniqueSuffix))
                .andExpect(status().isOk()));
        assertThat(results.isArray()).isTrue();
        assertThat(results.size()).isEqualTo(1);
        assertThat(results.get(0).get("name").asString()).isEqualTo("Zelda Trigram " + uniqueSuffix);
        assertThat(results.get(0).has("username")).isTrue();
        assertThat(results.get(0).has("email")).isFalse();
    }

    @Test
    void searchUsersMatchesByGeneratedUsername() throws Exception {
        // Keep this short: the username is truncated to 24 chars after slugifying, so a long
        // suffix (e.g. a raw nanoTime value) would get cut off and no longer match the query.
        String uniqueSuffix = Long.toString(System.nanoTime(), 36);
        registerAndGetAccessToken("search-username-" + uniqueSuffix + "@example.com", "Wendy Handle " + uniqueSuffix);
        String searcherToken = registerAndGetAccessToken("search-username-searcher-" + uniqueSuffix + "@example.com");

        // The auto-derived username is a slug of the name, e.g. "wendy-handle-<suffix>".
        JsonNode results = readBody(mockMvc.perform(get("/api/v1/users/search")
                        .header("Authorization", "Bearer " + searcherToken)
                        .param("q", "wendy-handle-" + uniqueSuffix))
                .andExpect(status().isOk()));
        assertThat(results.size()).isEqualTo(1);
        assertThat(results.get(0).get("username").asString()).isEqualTo("wendy-handle-" + uniqueSuffix);
    }

    @Test
    void searchUsersRejectsQueryShorterThanTwoCharacters() throws Exception {
        String accessToken = registerAndGetAccessToken("search-short-query-" + System.nanoTime() + "@example.com");

        mockMvc.perform(get("/api/v1/users/search")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("q", "a"))
                .andExpect(status().isConflict());
    }

    @Test
    void searchUsersRespectsLimitParameter() throws Exception {
        String uniqueSuffix = System.nanoTime() + "";
        String searcherToken = registerAndGetAccessToken("search-limit-searcher-" + uniqueSuffix + "@example.com");
        for (int i = 0; i < 3; i++) {
            registerAndGetAccessToken("search-limit-" + i + "-" + uniqueSuffix + "@example.com", "LimitMatch " + uniqueSuffix + " " + i);
        }

        JsonNode results = readBody(mockMvc.perform(get("/api/v1/users/search")
                        .header("Authorization", "Bearer " + searcherToken)
                        .param("q", "limitmatch " + uniqueSuffix)
                        .param("limit", "2"))
                .andExpect(status().isOk()));
        assertThat(results.size()).isEqualTo(2);
    }

    @Test
    void searchUsersExcludesDeletedAccounts() throws Exception {
        String uniqueSuffix = System.nanoTime() + "";
        String deletedToken = registerAndGetAccessToken("search-deleted-" + uniqueSuffix + "@example.com", "Deleteme Soon " + uniqueSuffix);
        String searcherToken = registerAndGetAccessToken("search-deleted-searcher-" + uniqueSuffix + "@example.com");

        mockMvc.perform(delete("/api/v1/users/me")
                        .header("Authorization", "Bearer " + deletedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"PASSWORD\",\"password\":\"SuperSecret123\"}"))
                .andExpect(status().isNoContent());

        JsonNode results = readBody(mockMvc.perform(get("/api/v1/users/search")
                        .header("Authorization", "Bearer " + searcherToken)
                        .param("q", "deleteme soon " + uniqueSuffix))
                .andExpect(status().isOk()));
        assertThat(results.size()).isEqualTo(0);
    }

    @Test
    void searchUsersExcludesTheCurrentUser() throws Exception {
        String uniqueSuffix = System.nanoTime() + "";
        String selfToken = registerAndGetAccessToken("search-self-" + uniqueSuffix + "@example.com", "Selfie Searcher " + uniqueSuffix);

        JsonNode results = readBody(mockMvc.perform(get("/api/v1/users/search")
                        .header("Authorization", "Bearer " + selfToken)
                        .param("q", "selfie searcher " + uniqueSuffix))
                .andExpect(status().isOk()));
        assertThat(results.size()).isEqualTo(0);
    }

    /**

     * The access token's JWT "sub" claim is the user id (see JwtService). Decoding it locally
     * avoids needing a round trip just to find out who we just registered.
     */
    private String userIdFromToken(String accessToken) {
        String[] parts = accessToken.split("\\.");
        byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
        JsonNode payload = objectMapper.readTree(new String(payloadBytes, StandardCharsets.UTF_8));
        return payload.get("sub").asString();
    }

    private void stubPresignedUrl(String url) throws Exception {
        PresignedPutObjectRequest presignedRequest = mock(PresignedPutObjectRequest.class);
        when(presignedRequest.url()).thenReturn(URI.create(url).toURL());
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedRequest);
    }

    private byte[] samplePngBytes() throws Exception {
        BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.BLUE);
            graphics.fillRect(0, 0, 200, 200);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private String registerAndGetAccessToken(String email) throws Exception {
        return registerAndGetAccessToken(email, "Test User");
    }

    private String registerAndGetAccessToken(String email, String name) throws Exception {
        RegisterRequest registerRequest = new RegisterRequest(email, name, "SuperSecret123");
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
