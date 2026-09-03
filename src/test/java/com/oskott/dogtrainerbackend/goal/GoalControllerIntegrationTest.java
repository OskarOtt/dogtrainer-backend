package com.oskott.dogtrainerbackend.goal;

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
class GoalControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void fullGoalLifecycleWorksAndIsOwnershipScoped() throws Exception {
        String ownerToken = registerAndGetAccessToken("goal-owner-" + System.nanoTime() + "@example.com");
        String otherToken = registerAndGetAccessToken("goal-other-" + System.nanoTime() + "@example.com");
        String dogId = createDog(ownerToken, "Rex");

        JsonNode emptyGoals = readBody(mockMvc.perform(get("/api/v1/dogs/" + dogId + "/goals")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()));
        assertThat(emptyGoals).isEmpty();

        JsonNode created = readBody(mockMvc.perform(post("/api/v1/dogs/" + dogId + "/goals")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Master recall\",\"description\":\"Reliable off-leash recall\",\"targetDate\":\"2026-01-01\"}"))
                .andExpect(status().isCreated()));
        String goalId = created.get("id").asString();
        assertThat(created.get("status").asString()).isEqualTo("NOT_STARTED");

        // another user cannot see or act on this goal
        mockMvc.perform(get("/api/v1/goals/" + goalId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());

        JsonNode updated = readBody(mockMvc.perform(put("/api/v1/goals/" + goalId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Master recall\",\"description\":\"Updated\",\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk()));
        assertThat(updated.get("status").asString()).isEqualTo("IN_PROGRESS");

        JsonNode listed = readBody(mockMvc.perform(get("/api/v1/dogs/" + dogId + "/goals")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()));
        assertThat(listed).hasSize(1);

        mockMvc.perform(delete("/api/v1/goals/" + goalId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        JsonNode afterDelete = readBody(mockMvc.perform(get("/api/v1/dogs/" + dogId + "/goals")
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
