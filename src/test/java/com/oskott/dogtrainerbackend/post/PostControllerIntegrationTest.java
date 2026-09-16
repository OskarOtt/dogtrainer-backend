package com.oskott.dogtrainerbackend.post;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PostControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private S3Presigner s3Presigner;

    @MockitoBean
    private S3Client s3Client;

    @Test
    void standalonePostIsPubliclyReadableButOnlyAuthorCanDeleteIt() throws Exception {
        String ownerToken = registerAndGetAccessToken("post-owner-" + System.nanoTime() + "@example.com");
        String otherToken = registerAndGetAccessToken("post-other-" + System.nanoTime() + "@example.com");
        String dogId = createDog(ownerToken, "Rex");

        JsonNode created = readBody(mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Great walk today!\",\"dogId\":\"" + dogId + "\"}"))
                .andExpect(status().isCreated()));
        String postId = created.get("id").asString();
        assertThat(created.get("content").asString()).isEqualTo("Great walk today!");
        assertThat(created.get("dogId").asString()).isEqualTo(dogId);
        assertThat(created.get("dogName").asString()).isEqualTo("Rex");
        assertThat(created.get("trainingSessionId").isNull()).isTrue();

        // any authenticated user can read the post - a feed is public within the app
        JsonNode readByOther = readBody(mockMvc.perform(get("/api/v1/posts/" + postId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk()));
        assertThat(readByOther.get("id").asString()).isEqualTo(postId);

        // but only the author can delete it
        mockMvc.perform(delete("/api/v1/posts/" + postId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/posts/" + postId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/posts/" + postId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void creatingAStandalonePostForSomeoneElsesDogIsForbidden() throws Exception {
        String ownerToken = registerAndGetAccessToken("dog-owner-" + System.nanoTime() + "@example.com");
        String otherToken = registerAndGetAccessToken("dog-other-" + System.nanoTime() + "@example.com");
        String dogId = createDog(ownerToken, "Rex");

        mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Not my dog\",\"dogId\":\"" + dogId + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void postFromSessionRequiresCompletionAllowsOneOnly() throws Exception {
        String ownerToken = registerAndGetAccessToken("session-owner-" + System.nanoTime() + "@example.com");
        String otherToken = registerAndGetAccessToken("session-other-" + System.nanoTime() + "@example.com");
        String dogId = createDog(ownerToken, "Buddy");
        String sessionId = createSession(ownerToken, dogId);

        // cannot post an in-progress session
        mockMvc.perform(post("/api/v1/posts/from-session/" + sessionId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());

        // another user cannot even see the session, let alone post it
        mockMvc.perform(post("/api/v1/posts/from-session/" + sessionId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/training-sessions/" + sessionId + "/complete")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        JsonNode postFromSession = readBody(mockMvc.perform(post("/api/v1/posts/from-session/" + sessionId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated()));
        assertThat(postFromSession.get("trainingSessionId").asString()).isEqualTo(sessionId);
        assertThat(postFromSession.get("dogId").asString()).isEqualTo(dogId);
        assertThat(postFromSession.get("content").asString()).contains("Buddy").contains("training session");

        // a session can only ever be posted once
        mockMvc.perform(post("/api/v1/posts/from-session/" + sessionId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    void feedIncludesOwnAndFollowedPostsNewestFirstWithCursorPagination() throws Exception {
        String userAToken = registerAndGetAccessToken("feed-a-" + System.nanoTime() + "@example.com");
        String userBToken = registerAndGetAccessToken("feed-b-" + System.nanoTime() + "@example.com");
        String userCToken = registerAndGetAccessToken("feed-c-" + System.nanoTime() + "@example.com");
        String userBId = userIdFromToken(userBToken);

        mockMvc.perform(post("/api/v1/users/" + userBId + "/follow")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isNoContent());

        createStandalonePost(userCToken, "from C, not followed - should not appear");
        Thread.sleep(5);
        createStandalonePost(userBToken, "from B, followed, post 1");
        Thread.sleep(5);
        createStandalonePost(userAToken, "from A, own post");
        Thread.sleep(5);
        createStandalonePost(userBToken, "from B, followed, post 2");

        JsonNode firstPage = readBody(mockMvc.perform(get("/api/v1/feed?limit=2")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk()));
        assertThat(firstPage.get("items")).hasSize(2);
        assertThat(firstPage.get("items").get(0).get("content").asString()).isEqualTo("from B, followed, post 2");
        assertThat(firstPage.get("items").get(1).get("content").asString()).isEqualTo("from A, own post");
        String nextCursor = firstPage.get("nextCursor").asString();
        assertThat(nextCursor).isNotBlank();

        JsonNode secondPage = readBody(mockMvc.perform(get("/api/v1/feed?limit=2&cursor=" + nextCursor)
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk()));
        assertThat(secondPage.get("items")).hasSize(1);
        assertThat(secondPage.get("items").get(0).get("content").asString()).isEqualTo("from B, followed, post 1");
        assertThat(secondPage.get("nextCursor").isNull()).isTrue();

        // sanity: C's post never appears anywhere in A's feed
        for (JsonNode item : firstPage.get("items")) {
            assertThat(item.get("content").asString()).doesNotContain("from C");
        }
    }

    @Test
    void userPostsEndpointListsOnlyThatUsersPosts() throws Exception {
        String userAToken = registerAndGetAccessToken("profile-a-" + System.nanoTime() + "@example.com");
        String userBToken = registerAndGetAccessToken("profile-b-" + System.nanoTime() + "@example.com");
        String userAId = userIdFromToken(userAToken);

        createStandalonePost(userAToken, "A post 1");
        Thread.sleep(5);
        createStandalonePost(userBToken, "B post 1");
        Thread.sleep(5);
        createStandalonePost(userAToken, "A post 2");

        JsonNode aPosts = readBody(mockMvc.perform(get("/api/v1/users/" + userAId + "/posts")
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isOk()));
        assertThat(aPosts.get("items")).hasSize(2);
        assertThat(aPosts.get("items").get(0).get("content").asString()).isEqualTo("A post 2");
        assertThat(aPosts.get("items").get(1).get("content").asString()).isEqualTo("A post 1");
    }

    @Test
    void postMediaUploadConfirmAndDeleteFlowIsScopedToTheAuthor() throws Exception {
        String ownerToken = registerAndGetAccessToken("media-owner-" + System.nanoTime() + "@example.com");
        String otherToken = registerAndGetAccessToken("media-other-" + System.nanoTime() + "@example.com");
        String postId = createStandalonePost(ownerToken, "Photo post");

        PresignedPutObjectRequest presignedRequest = mock(PresignedPutObjectRequest.class);
        when(presignedRequest.url()).thenReturn(URI.create("https://dummy.r2.cloudflarestorage.com/signed-image").toURL());
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedRequest);

        mockMvc.perform(post("/api/v1/posts/" + postId + "/media/upload-url")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/jpeg\",\"fileSizeBytes\":2048}"))
                .andExpect(status().isForbidden());

        JsonNode uploadUrlResponse = readBody(mockMvc.perform(post("/api/v1/posts/" + postId + "/media/upload-url")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/jpeg\",\"fileSizeBytes\":2048}"))
                .andExpect(status().isOk()));
        String objectKey = uploadUrlResponse.get("objectKey").asString();
        assertThat(objectKey).startsWith("posts/" + postId + "/").endsWith(".jpg");

        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().build());
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), samplePngBytes()));

        JsonNode confirmed = readBody(mockMvc.perform(put("/api/v1/posts/" + postId + "/media")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectKey\":\"" + objectKey + "\"}"))
                .andExpect(status().isOk()));
        assertThat(confirmed.get("imageUrl").asString()).isEqualTo("https://dev-only-dummy.example.com/" + objectKey);

        mockMvc.perform(delete("/api/v1/posts/" + postId + "/media")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/posts/" + postId + "/media")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());
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

    private String createStandalonePost(String accessToken, String content) throws Exception {
        JsonNode created = readBody(mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\"}"))
                .andExpect(status().isCreated()));
        return created.get("id").asString();
    }

    private String createDog(String accessToken, String name) throws Exception {
        JsonNode dog = readBody(mockMvc.perform(post("/api/v1/dogs")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()));
        return dog.get("id").asString();
    }

    private String createSession(String accessToken, String dogId) throws Exception {
        JsonNode session = readBody(mockMvc.perform(post("/api/v1/dogs/" + dogId + "/training-sessions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated()));
        return session.get("id").asString();
    }

    /**
     * The access token's JWT "sub" claim is the user id (see JwtService). Decoding it locally
     * avoids needing a dedicated "get my user id" endpoint just for these tests.
     */
    private String userIdFromToken(String accessToken) {
        String[] parts = accessToken.split("\\.");
        byte[] payloadBytes = java.util.Base64.getUrlDecoder().decode(parts[1]);
        JsonNode payload = objectMapper.readTree(new String(payloadBytes, java.nio.charset.StandardCharsets.UTF_8));
        return payload.get("sub").asString();
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
