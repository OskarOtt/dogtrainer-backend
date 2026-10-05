package com.oskott.dogtrainerbackend.dog;

import com.oskott.dogtrainerbackend.auth.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the read-only public dog profile endpoints ({@code GET /dogs/{id}/public} and
 * {@code GET /users/{id}/dogs}), which back the app's public dog profile screen.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PublicDogProfileControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void anyoneCanViewAnotherUsersDogPublicProfile() throws Exception {
        String ownerAccessToken = registerAndGetAccessToken("public-owner-" + System.nanoTime() + "@example.com");
        String viewerAccessToken = registerAndGetAccessToken("public-viewer-" + System.nanoTime() + "@example.com");
        String dogId = createDog(ownerAccessToken, "Rex").get("id").asString();

        JsonNode profile = readBody(mockMvc.perform(get("/api/v1/dogs/" + dogId + "/public")
                        .header("Authorization", "Bearer " + viewerAccessToken))
                .andExpect(status().isOk()));

        assertThat(profile.get("id").asString()).isEqualTo(dogId);
        assertThat(profile.get("name").asString()).isEqualTo("Rex");
        assertThat(profile.get("ownerName").asString()).isNotBlank();
        assertThat(profile.get("completedSessionCount").asLong()).isZero();
        assertThat(profile.get("titles").isArray()).isTrue();
        assertThat(profile.get("recentSessions").isArray()).isTrue();
        // weight is owner-private and must not leak on the public profile
        assertThat(profile.has("weight")).isFalse();
    }

    @Test
    void publicDogProfileReturns404ForUnknownDog() throws Exception {
        String viewerAccessToken = registerAndGetAccessToken("public-viewer-" + System.nanoTime() + "@example.com");

        mockMvc.perform(get("/api/v1/dogs/" + java.util.UUID.randomUUID() + "/public")
                        .header("Authorization", "Bearer " + viewerAccessToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void publicDogProfileIsHiddenAcrossABlockRelationship() throws Exception {
        String ownerAccessToken = registerAndGetAccessToken("public-blocked-owner-" + System.nanoTime() + "@example.com");
        String viewerAccessToken = registerAndGetAccessToken("public-blocked-viewer-" + System.nanoTime() + "@example.com");
        String dogId = createDog(ownerAccessToken, "Rex").get("id").asString();
        String ownerId = readBody(mockMvc.perform(get("/api/v1/dogs/" + dogId + "/public")
                        .header("Authorization", "Bearer " + viewerAccessToken))
                .andExpect(status().isOk()))
                .get("ownerId").asString();

        mockMvc.perform(post("/api/v1/users/" + ownerId + "/block")
                        .header("Authorization", "Bearer " + viewerAccessToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/dogs/" + dogId + "/public")
                        .header("Authorization", "Bearer " + viewerAccessToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/users/" + ownerId + "/dogs")
                        .header("Authorization", "Bearer " + viewerAccessToken))
                .andExpect(status().isNotFound());

        // the owner can still view their own dog and dog list despite the block
        mockMvc.perform(get("/api/v1/dogs/" + dogId + "/public")
                        .header("Authorization", "Bearer " + ownerAccessToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users/" + ownerId + "/dogs")
                        .header("Authorization", "Bearer " + ownerAccessToken))
                .andExpect(status().isOk());
    }

    @Test
    void listUsersDogsReturnsAllOfThatUsersDogsInOrder() throws Exception {
        String ownerAccessToken = registerAndGetAccessToken("public-list-owner-" + System.nanoTime() + "@example.com");
        String viewerAccessToken = registerAndGetAccessToken("public-list-viewer-" + System.nanoTime() + "@example.com");
        String rexId = createDog(ownerAccessToken, "Rex").get("id").asString();
        String fidoId = createDog(ownerAccessToken, "Fido").get("id").asString();
        String ownerId = readBody(mockMvc.perform(get("/api/v1/dogs/" + rexId + "/public")
                        .header("Authorization", "Bearer " + viewerAccessToken))
                .andExpect(status().isOk()))
                .get("ownerId").asString();

        JsonNode dogs = readBody(mockMvc.perform(get("/api/v1/users/" + ownerId + "/dogs")
                        .header("Authorization", "Bearer " + viewerAccessToken))
                .andExpect(status().isOk()));

        assertThat(dogs.isArray()).isTrue();
        java.util.List<String> ids = new java.util.ArrayList<>();
        dogs.forEach(node -> ids.add(node.get("id").asString()));
        assertThat(ids).containsExactly(rexId, fidoId);
    }

    @Test
    void publicDogProfileRecentSessionsExcludeCancelledSessions() throws Exception {
        String ownerAccessToken = registerAndGetAccessToken("public-sessions-owner-" + System.nanoTime() + "@example.com");
        String viewerAccessToken = registerAndGetAccessToken("public-sessions-viewer-" + System.nanoTime() + "@example.com");
        String dogId = createDog(ownerAccessToken, "Rex").get("id").asString();

        String completedSessionId = createSession(ownerAccessToken, dogId).get("id").asString();
        completeSession(ownerAccessToken, completedSessionId);
        String cancelledSessionId = createSession(ownerAccessToken, dogId).get("id").asString();
        cancelSession(ownerAccessToken, cancelledSessionId);

        JsonNode profile = readBody(mockMvc.perform(get("/api/v1/dogs/" + dogId + "/public")
                        .header("Authorization", "Bearer " + viewerAccessToken))
                .andExpect(status().isOk()));

        assertThat(profile.get("completedSessionCount").asLong()).isEqualTo(1);
        java.util.List<String> recentSessionIds = new java.util.ArrayList<>();
        profile.get("recentSessions").forEach(node -> recentSessionIds.add(node.get("id").asString()));
        assertThat(recentSessionIds).containsExactly(completedSessionId);
    }

    private JsonNode createSession(String accessToken, String dogId) throws Exception {
        return readBody(mockMvc.perform(post("/api/v1/dogs/" + dogId + "/training-sessions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated()));
    }

    private void completeSession(String accessToken, String sessionId) throws Exception {
        mockMvc.perform(post("/api/v1/training-sessions/" + sessionId + "/complete")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    private void cancelSession(String accessToken, String sessionId) throws Exception {
        mockMvc.perform(post("/api/v1/training-sessions/" + sessionId + "/cancel")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    private JsonNode createDog(String accessToken, String name) throws Exception {
        String payload = "{\"name\":\"" + name + "\"}";
        return readBody(mockMvc.perform(post("/api/v1/dogs")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated()));
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
