package com.oskott.dogtrainerbackend.stats;

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

@SpringBootTest
@AutoConfigureMockMvc
class DogStatisticsControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void statisticsAndProgressReflectCompletedSessionsAndAreOwnershipScoped() throws Exception {
        String ownerToken = registerAndGetAccessToken("stats-owner-" + System.nanoTime() + "@example.com");
        String otherToken = registerAndGetAccessToken("stats-other-" + System.nanoTime() + "@example.com");
        String dogId = createDog(ownerToken, "Nova");

        String exerciseId = discoverExerciseId(ownerToken);

        JsonNode session = readBody(mockMvc.perform(post("/api/v1/dogs/" + dogId + "/training-sessions")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated()));
        String sessionId = session.get("id").asString();

        mockMvc.perform(post("/api/v1/training-sessions/" + sessionId + "/exercises")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exerciseId\":\"" + exerciseId + "\",\"repetitions\":10,\"successfulRepetitions\":5}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/training-sessions/" + sessionId + "/complete")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        JsonNode statistics = readBody(mockMvc.perform(get("/api/v1/dogs/" + dogId + "/statistics")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()));
        assertThat(statistics.get("totalSessions").asInt()).isEqualTo(1);
        assertThat(statistics.get("completedSessions").asInt()).isEqualTo(1);
        assertThat(statistics.get("sessionsThisWeek").asInt()).isEqualTo(1);
        assertThat(statistics.get("currentStreakWeeks").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(statistics.get("averageSuccessRate").asDouble()).isEqualTo(0.5);

        JsonNode progress = readBody(mockMvc.perform(get("/api/v1/dogs/" + dogId + "/progress")
                        .header("Authorization", "Bearer " + ownerToken)
                        .header("Accept-Language", "nn"))
                .andExpect(status().isOk()));
        assertThat(progress.get("history")).hasSize(1);
        assertThat(progress.get("averageSuccessRate").asDouble()).isEqualTo(0.5);
        assertThat(progress.get("exerciseProgress")).hasSize(1);
        assertThat(progress.get("exerciseProgress").get(0).get("exerciseName").asString())
                .isEqualTo("Tilgjengelighet");
        assertThat(progress.get("exerciseProgress").get(0).get("points")).hasSize(1);

        // another user cannot see this dog's statistics/progress
        mockMvc.perform(get("/api/v1/dogs/" + dogId + "/statistics")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/dogs/" + dogId + "/progress")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    private String discoverExerciseId(String accessToken) throws Exception {
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
