package com.oskott.dogtrainerbackend.training;

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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TrainingCatalogControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void catalogIsSeededAndBrowsableCategoryToActivityToExercise() throws Exception {
        String accessToken = registerAndGetAccessToken("trainer-" + System.nanoTime() + "@example.com");

        JsonNode categories = readBody(mockMvc.perform(get("/api/v1/training/categories")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk()));
        assertThat(categories.isArray()).isTrue();
        assertThat(categories).isNotEmpty();
        assertThat(categories.get(0).get("name").asString()).isNotBlank();

        String firstCategoryId = categories.get(0).get("id").asString();
        JsonNode activities = readBody(mockMvc.perform(get("/api/v1/training/categories/" + firstCategoryId + "/activities")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk()));
        assertThat(activities).isNotEmpty();

        String firstActivityId = activities.get(0).get("id").asString();
        JsonNode exercises = readBody(mockMvc.perform(get("/api/v1/training/activities/" + firstActivityId + "/exercises")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk()));
        assertThat(exercises).isNotEmpty();
        assertThat(exercises.get(0).get("difficulty").asString()).isIn("BEGINNER", "INTERMEDIATE", "ADVANCED");

        // unknown category/activity ids yield 404, not empty lists
        mockMvc.perform(get("/api/v1/training/categories/" + UUID.randomUUID() + "/activities")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/training/activities/" + UUID.randomUUID() + "/exercises")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
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
