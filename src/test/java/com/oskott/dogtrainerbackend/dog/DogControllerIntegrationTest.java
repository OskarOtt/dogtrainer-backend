package com.oskott.dogtrainerbackend.dog;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DogControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private S3Presigner s3Presigner;

    @MockitoBean
    private S3Client s3Client;

    @Test
    void dogCrudFlowIsScopedToTheOwningUser() throws Exception {
        String ownerAccessToken = registerAndGetAccessToken("owner-" + System.nanoTime() + "@example.com");
        String otherAccessToken = registerAndGetAccessToken("other-" + System.nanoTime() + "@example.com");

        // no dogs initially
        JsonNode emptyList = readBody(mockMvc.perform(get("/api/v1/dogs")
                        .header("Authorization", "Bearer " + ownerAccessToken))
                .andExpect(status().isOk()));
        assertThat(emptyList.isArray()).isTrue();
        assertThat(emptyList).isEmpty();

        String createPayload = """
                {"name":"Rex","breed":"Labrador","birthDate":"2020-01-15","sex":"MALE","weight":28.5}
                """;
        JsonNode created = readBody(mockMvc.perform(post("/api/v1/dogs")
                        .header("Authorization", "Bearer " + ownerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated()));
        assertThat(created.get("name").asString()).isEqualTo("Rex");
        String dogId = created.get("id").asString();

        // owner can fetch it
        mockMvc.perform(get("/api/v1/dogs/" + dogId)
                        .header("Authorization", "Bearer " + ownerAccessToken))
                .andExpect(status().isOk());

        // another user cannot fetch, update, or delete it
        mockMvc.perform(get("/api/v1/dogs/" + dogId)
                        .header("Authorization", "Bearer " + otherAccessToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/dogs/" + dogId)
                        .header("Authorization", "Bearer " + otherAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/dogs/" + dogId)
                        .header("Authorization", "Bearer " + otherAccessToken))
                .andExpect(status().isForbidden());

        // and it does not show up in the other user's dog list
        JsonNode otherList = readBody(mockMvc.perform(get("/api/v1/dogs")
                        .header("Authorization", "Bearer " + otherAccessToken))
                .andExpect(status().isOk()));
        assertThat(otherList).isEmpty();

        // owner can update it
        String updatePayload = """
                {"name":"Rex Updated","breed":"Labrador","birthDate":"2020-01-15","sex":"MALE","weight":30.0}
                """;
        JsonNode updated = readBody(mockMvc.perform(put("/api/v1/dogs/" + dogId)
                        .header("Authorization", "Bearer " + ownerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk()));
        assertThat(updated.get("name").asString()).isEqualTo("Rex Updated");

        // owner can delete it
        mockMvc.perform(delete("/api/v1/dogs/" + dogId)
                        .header("Authorization", "Bearer " + ownerAccessToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/dogs/" + dogId)
                        .header("Authorization", "Bearer " + ownerAccessToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void reorderDogsChangesListOrderAndIsScopedToOwner() throws Exception {
        String ownerAccessToken = registerAndGetAccessToken("owner-" + System.nanoTime() + "@example.com");
        String otherAccessToken = registerAndGetAccessToken("other-" + System.nanoTime() + "@example.com");

        String rexId = createDog(ownerAccessToken, "Rex").get("id").asString();
        String fidoId = createDog(ownerAccessToken, "Fido").get("id").asString();
        String maxId = createDog(ownerAccessToken, "Max").get("id").asString();
        String otherDogId = createDog(otherAccessToken, "Buddy").get("id").asString();

        // default order is creation order (oldest first)
        JsonNode initialList = readBody(mockMvc.perform(get("/api/v1/dogs")
                        .header("Authorization", "Bearer " + ownerAccessToken))
                .andExpect(status().isOk()));
        assertThat(idsOf(initialList)).containsExactly(rexId, fidoId, maxId);

        // reorder: Max, Rex, Fido
        JsonNode reordered = readBody(mockMvc.perform(put("/api/v1/dogs/order")
                        .header("Authorization", "Bearer " + ownerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dogIds\":[\"" + maxId + "\",\"" + rexId + "\",\"" + fidoId + "\"]}"))
                .andExpect(status().isOk()));
        assertThat(idsOf(reordered)).containsExactly(maxId, rexId, fidoId);

        JsonNode listAfterReorder = readBody(mockMvc.perform(get("/api/v1/dogs")
                        .header("Authorization", "Bearer " + ownerAccessToken))
                .andExpect(status().isOk()));
        assertThat(idsOf(listAfterReorder)).containsExactly(maxId, rexId, fidoId);

        // missing a dog id (incomplete set) is rejected
        mockMvc.perform(put("/api/v1/dogs/order")
                        .header("Authorization", "Bearer " + ownerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dogIds\":[\"" + maxId + "\",\"" + rexId + "\"]}"))
                .andExpect(status().isConflict());

        // including another user's dog id is rejected (cannot reorder dogs you don't own)
        mockMvc.perform(put("/api/v1/dogs/order")
                        .header("Authorization", "Bearer " + ownerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dogIds\":[\"" + otherDogId + "\",\"" + rexId + "\",\"" + fidoId + "\",\"" + maxId + "\"]}"))
                .andExpect(status().isConflict());

        // the other user's own dog list/order is unaffected
        JsonNode otherList = readBody(mockMvc.perform(get("/api/v1/dogs")
                        .header("Authorization", "Bearer " + otherAccessToken))
                .andExpect(status().isOk()));
        assertThat(idsOf(otherList)).containsExactly(otherDogId);
    }

    @Test
    void dogMediaUploadConfirmAndDeleteFlowIsScopedToTheOwningUser() throws Exception {
        String ownerAccessToken = registerAndGetAccessToken("media-owner-" + System.nanoTime() + "@example.com");
        String otherAccessToken = registerAndGetAccessToken("media-other-" + System.nanoTime() + "@example.com");
        String dogId = createDog(ownerAccessToken, "Rex").get("id").asString();

        PresignedPutObjectRequest presignedRequest = mock(PresignedPutObjectRequest.class);
        when(presignedRequest.url()).thenReturn(URI.create("https://dummy.r2.cloudflarestorage.com/signed-video").toURL());
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedRequest);

        // another user cannot request an upload URL for this dog
        mockMvc.perform(post("/api/v1/dogs/" + dogId + "/media/upload-url")
                        .header("Authorization", "Bearer " + otherAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"video/mp4\",\"fileSizeBytes\":2048}"))
                .andExpect(status().isForbidden());

        JsonNode uploadUrlResponse = readBody(mockMvc.perform(post("/api/v1/dogs/" + dogId + "/media/upload-url")
                        .header("Authorization", "Bearer " + ownerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"video/mp4\",\"fileSizeBytes\":2048}"))
                .andExpect(status().isOk()));
        String objectKey = uploadUrlResponse.get("objectKey").asString();
        assertThat(objectKey).startsWith("dogs/" + dogId + "/").endsWith(".mp4");

        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().build());

        // another user cannot confirm media onto this dog
        mockMvc.perform(put("/api/v1/dogs/" + dogId + "/media")
                        .header("Authorization", "Bearer " + otherAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectKey\":\"" + objectKey + "\"}"))
                .andExpect(status().isForbidden());

        JsonNode confirmed = readBody(mockMvc.perform(put("/api/v1/dogs/" + dogId + "/media")
                        .header("Authorization", "Bearer " + ownerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectKey\":\"" + objectKey + "\"}"))
                .andExpect(status().isOk()));
        assertThat(confirmed.get("mediaUrl").asString()).isEqualTo("https://dev-only-dummy.example.com/" + objectKey);
        assertThat(confirmed.get("mediaType").asString()).isEqualTo("VIDEO");

        mockMvc.perform(delete("/api/v1/dogs/" + dogId + "/media")
                        .header("Authorization", "Bearer " + otherAccessToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/dogs/" + dogId + "/media")
                        .header("Authorization", "Bearer " + ownerAccessToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void dogMediaUploadUrlRejectsUnsupportedContentType() throws Exception {
        String accessToken = registerAndGetAccessToken("media-bad-type-" + System.nanoTime() + "@example.com");
        String dogId = createDog(accessToken, "Rex").get("id").asString();

        mockMvc.perform(post("/api/v1/dogs/" + dogId + "/media/upload-url")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"application/pdf\",\"fileSizeBytes\":1024}"))
                .andExpect(status().isUnsupportedMediaType());
    }

    private JsonNode createDog(String accessToken, String name) throws Exception {
        String payload = "{\"name\":\"" + name + "\"}";
        return readBody(mockMvc.perform(post("/api/v1/dogs")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated()));
    }

    private java.util.List<String> idsOf(JsonNode dogList) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        dogList.forEach(node -> ids.add(node.get("id").asString()));
        return ids;
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
