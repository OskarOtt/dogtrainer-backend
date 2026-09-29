package com.oskott.dogtrainerbackend.title;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DogTitleControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void fullTitleLifecycleWorksAndIsOwnershipScoped() throws Exception {
        String ownerToken = registerAndGetAccessToken("title-owner-" + System.nanoTime() + "@example.com");
        String otherToken = registerAndGetAccessToken("title-other-" + System.nanoTime() + "@example.com");
        String dogId = createDog(ownerToken, "Rex");

        JsonNode emptyTitles = readBody(mockMvc.perform(get("/api/v1/dogs/" + dogId + "/titles")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()));
        assertThat(emptyTitles).isEmpty();

        // another user cannot list or create titles for a dog they don't own
        mockMvc.perform(get("/api/v1/dogs/" + dogId + "/titles")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());

        JsonNode created = readBody(mockMvc.perform(post("/api/v1/dogs/" + dogId + "/titles")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"CH\",\"dateEarned\":\"2024-05-01\"}"))
                .andExpect(status().isCreated()));
        String titleId = created.get("id").asString();
        assertThat(created.get("title").asString()).isEqualTo("CH");
        assertThat(created.get("dateEarned").asString()).isEqualTo("2024-05-01");

        // another user cannot update or delete this title
        mockMvc.perform(put("/api/v1/titles/" + titleId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hacked\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/titles/" + titleId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());

        JsonNode updated = readBody(mockMvc.perform(put("/api/v1/titles/" + titleId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"CH updated\"}"))
                .andExpect(status().isOk()));
        assertThat(updated.get("title").asString()).isEqualTo("CH updated");
        assertThat(updated.get("dateEarned").isNull()).isTrue();

        JsonNode listed = readBody(mockMvc.perform(get("/api/v1/dogs/" + dogId + "/titles")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()));
        assertThat(listed).hasSize(1);

        mockMvc.perform(delete("/api/v1/titles/" + titleId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        JsonNode afterDelete = readBody(mockMvc.perform(get("/api/v1/dogs/" + dogId + "/titles")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()));
        assertThat(afterDelete).isEmpty();
    }

    private String createDog(String accessToken, String name) throws Exception {
        JsonNode dog = readBody(mockMvc.perform(post("/api/v1/dogs")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()));
        return dog.get("id").asString();
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
