package com.oskott.dogtrainerbackend.auth;

import com.oskott.dogtrainerbackend.auth.dto.LoginRequest;
import com.oskott.dogtrainerbackend.auth.dto.RefreshRequest;
import com.oskott.dogtrainerbackend.auth.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registerLoginRefreshLogoutAndMeFlowWorksEndToEnd() throws Exception {
        String email = "trainer-" + System.nanoTime() + "@example.com";
        RegisterRequest registerRequest = new RegisterRequest(email, "Jane Trainer", "SuperSecret123");

        JsonNode registerBody = readBody(mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated()));
        assertThat(registerBody.get("accessToken").asString()).isNotBlank();
        assertThat(registerBody.get("refreshToken").asString()).isNotBlank();

        // duplicate registration is rejected
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isConflict());

        // login
        JsonNode loginBody = readBody(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "SuperSecret123"))))
                .andExpect(status().isOk()));

        // wrong password is rejected
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "wrong-password"))))
                .andExpect(status().isUnauthorized());

        String accessToken = loginBody.get("accessToken").asString();
        String refreshToken = loginBody.get("refreshToken").asString();

        // /me requires the access token
        JsonNode meBody = readBody(mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk()));
        assertThat(meBody.get("email").asString()).isEqualTo(email);

        // /me without a token is unauthorized
        mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());

        // refresh rotates the refresh token
        JsonNode refreshedBody = readBody(mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isOk()));
        String newRefreshToken = refreshedBody.get("refreshToken").asString();
        assertThat(newRefreshToken).isNotEqualTo(refreshToken);

        // the old refresh token can no longer be used
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isUnauthorized());

        // logout revokes the current refresh token
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + refreshedBody.get("accessToken").asString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(newRefreshToken))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(newRefreshToken))))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode readBody(org.springframework.test.web.servlet.ResultActions resultActions) throws Exception {
        String content = resultActions.andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(content);
    }
}
