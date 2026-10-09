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

        // exercises can still be added to a completed session (e.g. logging something forgotten)
        mockMvc.perform(post("/api/v1/training-sessions/" + sessionId + "/exercises")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exerciseId\":\"" + exerciseId + "\",\"repetitions\":1,\"successfulRepetitions\":1}"))
                .andExpect(status().isCreated());

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

        // cannot add exercises to a cancelled session
        mockMvc.perform(post("/api/v1/training-sessions/" + secondSessionId + "/exercises")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exerciseId\":\"" + exerciseId + "\",\"repetitions\":1,\"successfulRepetitions\":1}"))
                .andExpect(status().isConflict());

        // dog's session list now has both sessions
        JsonNode allSessions = readBody(mockMvc.perform(get("/api/v1/dogs/" + dogId + "/training-sessions")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()));
        assertThat(allSessions).hasSize(2);
    }

    @Test
    void manualSessionCreationGoesStraightToCompletedAndRejectsFutureOrOtherUsersDog() throws Exception {
        String ownerToken = registerAndGetAccessToken("manual-owner-" + System.nanoTime() + "@example.com");
        String otherToken = registerAndGetAccessToken("manual-other-" + System.nanoTime() + "@example.com");
        String dogId = createDog(ownerToken, "Rex");

        String startedAt = java.time.Instant.now().minus(java.time.Duration.ofHours(2)).toString();
        JsonNode session = readBody(mockMvc.perform(post("/api/v1/dogs/" + dogId + "/training-sessions/manual")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startedAt\":\"" + startedAt + "\",\"durationMinutes\":45,"
                                + "\"location\":\"Park\",\"notes\":\"Forgot to track this one\"}"))
                .andExpect(status().isCreated()));
        assertThat(session.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(session.get("durationMinutes").asInt()).isEqualTo(45);
        assertThat(session.get("completedAt").isNull()).isFalse();
        assertThat(session.get("location").asString()).isEqualTo("Park");

        // a session whose startedAt is valid (past) but whose computed completedAt (startedAt +
        // duration) would land in the future is rejected
        String almostNow = java.time.Instant.now().minus(java.time.Duration.ofMinutes(10)).toString();
        mockMvc.perform(post("/api/v1/dogs/" + dogId + "/training-sessions/manual")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startedAt\":\"" + almostNow + "\",\"durationMinutes\":60}"))
                .andExpect(status().isConflict());

        // another user cannot log a manual session against someone else's dog
        mockMvc.perform(post("/api/v1/dogs/" + dogId + "/training-sessions/manual")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startedAt\":\"" + startedAt + "\",\"durationMinutes\":30}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteSessionRequiresCompletedStatusAndIsOwnershipScoped() throws Exception {
        String ownerToken = registerAndGetAccessToken("delete-owner-" + System.nanoTime() + "@example.com");
        String otherToken = registerAndGetAccessToken("delete-other-" + System.nanoTime() + "@example.com");
        String dogId = createDog(ownerToken, "Fido");

        // IN_PROGRESS session cannot be deleted
        JsonNode inProgress = readBody(mockMvc.perform(post("/api/v1/dogs/" + dogId + "/training-sessions")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated()));
        String inProgressId = inProgress.get("id").asString();
        mockMvc.perform(delete("/api/v1/training-sessions/" + inProgressId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isConflict());

        // CANCELLED session cannot be deleted either
        mockMvc.perform(post("/api/v1/training-sessions/" + inProgressId + "/cancel")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/training-sessions/" + inProgressId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isConflict());

        // a completed session, shared as a post, can be deleted - the post survives, unlinked
        JsonNode completedSession = readBody(mockMvc.perform(post("/api/v1/dogs/" + dogId + "/training-sessions/manual")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startedAt\":\"" + java.time.Instant.now().minus(java.time.Duration.ofHours(1)) + "\","
                                + "\"durationMinutes\":30}"))
                .andExpect(status().isCreated()));
        String completedSessionId = completedSession.get("id").asString();

        JsonNode post = readBody(mockMvc.perform(post("/api/v1/posts/from-session/" + completedSessionId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated()));
        String postId = post.get("id").asString();
        assertThat(post.get("trainingSessionId").asString()).isEqualTo(completedSessionId);

        // another user cannot delete it
        mockMvc.perform(delete("/api/v1/training-sessions/" + completedSessionId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/training-sessions/" + completedSessionId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/training-sessions/" + completedSessionId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());

        // the post is still there, just unlinked from the now-deleted session
        JsonNode survivingPost = readBody(mockMvc.perform(get("/api/v1/posts/" + postId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()));
        assertThat(survivingPost.get("trainingSessionId").isNull()).isTrue();

        // deleting an already-deleted session is a 404, not another conflict
        mockMvc.perform(delete("/api/v1/training-sessions/" + completedSessionId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
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
