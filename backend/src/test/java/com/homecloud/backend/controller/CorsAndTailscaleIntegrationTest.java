package com.homecloud.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homecloud.backend.dto.LoginRequest;
import com.homecloud.backend.dto.RegisterRequest;
import com.homecloud.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class CorsAndTailscaleIntegrationTest {

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
    @DisplayName("CORS preflight for Tailscale CGNAT IP origin returns 200 and allowed origin")
    void corsPreflight_tailscaleIp_success() throws Exception {
        mockMvc.perform(options("/api/auth/register")
                        .header(HttpHeaders.ORIGIN, "http://100.115.82.45:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://100.115.82.45:3000"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS));
    }

    @Test
    @DisplayName("CORS preflight for Tailscale MagicDNS ts.net origin returns 200 and allowed origin")
    void corsPreflight_tailscaleMagicDns_success() throws Exception {
        mockMvc.perform(options("/api/auth/register")
                        .header(HttpHeaders.ORIGIN, "https://node.tailnet.ts.net:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://node.tailnet.ts.net:3000"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    @DisplayName("CORS preflight for untrusted origin is rejected with 403 Forbidden")
    void corsPreflight_untrustedOrigin_rejected() throws Exception {
        mockMvc.perform(options("/api/auth/register")
                        .header(HttpHeaders.ORIGIN, "http://malicious-attacker.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("CORS request from untrusted origin does not receive Access-Control-Allow-Origin header")
    void corsRequest_untrustedOrigin_noAllowOriginHeader() throws Exception {
        RegisterRequest request = new RegisterRequest("evil@example.com", "password123");
        mockMvc.perform(post("/api/auth/register")
                        .header(HttpHeaders.ORIGIN, "http://malicious-attacker.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("Registration from Tailscale IP origin succeeds and includes CORS headers")
    void register_tailscaleOrigin_success() throws Exception {
        RegisterRequest request = new RegisterRequest("tailscale.user@example.com", "securePassword123");

        mockMvc.perform(post("/api/auth/register")
                        .header(HttpHeaders.ORIGIN, "http://100.85.120.4:3000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://100.85.120.4:3000"))
                .andExpect(jsonPath("$.email", is("tailscale.user@example.com")));
    }

    @Test
    @DisplayName("Login from Tailscale MagicDNS origin succeeds and returns token with CORS headers")
    void login_tailscaleOrigin_success() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("login.ts@example.com", "securePassword123");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        LoginRequest loginReq = new LoginRequest("login.ts@example.com", "securePassword123");
        mockMvc.perform(post("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://my-server.tailnet.ts.net")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://my-server.tailnet.ts.net"))
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.email", is("login.ts@example.com")));
    }

    @Test
    @DisplayName("Authenticated request with JWT from Tailscale origin succeeds with CORS headers")
    void authenticatedRequest_tailscaleOrigin_success() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("files.ts@example.com", "securePassword123");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        LoginRequest loginReq = new LoginRequest("files.ts@example.com", "securePassword123");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String token = jsonNode.get("token").asText();

        mockMvc.perform(get("/api/files")
                        .header(HttpHeaders.ORIGIN, "http://100.64.0.5:3000")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://100.64.0.5:3000"))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("CORS preflight from localhost:3000 returns 200 and allowed origin")
    void corsPreflight_localhost_success() throws Exception {
        mockMvc.perform(options("/api/auth/register")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    @DisplayName("Registration from localhost origin succeeds")
    void register_localhostOrigin_success() throws Exception {
        RegisterRequest request = new RegisterRequest("local.user@example.com", "securePassword123");

        mockMvc.perform(post("/api/auth/register")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
                .andExpect(jsonPath("$.email", is("local.user@example.com")));
    }

    @Test
    @DisplayName("CORS preflight from private LAN origin returns 200 and allowed origin")
    void corsPreflight_lanOrigin_success() throws Exception {
        mockMvc.perform(options("/api/auth/register")
                        .header(HttpHeaders.ORIGIN, "http://192.168.1.150:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://192.168.1.150:3000"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    @DisplayName("Registration from LAN origin succeeds")
    void register_lanOrigin_success() throws Exception {
        RegisterRequest request = new RegisterRequest("lan.user@example.com", "securePassword123");

        mockMvc.perform(post("/api/auth/register")
                        .header(HttpHeaders.ORIGIN, "http://192.168.1.150:3000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://192.168.1.150:3000"))
                .andExpect(jsonPath("$.email", is("lan.user@example.com")));
    }

    @Test
    @DisplayName("SecurityConfig sanitizes trailing slashes, trims whitespace, and expands portless origins")
    void securityConfig_sanitizesPatternsCorrectly() {
        String raw = " http://100.115.82.45/ , https://my-server.ts.net/ , http://localhost:3000 ";
        java.util.List<String> sanitized = com.homecloud.backend.security.SecurityConfig.parseAndSanitizeOriginPatterns(raw);

        assertTrue(sanitized.contains("http://100.115.82.45"));
        assertTrue(sanitized.contains("http://100.115.82.45:*"));
        assertTrue(sanitized.contains("https://my-server.ts.net"));
        assertTrue(sanitized.contains("https://my-server.ts.net:*"));
        assertTrue(sanitized.contains("http://localhost:3000"));
    }

    @Test
    @DisplayName("SecurityConfig prohibits unrestricted wildcard origin '*'")
    void securityConfig_prohibitsWildcardAsterisk() {
        assertThrows(IllegalArgumentException.class, () ->
                com.homecloud.backend.security.SecurityConfig.parseAndSanitizeOriginPatterns("http://localhost, *"));
    }
}
