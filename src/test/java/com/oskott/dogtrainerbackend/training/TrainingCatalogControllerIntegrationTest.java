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
import java.util.List;

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

    @Test
    void norwegianLocalesUseBokmalAndUnsupportedLocaleFallsBackToEnglish() throws Exception {
        String accessToken = registerAndGetAccessToken("locale-" + System.nanoTime() + "@example.com");

        for (String locale : List.of("nb-NO", "no", "nn-NO")) {
            JsonNode categories = readBody(mockMvc.perform(get("/api/v1/training/categories")
                            .header("Authorization", "Bearer " + accessToken)
                            .header("Accept-Language", locale))
                    .andExpect(status().isOk()));
            assertThat(categories).hasSize(8);
            assertThat(categories.get(0).get("id").asString())
                    .isEqualTo("8aadad5d-94a0-4f77-ac3d-2bf62124f89f");
            assertThat(categories.get(0).get("name").asString()).isEqualTo("Lydighetsprøver");
        }

        JsonNode english = readBody(mockMvc.perform(get("/api/v1/training/categories")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Accept-Language", "de-DE"))
                .andExpect(status().isOk()));
        assertThat(english.get(0).get("name").asString()).isEqualTo("Obedience Trials");
    }

    @Test
    void exactObedienceTrialNamesDescriptionsAndContractsAreLocalized() throws Exception {
        String accessToken = registerAndGetAccessToken("trials-" + System.nanoTime() + "@example.com");
        String categoryId = "8aadad5d-94a0-4f77-ac3d-2bf62124f89f";
        JsonNode activities = readBody(mockMvc.perform(get("/api/v1/training/categories/" + categoryId + "/activities")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Accept-Language", "nb"))
                .andExpect(status().isOk()));
        assertThat(activities).hasSize(4);
        assertThat(activities.get(0).get("name").asString()).isEqualTo("Klasse 1");

        JsonNode exercises = readBody(mockMvc.perform(get("/api/v1/training/activities/"
                                + activities.get(0).get("id").asString() + "/exercises")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Accept-Language", "nb"))
                .andExpect(status().isOk()));
        assertThat(exercises).hasSize(9);
        assertThat(exercises.get(0).get("name").asString()).isEqualTo("Tilgjengelighet");
        assertThat(exercises.get(0).get("description").asString()).isEqualTo("Koeffisient: 2");
        assertThat(exercises.get(0).has("id")).isTrue();
        assertThat(exercises.get(0).has("activityId")).isTrue();
        assertThat(exercises.get(0).has("difficulty")).isTrue();
        assertThat(exercises.get(0).has("instructions")).isTrue();
    }

    @Test
    void missingNorwegianTranslationFallsBackToEnglishPerEntity() throws Exception {
        String accessToken = registerAndGetAccessToken("fallback-" + System.nanoTime() + "@example.com");
        String translationId = "4578216c-676f-4431-ad50-52655f1986ec";
        org.springframework.jdbc.core.JdbcTemplate jdbcTemplate =
                new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        jdbcTemplate.update("delete from exercise_translations where exercise_id = ?", UUID.fromString(translationId));
        try {
            JsonNode exercise = readBody(mockMvc.perform(get("/api/v1/training/exercises/" + translationId)
                            .header("Authorization", "Bearer " + accessToken)
                            .header("Accept-Language", "nb"))
                    .andExpect(status().isOk()));
            assertThat(exercise.get("name").asString()).isEqualTo("Accessibility");
        } finally {
            jdbcTemplate.update("""
                    insert into exercise_translations (id, exercise_id, locale, name, description)
                    values (?, ?, 'nb', 'Tilgjengelighet', 'Koeffisient: 2')
                    """, UUID.fromString(translationId), UUID.fromString(translationId));
        }
    }

    @Autowired
    private javax.sql.DataSource dataSource;

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
