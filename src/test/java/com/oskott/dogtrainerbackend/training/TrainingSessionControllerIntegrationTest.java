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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TrainingSessionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void fullTrainingSessionFlowWorksEndToEndAndIsOwnershipScoped() throws Exception {
        String ownerToken = registerAndGetAccessToken("owner-" + System.nanoTime() + "@example.com");
        String otherToken = registerAndGetAccessToken("other-" + System.nanoTime() + "@example.com");

        String dogId = createDog(ownerToken, "Buddy");

        // discover a real exercise id from the seeded catalog
        JsonNode categories = readBody(mockMvc.perform(get("/api/v1/training/categories")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()));
        String categoryId = categories.get(0).get("id").asString();
        JsonNode activities = readBody(mockMvc.perform(get("/api/v1/training/categories/" + categoryId + "/activities")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()));
        String activityId = activities.get(0).get("id").asString();
        JsonNode exercises = readBody(mockMvc.perform(get("/api/v1/training/activities/" + activityId + "/exercises")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()));
        String exerciseId = exercises.get(0).get("id").asString();

        // no sessions yet
        JsonNode emptySessions = readBody(mockMvc.perform(get("/api/v1/dogs/" + dogId + "/training-sessions")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()));
        assertThat(emptySessions).isEmpty();

        // start a session
        JsonNode session = readBody(mockMvc.perform(post("/api/v1/dogs/" + dogId + "/training-sessions")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"location\":\"Backyard\",\"notes\":\"Morning session\"}"))
                .andExpect(status().isCreated()));
        String sessionId = session.get("id").asString();
        assertThat(session.get("status").asString()).isEqualTo("IN_PROGRESS");

        // another user cannot see or act on this session
        mockMvc.perform(get("/api/v1/training-sessions/" + sessionId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());

        // add an exercise with repetitions
        JsonNode withExercise = readBody(mockMvc.perform(post("/api/v1/training-sessions/" + sessionId + "/exercises")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exerciseId\":\"" + exerciseId + "\",\"repetitions\":10,\"successfulRepetitions\":7}"))
                .andExpect(status().isCreated()));
        assertThat(withExercise.get("exercises")).hasSize(1);
        String sessionExerciseId = withExercise.get("exercises").get(0).get("id").asString();
        assertThat(withExercise.get("exercises").get(0).get("successRate").asDouble()).isEqualTo(0.7);

        // successfulRepetitions cannot exceed repetitions
        mockMvc.perform(post("/api/v1/training-sessions/" + sessionId + "/exercises")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exerciseId\":\"" + exerciseId + "\",\"repetitions\":2,\"successfulRepetitions\":5}"))
                .andExpect(status().isConflict());

        // update the recorded exercise
        JsonNode updated = readBody(mockMvc.perform(put("/api/v1/training-sessions/" + sessionId + "/exercises/" + sessionExerciseId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repetitions\":12,\"successfulRepetitions\":12,\"notes\":\"Great progress\"}"))
                .andExpect(status().isOk()));
        assertThat(updated.get("exercises").get(0).get("successRate").asDouble()).isEqualTo(1.0);

        // complete the session
        JsonNode completed = readBody(mockMvc.perform(post("/api/v1/training-sessions/" + sessionId + "/complete")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()));
        assertThat(completed.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(completed.get("completedAt").isNull()).isFalse();

        // cannot add exercises to a completed session
        mockMvc.perform(post("/api/v1/training-sessions/" + sessionId + "/exercises")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exerciseId\":\"" + exerciseId + "\",\"repetitions\":1,\"successfulRepetitions\":1}"))
                .andExpect(status().isConflict());

        // cannot complete an already-completed session again
        mockMvc.perform(post("/api/v1/training-sessions/" + sessionId + "/complete")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isConflict());

        // remove the exercise
        mockMvc.perform(delete("/api/v1/training-sessions/" + sessionId + "/exercises/" + sessionExerciseId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        // a second session can be cancelled
        JsonNode secondSession = readBody(mockMvc.perform(post("/api/v1/dogs/" + dogId + "/training-sessions")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated()));
        String secondSessionId = secondSession.get("id").asString();
        JsonNode cancelled = readBody(mockMvc.perform(post("/api/v1/training-sessions/" + secondSessionId + "/cancel")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()));
        assertThat(cancelled.get("status").asString()).isEqualTo("CANCELLED");

        // dog's session list now has both sessions
        JsonNode allSessions = readBody(mockMvc.perform(get("/api/v1/dogs/" + dogId + "/training-sessions")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()));
        assertThat(allSessions).hasSize(2);
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
