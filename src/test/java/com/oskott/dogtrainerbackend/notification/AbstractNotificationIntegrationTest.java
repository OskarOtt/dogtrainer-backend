package com.oskott.dogtrainerbackend.notification;

import com.oskott.dogtrainerbackend.auth.dto.RegisterRequest;
import com.oskott.dogtrainerbackend.notification.service.ExpoPushClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
abstract class AbstractNotificationIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockitoBean
    protected ExpoPushClient expoPushClient;

    @MockitoBean
    private S3Presigner s3Presigner;

    @MockitoBean
    private S3Client s3Client;

    protected record TestUser(String token, String id) {
    }

    protected TestUser registerUser(String prefix) throws Exception {
        String email = prefix + "-" + System.nanoTime() + "@example.com";
        RegisterRequest registerRequest = new RegisterRequest(email, prefix + " User", "SuperSecret123");
        JsonNode body = readBody(mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated()));
        String token = body.get("accessToken").asString();
        return new TestUser(token, userIdFromToken(token));
    }

    protected void follow(TestUser follower, TestUser followee) throws Exception {
        mockMvc.perform(post("/api/v1/users/" + followee.id() + "/follow")
                        .header("Authorization", bearer(follower)))
                .andExpect(status().isNoContent());
    }

    protected void unfollow(TestUser follower, TestUser followee) throws Exception {
        mockMvc.perform(delete("/api/v1/users/" + followee.id() + "/follow")
                        .header("Authorization", bearer(follower)))
                .andExpect(status().isNoContent());
    }

    protected void block(TestUser blocker, TestUser blocked) throws Exception {
        mockMvc.perform(post("/api/v1/users/" + blocked.id() + "/block")
                        .header("Authorization", bearer(blocker)))
                .andExpect(status().is2xxSuccessful());
    }

    protected String createPost(TestUser author) throws Exception {
        JsonNode created = readBody(mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"A walk in the park\"}"))
                .andExpect(status().isCreated()));
        return created.get("id").asString();
    }

    protected void comment(TestUser commenter, String postId) throws Exception {
        mockMvc.perform(post("/api/v1/posts/" + postId + "/comments")
                        .header("Authorization", bearer(commenter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Nice!\"}"))
                .andExpect(status().is2xxSuccessful());
    }

    protected void registerDevice(TestUser user, String token, String platform, String locale) throws Exception {
        mockMvc.perform(put("/api/v1/devices")
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expoPushToken\":\"" + token + "\",\"platform\":\"" + platform
                                + "\",\"locale\":\"" + locale + "\"}"))
                .andExpect(status().isNoContent());
    }

    protected JsonNode notificationsOf(TestUser user) throws Exception {
        return readBody(mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", bearer(user)))
                .andExpect(status().isOk())).get("items");
    }

    protected long unreadCountOf(TestUser user) throws Exception {
        return readBody(mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", bearer(user)))
                .andExpect(status().isOk())).get("count").asLong();
    }

    protected String bearer(TestUser user) {
        return "Bearer " + user.token();
    }

    protected static String uniqueToken() {
        return "ExponentPushToken[test-" + System.nanoTime() + "]";
    }

    /** The access token's JWT "sub" claim is the user id (see JwtService). */
    private String userIdFromToken(String accessToken) {
        String[] parts = accessToken.split("\\.");
        byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
        JsonNode payload = objectMapper.readTree(new String(payloadBytes, StandardCharsets.UTF_8));
        return payload.get("sub").asString();
    }

    protected JsonNode readBody(ResultActions resultActions) throws Exception {
        String content = resultActions.andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(content);
    }
}
