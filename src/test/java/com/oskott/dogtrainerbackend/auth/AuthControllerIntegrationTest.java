package com.oskott.dogtrainerbackend.auth;

import com.oskott.dogtrainerbackend.auth.dto.LoginRequest;
import com.oskott.dogtrainerbackend.auth.dto.RefreshRequest;
import com.oskott.dogtrainerbackend.auth.dto.RegisterRequest;
import com.oskott.dogtrainerbackend.auth.entity.ExternalAuthProvider;
import com.oskott.dogtrainerbackend.auth.service.AppleTokenRevoker;
import com.oskott.dogtrainerbackend.auth.service.ExternalIdentityVerifier;
import com.oskott.dogtrainerbackend.auth.service.VerifiedExternalIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

    @MockitoBean
    private ExternalIdentityVerifier externalIdentityVerifier;

    @MockitoBean
    private AppleTokenRevoker appleTokenRevoker;

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

    @Test
    void socialLoginCreatesAndReusesLocalUser() throws Exception {
        String token = "apple-token-" + System.nanoTime();
        String email = "apple-" + System.nanoTime() + "@gmail.com";
        when(externalIdentityVerifier.verify(ExternalAuthProvider.APPLE, token))
                .thenReturn(new VerifiedExternalIdentity(
                        "apple-subject-" + System.nanoTime(),
                        email,
                        true,
                        "Apple Trainer",
                        Instant.now()
                ));

        String request = """
                {"provider":"APPLE","idToken":"%s"}
                """.formatted(token);
        JsonNode firstLogin = readBody(mockMvc.perform(post("/api/v1/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk()));
        assertThat(firstLogin.get("accessToken").asString()).isNotBlank();

        JsonNode me = readBody(mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + firstLogin.get("accessToken").asString()))
                .andExpect(status().isOk()));
        assertThat(me.get("email").asString()).isEqualTo(email);
        assertThat(me.get("name").asString()).isEqualTo("Apple Trainer");
        assertThat(me.get("authMethods").toString()).contains("APPLE");

        mockMvc.perform(post("/api/v1/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk());
    }

    @Test
    void socialLoginRequiresNameWhenProviderHasNone() throws Exception {
        String token = "nameless-token-" + System.nanoTime();
        when(externalIdentityVerifier.verify(ExternalAuthProvider.APPLE, token))
                .thenReturn(new VerifiedExternalIdentity(
                        "apple-subject-" + System.nanoTime(),
                        "private-" + System.nanoTime() + "@privaterelay.appleid.com",
                        true,
                        null,
                        Instant.now()
                ));

        JsonNode error = readBody(mockMvc.perform(post("/api/v1/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"APPLE","idToken":"%s"}
                                """.formatted(token)))
                .andExpect(status().isUnprocessableContent()));
        assertThat(error.get("code").asString()).isEqualTo("PROFILE_NAME_REQUIRED");
    }

    @Test
    void matchingEmailRequiresExplicitAuthenticatedLink() throws Exception {
        String email = "link-" + System.nanoTime() + "@gmail.com";
        JsonNode registered = readBody(mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(email, "Link Trainer", "SuperSecret123")
                        )))
                .andExpect(status().isCreated()));

        String providerToken = "link-token-" + System.nanoTime();
        when(externalIdentityVerifier.verify(eq(ExternalAuthProvider.APPLE), eq(providerToken)))
                .thenReturn(new VerifiedExternalIdentity(
                        "link-subject-" + System.nanoTime(),
                        email,
                        true,
                        "Link Trainer",
                        Instant.now()
                ));
        String request = """
                {"provider":"APPLE","idToken":"%s"}
                """.formatted(providerToken);

        JsonNode collision = readBody(mockMvc.perform(post("/api/v1/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict()));
        assertThat(collision.get("code").asString()).isEqualTo("ACCOUNT_LINK_REQUIRED");

        JsonNode methods = readBody(mockMvc.perform(post("/api/v1/auth/social/link")
                        .header("Authorization", "Bearer " + registered.get("accessToken").asString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk()));
        assertThat(methods.toString()).contains("PASSWORD", "APPLE");

        mockMvc.perform(post("/api/v1/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk());
    }

    @Test
    void socialDeletionRequiresFreshLinkedIdentity() throws Exception {
        String providerToken = "delete-token-" + System.nanoTime();
        String subject = "delete-subject-" + System.nanoTime();
        when(externalIdentityVerifier.verify(ExternalAuthProvider.APPLE, providerToken))
                .thenReturn(new VerifiedExternalIdentity(
                        subject,
                        "delete-" + System.nanoTime() + "@gmail.com",
                        true,
                        "Delete Trainer",
                        Instant.now()
                ));

        JsonNode loggedIn = readBody(mockMvc.perform(post("/api/v1/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"APPLE","idToken":"%s"}
                                """.formatted(providerToken)))
                .andExpect(status().isOk()));

        mockMvc.perform(delete("/api/v1/users/me")
                        .header("Authorization", "Bearer " + loggedIn.get("accessToken").asString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"method":"APPLE","idToken":"%s","authorizationCode":"auth-code-%s"}
                                """.formatted(providerToken, providerToken)))
                .andExpect(status().isNoContent());
    }

    @Test
    void socialDeletionRejectsStaleProviderCredential() throws Exception {
        String providerToken = "stale-delete-token-" + System.nanoTime();
        String subject = "stale-delete-subject-" + System.nanoTime();
        String email = "stale-delete-" + System.nanoTime() + "@gmail.com";
        when(externalIdentityVerifier.verify(ExternalAuthProvider.APPLE, providerToken))
                .thenReturn(
                        new VerifiedExternalIdentity(subject, email, true, "Trainer", Instant.now()),
                        new VerifiedExternalIdentity(subject, email, true, "Trainer", Instant.now().minusSeconds(600))
                );

        JsonNode loggedIn = readBody(mockMvc.perform(post("/api/v1/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"APPLE","idToken":"%s"}
                                """.formatted(providerToken)))
                .andExpect(status().isOk()));

        mockMvc.perform(delete("/api/v1/users/me")
                        .header("Authorization", "Bearer " + loggedIn.get("accessToken").asString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"method":"APPLE","idToken":"%s"}
                                """.formatted(providerToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordDeletionRequestRemainsBackwardCompatible() throws Exception {
        JsonNode registered = readBody(mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                "password-delete-" + System.nanoTime() + "@example.com",
                                "Password Trainer",
                                "SuperSecret123"
                        ))))
                .andExpect(status().isCreated()));

        mockMvc.perform(delete("/api/v1/users/me")
                        .header("Authorization", "Bearer " + registered.get("accessToken").asString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"SuperSecret123\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void appleDeletionRevokesProviderAuthorization() throws Exception {
        String providerToken = "apple-delete-token-" + System.nanoTime();
        String subject = "apple-delete-subject-" + System.nanoTime();
        when(externalIdentityVerifier.verify(ExternalAuthProvider.APPLE, providerToken))
                .thenReturn(new VerifiedExternalIdentity(
                        subject,
                        "private-" + System.nanoTime() + "@privaterelay.appleid.com",
                        true,
                        "Apple Trainer",
                        Instant.now()
                ));

        JsonNode loggedIn = readBody(mockMvc.perform(post("/api/v1/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"APPLE","idToken":"%s"}
                                """.formatted(providerToken)))
                .andExpect(status().isOk()));

        mockMvc.perform(delete("/api/v1/users/me")
                        .header("Authorization", "Bearer " + loggedIn.get("accessToken").asString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "method":"APPLE",
                                  "idToken":"%s",
                                  "authorizationCode":"fresh-authorization-code"
                                }
                                """.formatted(providerToken)))
                .andExpect(status().isNoContent());
        verify(appleTokenRevoker).revoke("fresh-authorization-code", subject);
    }

    private JsonNode readBody(org.springframework.test.web.servlet.ResultActions resultActions) throws Exception {
        String content = resultActions.andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(content);
    }
}
