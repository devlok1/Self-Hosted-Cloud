package com.homecloud.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.homecloud.backend.dto.LoginRequest;
import com.homecloud.backend.dto.RegisterRequest;
import com.homecloud.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class AuthControllerIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Register via JSON request body succeeds and returns UserResponse DTO")
    void register_jsonPayload_success() throws Exception {
        RegisterRequest request = new RegisterRequest("john@example.com", "securePass123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email", is("john@example.com")))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("Register via Form urlencoded succeeds for backwards compatibility")
    void register_formPayload_success() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .param("email", "formuser@example.com")
                        .param("password", "securePass123"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email", is("formuser@example.com")));
    }

    @Test
    @DisplayName("Register with invalid email returns 400 Bad Request with validation errors")
    void register_invalidEmail_returnsBadRequest() throws Exception {
        RegisterRequest request = new RegisterRequest("not-an-email", "validPassword123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Validation Error")))
                .andExpect(jsonPath("$.validationErrors.email").exists());
    }

    @Test
    @DisplayName("Login via JSON returns JWT token")
    void login_jsonPayload_success() throws Exception {
        // Register first
        RegisterRequest reg = new RegisterRequest("loginuser@example.com", "mypassword123");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());

        // Login
        LoginRequest login = new LoginRequest("loginuser@example.com", "mypassword123");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.email", is("loginuser@example.com")))
                .andExpect(jsonPath("$.message", is("Login successful")));
    }

    @Test
    @DisplayName("GET /api/auth/me returns current user profile when authenticated")
    void getCurrentUser_authenticated() throws Exception {
        // Register & login
        RegisterRequest reg = new RegisterRequest("profile@example.com", "mypassword123");
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reg)));

        LoginRequest login = new LoginRequest("profile@example.com", "mypassword123");
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();

        // Call /api/auth/me with Bearer token
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("profile@example.com")))
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    @DisplayName("GET /api/auth/me returns 401 Unauthorized without token")
    void getCurrentUser_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("Unauthorized")));
    }
}
