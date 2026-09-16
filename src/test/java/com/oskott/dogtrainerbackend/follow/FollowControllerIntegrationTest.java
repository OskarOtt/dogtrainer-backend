package com.oskott.dogtrainerbackend.follow;

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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FollowControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void followUnfollowUpdatesFollowersAndFollowingLists() throws Exception {
        String aToken = registerAndGetAccessToken("follow-a-" + System.nanoTime() + "@example.com");
        String bToken = registerAndGetAccessToken("follow-b-" + System.nanoTime() + "@example.com");
        String aId = userIdFromToken(aToken);
        String bId = userIdFromToken(bToken);

        mockMvc.perform(post("/api/v1/users/" + bId + "/follow")
                        .header("Authorization", "Bearer " + aToken))
                .andExpect(status().isNoContent());

        JsonNode aFollowing = readBody(mockMvc.perform(get("/api/v1/users/" + aId + "/following")
                        .header("Authorization", "Bearer " + aToken))
                .andExpect(status().isOk()));
        assertThat(aFollowing).hasSize(1);
        assertThat(aFollowing.get(0).get("id").asString()).isEqualTo(bId);

        JsonNode bFollowers = readBody(mockMvc.perform(get("/api/v1/users/" + bId + "/followers")
                        .header("Authorization", "Bearer " + bToken))
                .andExpect(status().isOk()));
        assertThat(bFollowers).hasSize(1);
        assertThat(bFollowers.get(0).get("id").asString()).isEqualTo(aId);

        mockMvc.perform(delete("/api/v1/users/" + bId + "/follow")
                        .header("Authorization", "Bearer " + aToken))
                .andExpect(status().isNoContent());

        JsonNode aFollowingAfterUnfollow = readBody(mockMvc.perform(get("/api/v1/users/" + aId + "/following")
                        .header("Authorization", "Bearer " + aToken))
                .andExpect(status().isOk()));
        assertThat(aFollowingAfterUnfollow).isEmpty();
    }

    @Test
    void cannotFollowYourself() throws Exception {
        String token = registerAndGetAccessToken("follow-self-" + System.nanoTime() + "@example.com");
        String id = userIdFromToken(token);

        mockMvc.perform(post("/api/v1/users/" + id + "/follow")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    void cannotFollowTheSameUserTwice() throws Exception {
        String aToken = registerAndGetAccessToken("follow-dup-a-" + System.nanoTime() + "@example.com");
        String bToken = registerAndGetAccessToken("follow-dup-b-" + System.nanoTime() + "@example.com");
        String bId = userIdFromToken(bToken);

        mockMvc.perform(post("/api/v1/users/" + bId + "/follow")
                        .header("Authorization", "Bearer " + aToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/users/" + bId + "/follow")
                        .header("Authorization", "Bearer " + aToken))
                .andExpect(status().isConflict());
    }

    @Test
    void followingAndUnfollowingAnUnknownUserReturnsNotFound() throws Exception {
        String token = registerAndGetAccessToken("follow-unknown-" + System.nanoTime() + "@example.com");
        String unknownUserId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/users/" + unknownUserId + "/follow")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/users/" + unknownUserId + "/follow")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    /**
     * The access token's JWT "sub" claim is the user id (see JwtService). Decoding it locally
     * avoids needing a dedicated "get my user id" endpoint just for these tests.
     */
    private String userIdFromToken(String accessToken) {
        String[] parts = accessToken.split("\\.");
        byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
        JsonNode payload = objectMapper.readTree(new String(payloadBytes, StandardCharsets.UTF_8));
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
