package com.example.todo.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TodoApiControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static MediaType jsonMediaType() {
        return Objects.requireNonNull(MediaType.APPLICATION_JSON);
    }

    private static Matcher<? super String> containsText(String text) {
        return Objects.requireNonNull(containsString(text));
    }

    @SuppressWarnings("unchecked")
    private static <T> Matcher<? super Iterable<? super T>> hasItemValue(T value) {
        return (Matcher<? super Iterable<? super T>>) (Matcher<?>) Objects.requireNonNull(hasItem(value));
    }

    @BeforeEach
    void setUpUserForApi() {
        jdbcTemplate.update(
                "MERGE INTO users (id, username, password, role, enabled) KEY(id) VALUES (?, ?, ?, ?, ?)",
                1L, "api-test-user", "password", "USER", true
        );
    }

    @Test
    void createAndFindById_Returns201Then200() throws Exception {
        String requestBody = """
                {
                  "title": "integration title",
                  "author": "integration author",
                  "priority": "HIGH",
                  "categoryId": 1,
                  "deadline": "2026-12-31",
                  "completed": false
                }
                """;

        String createResponse = mockMvc.perform(post("/api/todo")
                        .contentType(jsonMediaType())
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(createResponse);
        long createdId = root.path("data").path("id").asLong();

        mockMvc.perform(get("/api/todo/{id}", createdId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(createdId));
    }

    @Test
    void findById_NotFound_Returns404Envelope() throws Exception {
        mockMvc.perform(get("/api/todo/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsText("ToDoが見つかりません")));
    }

    @Test
    void create_InvalidPayload_Returns400Envelope() throws Exception {
        String invalidBody = """
                {
                  "title": "",
                  "author": "",
                  "priority": null,
                  "categoryId": null
                }
                """;

        mockMvc.perform(post("/api/todo")
                        .contentType(jsonMediaType())
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").isMap());
    }

    @Test
    @WithMockUser(username = "api-test-user", roles = "USER")
    void listCalendarEvents_ReturnsDeadlineEventsWithColors() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO todos (title, author, user_id, priority, category_id, deadline, completed) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "未完了タスク", "tester", 1L, "MEDIUM", 1L, "2099-12-31", false
        );
        jdbcTemplate.update(
                "INSERT INTO todos (title, author, user_id, priority, category_id, deadline, completed) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "完了タスク", "tester", 1L, "HIGH", 1L, "2099-12-30", true
        );

        mockMvc.perform(get("/api/todo/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].start").exists())
                .andExpect(jsonPath("$[0].allDay").value(true))
                .andExpect(jsonPath("$[*].url").isArray())
                .andExpect(jsonPath("$[?(@.title == '未完了タスク')].backgroundColor").value(hasItemValue("#0d6efd")))
                .andExpect(jsonPath("$[?(@.title == '完了タスク')].backgroundColor").value(hasItemValue("#198754")));
    }

    @Test
    @WithMockUser(username = "api-test-user", roles = "USER")
    void getProgressSummary_ReturnsOverallAndCategoryRates() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO todos (title, author, user_id, priority, category_id, deadline, completed) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "カテゴリ1完了", "tester", 1L, "LOW", 1L, "2099-12-31", true
        );
        jdbcTemplate.update(
                "INSERT INTO todos (title, author, user_id, priority, category_id, deadline, completed) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "カテゴリ1未完了", "tester", 1L, "MEDIUM", 1L, "2099-12-31", false
        );
        jdbcTemplate.update(
                "INSERT INTO todos (title, author, user_id, priority, category_id, deadline, completed) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "カテゴリ2未完了", "tester", 1L, "HIGH", 2L, "2099-12-31", false
        );

        mockMvc.perform(get("/api/todo/progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.completedCount").value(1))
                .andExpect(jsonPath("$.completionRate").value(33))
                .andExpect(jsonPath("$.barColor").value("#ffc107"))
                .andExpect(jsonPath("$.categories[?(@.categoryId == 1)].completionRate").value(hasItemValue(50)))
                .andExpect(jsonPath("$.categories[?(@.categoryId == 2)].completionRate").value(hasItemValue(0)));
    }

    @Test
    @WithMockUser(username = "api-test-user", roles = "USER")
    void getCategoryStats_ReturnsGroupedCounts() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO todos (title, author, user_id, priority, category_id, deadline, completed) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "c1-1", "tester", 1L, "LOW", 1L, "2099-12-31", false
        );
        jdbcTemplate.update(
                "INSERT INTO todos (title, author, user_id, priority, category_id, deadline, completed) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "c1-2", "tester", 1L, "HIGH", 1L, "2099-12-31", true
        );
        jdbcTemplate.update(
                "INSERT INTO todos (title, author, user_id, priority, category_id, deadline, completed) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "c2-1", "tester", 1L, "MEDIUM", 2L, "2099-12-31", false
        );

        mockMvc.perform(get("/api/todo/stats/category"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.categoryId == 1)].taskCount").value(hasItemValue(2)))
                .andExpect(jsonPath("$[?(@.categoryId == 2)].taskCount").value(hasItemValue(1)));
    }

    @Test
    @WithMockUser(username = "api-test-user", roles = "USER")
    void getPriorityStats_ReturnsGroupedCounts() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO todos (title, author, user_id, priority, category_id, deadline, completed) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "p-high", "tester", 1L, "HIGH", 1L, "2099-12-31", false
        );
        jdbcTemplate.update(
                "INSERT INTO todos (title, author, user_id, priority, category_id, deadline, completed) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "p-medium", "tester", 1L, "MEDIUM", 1L, "2099-12-31", false
        );
        jdbcTemplate.update(
                "INSERT INTO todos (title, author, user_id, priority, category_id, deadline, completed) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "p-low", "tester", 1L, "LOW", 1L, "2099-12-31", false
        );
        jdbcTemplate.update(
                "INSERT INTO todos (title, author, user_id, priority, category_id, deadline, completed) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "p-low-2", "tester", 1L, "LOW", 2L, "2099-12-31", false
        );

        mockMvc.perform(get("/api/todo/stats/priority"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.priority == 'HIGH')].taskCount").value(hasItemValue(1)))
                .andExpect(jsonPath("$[?(@.priority == 'MEDIUM')].taskCount").value(hasItemValue(1)))
                .andExpect(jsonPath("$[?(@.priority == 'LOW')].taskCount").value(hasItemValue(2)));
    }
}
