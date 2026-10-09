package com.oskott.dogtrainerbackend.activity;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PhysicalActivityControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void fullPhysicalActivityFlowWorksEndToEndAndIsOwnershipScoped() throws Exception {
        String ownerToken = registerAndGetAccessToken("activity-owner-" + System.nanoTime() + "@example.com");
        String otherToken = registerAndGetAccessToken("activity-other-" + System.nanoTime() + "@example.com");
        String dogId = createDog(ownerToken, "Buddy");

        // no activities yet
        JsonNode emptyActivities = readBody(mockMvc.perform(get("/api/v1/dogs/" + dogId + "/physical-activities")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()));
        assertThat(emptyActivities).isEmpty();

        // start an activity with a default title
        JsonNode activity = readBody(mockMvc.perform(post("/api/v1/physical-activities")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dogIds\":[\"" + dogId + "\"],\"activityType\":\"WALK\"}"))
                .andExpect(status().isCreated()));
        String activityId = activity.get("id").asString();
        assertThat(activity.get("status").asString()).isEqualTo("IN_PROGRESS");
        assertThat(activity.get("activityType").asString()).isEqualTo("WALK");
        assertThat(activity.get("title").asString()).isEqualTo("Walk");
        assertThat(activity.get("dogIds")).hasSize(1);
        assertThat(activity.get("dogIds").get(0).asString()).isEqualTo(dogId);

        // another user cannot see or act on this activity
        mockMvc.perform(get("/api/v1/physical-activities/" + activityId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());

        // update title/notes
        JsonNode updated = readBody(mockMvc.perform(put("/api/v1/physical-activities/" + activityId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Evening walk\",\"notes\":\"Sunny out\"}"))
                .andExpect(status().isOk()));
        assertThat(updated.get("title").asString()).isEqualTo("Evening walk");
        assertThat(updated.get("notes").asString()).isEqualTo("Sunny out");

        // pause, then resume
        JsonNode paused = readBody(mockMvc.perform(post("/api/v1/physical-activities/" + activityId + "/pause")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()));
        assertThat(paused.get("status").asString()).isEqualTo("PAUSED");
        assertThat(paused.get("pausedAt").isNull()).isFalse();

        // cannot pause an already-paused activity
        mockMvc.perform(post("/api/v1/physical-activities/" + activityId + "/pause")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isConflict());

        JsonNode resumed = readBody(mockMvc.perform(post("/api/v1/physical-activities/" + activityId + "/resume")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()));
        assertThat(resumed.get("status").asString()).isEqualTo("IN_PROGRESS");
        assertThat(resumed.get("pausedAt").isNull()).isTrue();

        // cannot resume an activity that isn't paused
        mockMvc.perform(post("/api/v1/physical-activities/" + activityId + "/resume")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isConflict());

        // complete it
        JsonNode completed = readBody(mockMvc.perform(post("/api/v1/physical-activities/" + activityId + "/complete")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()));
        assertThat(completed.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(completed.get("completedAt").isNull()).isFalse();
        assertThat(completed.get("durationMinutes").asInt()).isGreaterThanOrEqualTo(1);

        // cannot complete an already-completed activity again
        mockMvc.perform(post("/api/v1/physical-activities/" + activityId + "/complete")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isConflict());

        // a second activity can be cancelled
        JsonNode secondActivity = readBody(mockMvc.perform(post("/api/v1/physical-activities")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dogIds\":[\"" + dogId + "\"],\"activityType\":\"RUN\",\"title\":\"Morning run\"}"))
                .andExpect(status().isCreated()));
        String secondActivityId = secondActivity.get("id").asString();
        assertThat(secondActivity.get("title").asString()).isEqualTo("Morning run");
        JsonNode cancelled = readBody(mockMvc.perform(post("/api/v1/physical-activities/" + secondActivityId + "/cancel")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()));
        assertThat(cancelled.get("status").asString()).isEqualTo("CANCELLED");

        // dog's activity list now has both activities
        JsonNode allActivities = readBody(mockMvc.perform(get("/api/v1/dogs/" + dogId + "/physical-activities")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()));
        assertThat(allActivities).hasSize(2);
    }

    @Test
    void activityCanBeLoggedAgainstMultipleDogsAndShowsUpInEachDogsList() throws Exception {
        String ownerToken = registerAndGetAccessToken("multi-dog-activity-owner-" + System.nanoTime() + "@example.com");
        String otherToken = registerAndGetAccessToken("multi-dog-activity-other-" + System.nanoTime() + "@example.com");
        String firstDogId = createDog(ownerToken, "Rex");
        String secondDogId = createDog(ownerToken, "Fido");
        String otherUsersDogId = createDog(otherToken, "NotYours");

        JsonNode activity = readBody(mockMvc.perform(post("/api/v1/physical-activities")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dogIds\":[\"" + firstDogId + "\",\"" + secondDogId + "\"],\"activityType\":\"HIKE\"}"))
                .andExpect(status().isCreated()));
        assertThat(activity.get("dogIds")).hasSize(2);

        JsonNode firstDogActivities = readBody(mockMvc.perform(get("/api/v1/dogs/" + firstDogId + "/physical-activities")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()));
        assertThat(firstDogActivities).hasSize(1);

        JsonNode secondDogActivities = readBody(mockMvc.perform(get("/api/v1/dogs/" + secondDogId + "/physical-activities")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()));
        assertThat(secondDogActivities).hasSize(1);

        // can't create an activity that includes a dog you don't own
        mockMvc.perform(post("/api/v1/physical-activities")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dogIds\":[\"" + firstDogId + "\",\"" + otherUsersDogId + "\"],\"activityType\":\"HIKE\"}"))
                .andExpect(status().isForbidden());

        // a request with no dogs at all is rejected
        mockMvc.perform(post("/api/v1/physical-activities")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dogIds\":[],\"activityType\":\"HIKE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void manualActivityCreationGoesStraightToCompletedAndRejectsFutureOrOtherUsersDog() throws Exception {
        String ownerToken = registerAndGetAccessToken("manual-activity-owner-" + System.nanoTime() + "@example.com");
        String otherToken = registerAndGetAccessToken("manual-activity-other-" + System.nanoTime() + "@example.com");
        String dogId = createDog(ownerToken, "Rex");
        String otherUsersDogId = createDog(otherToken, "NotYours");

        String startedAt = java.time.Instant.now().minus(java.time.Duration.ofHours(1)).toString();
        JsonNode activity = readBody(mockMvc.perform(post("/api/v1/physical-activities/manual")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dogIds\":[\"" + dogId + "\"],\"activityType\":\"RUN\",\"startedAt\":\"" + startedAt
                                + "\",\"durationMinutes\":40,\"notes\":\"Forgot to track this one\"}"))
                .andExpect(status().isCreated()));
        assertThat(activity.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(activity.get("durationMinutes").asInt()).isEqualTo(40);
        assertThat(activity.get("title").asString()).isEqualTo("Run");
        assertThat(activity.get("completedAt").isNull()).isFalse();

        // an activity whose startedAt is valid (past) but whose computed completedAt (startedAt +
        // duration) would land in the future is rejected
        String almostNow = java.time.Instant.now().minus(java.time.Duration.ofMinutes(10)).toString();
        mockMvc.perform(post("/api/v1/physical-activities/manual")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dogIds\":[\"" + dogId + "\"],\"activityType\":\"WALK\",\"startedAt\":\"" + almostNow
                                + "\",\"durationMinutes\":60}"))
                .andExpect(status().isConflict());

        // another user cannot log a manual activity against someone else's dog
        mockMvc.perform(post("/api/v1/physical-activities/manual")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dogIds\":[\"" + dogId + "\"],\"activityType\":\"WALK\",\"startedAt\":\"" + startedAt
                                + "\",\"durationMinutes\":30}"))
                .andExpect(status().isForbidden());

        // sanity check the "someone else's dog" assertion above isn't vacuous
        assertThat(otherUsersDogId).isNotEqualTo(dogId);
    }

    @Test
    void legacySingleDogEndpointsAndResponseFieldsStillWorkForOldAppVersions() throws Exception {
        String ownerToken = registerAndGetAccessToken("legacy-activity-owner-" + System.nanoTime() + "@example.com");
        String dogId = createDog(ownerToken, "Rex");

        JsonNode activity = readBody(mockMvc.perform(post("/api/v1/dogs/" + dogId + "/physical-activities")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityType\":\"WALK\"}"))
                .andExpect(status().isCreated()));
        assertThat(activity.get("dogId").asString()).isEqualTo(dogId);
        assertThat(activity.get("dogIds").get(0).asString()).isEqualTo(dogId);

        String startedAt = java.time.Instant.now().minus(java.time.Duration.ofHours(1)).toString();
        JsonNode manual = readBody(mockMvc.perform(post("/api/v1/dogs/" + dogId + "/physical-activities/manual")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityType\":\"RUN\",\"startedAt\":\"" + startedAt + "\",\"durationMinutes\":40}"))
                .andExpect(status().isCreated()));
        assertThat(manual.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(manual.get("dogId").asString()).isEqualTo(dogId);
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
