package com.oskott.dogtrainerbackend.plan;

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
class TrainingPlanControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void fullTrainingPlanLifecycleWorksAndIsOwnershipScoped() throws Exception {
        String ownerToken = registerAndGetAccessToken("plan-owner-" + System.nanoTime() + "@example.com");
        String otherToken = registerAndGetAccessToken("plan-other-" + System.nanoTime() + "@example.com");
        String dogId = createDog(ownerToken, "Milo");
        String exerciseId = firstExerciseId(ownerToken);

        JsonNode emptyPlans = readBody(mockMvc.perform(get("/api/v1/dogs/" + dogId + "/training-plans")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()));
        assertThat(emptyPlans).isEmpty();

        JsonNode created = readBody(mockMvc.perform(post("/api/v1/dogs/" + dogId + "/training-plans")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"8-week obedience plan\",\"description\":\"Foundational obedience\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-02-26\",\"exerciseIds\":[\"" + exerciseId + "\"]}"))
                .andExpect(status().isCreated()));
        String planId = created.get("id").asString();
        assertThat(created.get("status").asString()).isEqualTo("NOT_STARTED");
        assertThat(created.get("exercises")).hasSize(1);
        assertThat(created.get("exercises").get(0).get("id").asString()).isEqualTo(exerciseId);

        // another user cannot see or act on this plan
        mockMvc.perform(get("/api/v1/training-plans/" + planId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());

        JsonNode updated = readBody(mockMvc.perform(put("/api/v1/training-plans/" + planId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"8-week obedience plan\",\"description\":\"Updated\",\"status\":\"IN_PROGRESS\",\"exerciseIds\":[]}"))
                .andExpect(status().isOk()));
        assertThat(updated.get("status").asString()).isEqualTo("IN_PROGRESS");
        assertThat(updated.get("exercises")).isEmpty();

        JsonNode listed = readBody(mockMvc.perform(get("/api/v1/dogs/" + dogId + "/training-plans")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()));
        assertThat(listed).hasSize(1);

        JsonNode listedAll = readBody(mockMvc.perform(get("/api/v1/training-plans")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()));
        assertThat(listedAll).hasSize(1);
        assertThat(listedAll.get(0).get("dogName").asString()).isEqualTo("Milo");

        mockMvc.perform(delete("/api/v1/training-plans/" + planId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        JsonNode afterDelete = readBody(mockMvc.perform(get("/api/v1/dogs/" + dogId + "/training-plans")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()));
        assertThat(afterDelete).isEmpty();
    }

    @Test
    void planExerciseSummariesUseRequestLocale() throws Exception {
        String token = registerAndGetAccessToken("plan-locale-" + System.nanoTime() + "@example.com");
        String dogId = createDog(token, "Milo");
        String exerciseId = "4578216c-676f-4431-ad50-52655f1986ec";

        JsonNode created = readBody(mockMvc.perform(post("/api/v1/dogs/" + dogId + "/training-plans")
                        .header("Authorization", "Bearer " + token)
                        .header("Accept-Language", "no")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Plan\",\"exerciseIds\":[\"" + exerciseId + "\"]}"))
                .andExpect(status().isCreated()));
        assertThat(created.get("exercises").get(0).get("name").asString()).isEqualTo("Tilgjengelighet");

        JsonNode english = readBody(mockMvc.perform(get("/api/v1/training-plans/" + created.get("id").asString())
                        .header("Authorization", "Bearer " + token)
                        .header("Accept-Language", "en"))
                .andExpect(status().isOk()));
        assertThat(english.get("exercises").get(0).get("name").asString()).isEqualTo("Accessibility");
    }

    private String firstExerciseId(String accessToken) throws Exception {
        JsonNode categories = readBody(mockMvc.perform(get("/api/v1/training/categories")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk()));
        String categoryId = categories.get(0).get("id").asString();
        JsonNode activities = readBody(mockMvc.perform(get("/api/v1/training/categories/" + categoryId + "/activities")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk()));
        String activityId = activities.get(0).get("id").asString();
        JsonNode exercises = readBody(mockMvc.perform(get("/api/v1/training/activities/" + activityId + "/exercises")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk()));
        return exercises.get(0).get("id").asString();
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
