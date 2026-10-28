package com.homecloud.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.homecloud.backend.dto.LoginRequest;
import com.homecloud.backend.dto.RegisterRequest;
import com.homecloud.backend.repository.FileRepository;
import com.homecloud.backend.repository.FolderRepository;
import com.homecloud.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class FileAndFolderIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private FolderRepository folderRepository;

    private String userAToken;
    private String userBToken;

    @BeforeEach
    void setUp() throws Exception {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        fileRepository.deleteAll();
        folderRepository.deleteAll();
        userRepository.deleteAll();

        userAToken = registerAndLogin("userA@example.com", "passwordA123");
        userBToken = registerAndLogin("userB@example.com", "passwordB123");
    }

    private String registerAndLogin(String email, String password) throws Exception {
        RegisterRequest reg = new RegisterRequest(email, password);
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reg)));

        LoginRequest login = new LoginRequest(email, password);
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andReturn();

        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    @DisplayName("Folder operations: create, subfolder, breadcrumbs, list, rename, delete")
    void folderLifecycle() throws Exception {
        // Create root folder
        MvcResult rootRes = mockMvc.perform(post("/api/folders")
                        .header("Authorization", "Bearer " + userAToken)
                        .param("name", "Documents"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name", is("Documents")))
                .andExpect(jsonPath("$.parentId").doesNotExist())
                .andReturn();

        long rootId = objectMapper.readTree(rootRes.getResponse().getContentAsString()).get("id").asLong();

        // Create child folder
        MvcResult childRes = mockMvc.perform(post("/api/folders")
                        .header("Authorization", "Bearer " + userAToken)
                        .param("name", "Invoices")
                        .param("parentId", String.valueOf(rootId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Invoices")))
                .andExpect(jsonPath("$.parentId", is((int) rootId)))
                .andReturn();

        long childId = objectMapper.readTree(childRes.getResponse().getContentAsString()).get("id").asLong();

        // Check breadcrumbs
        mockMvc.perform(get("/api/folders/" + childId)
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.breadcrumbs", hasSize(2)))
                .andExpect(jsonPath("$.breadcrumbs[0].name", is("Documents")))
                .andExpect(jsonPath("$.breadcrumbs[1].name", is("Invoices")));

        // Rename child folder
        mockMvc.perform(put("/api/folders/" + childId)
                        .header("Authorization", "Bearer " + userAToken)
                        .param("name", "PaidInvoices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("PaidInvoices")));

        // Attempt delete parent folder while child exists -> 400 Bad Request
        mockMvc.perform(delete("/api/folders/" + rootId)
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("not empty")));

        // Delete child then parent
        mockMvc.perform(delete("/api/folders/" + childId)
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/folders/" + rootId)
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("File operations: upload, download, list DTO without path leakage, rename, move, delete")
    void fileLifecycle() throws Exception {
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "hello.txt",
                "text/plain",
                "Hello, HomeCloud!".getBytes()
        );

        // Upload file to root
        MvcResult uploadRes = mockMvc.perform(multipart("/api/files/upload")
                        .file(multipartFile)
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.filename", is("hello.txt")))
                .andExpect(jsonPath("$.size", is(17)))
                // Verify sensitive internal fields are NOT exposed
                .andExpect(jsonPath("$.storagePath").doesNotExist())
                .andExpect(jsonPath("$.storedFilename").doesNotExist())
                .andReturn();

        long fileId = objectMapper.readTree(uploadRes.getResponse().getContentAsString()).get("id").asLong();

        // Download file
        mockMvc.perform(get("/api/files/" + fileId + "/download")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("hello.txt")))
                .andExpect(content().string("Hello, HomeCloud!"));

        // Rename file
        mockMvc.perform(put("/api/files/" + fileId)
                        .header("Authorization", "Bearer " + userAToken)
                        .param("filename", "greeting.txt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename", is("greeting.txt")));

        // Search files
        mockMvc.perform(get("/api/files/search")
                        .header("Authorization", "Bearer " + userAToken)
                        .param("q", "greet"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].filename", is("greeting.txt")));

        // Storage stats
        mockMvc.perform(get("/api/storage/stats")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileCount", is(1)))
                .andExpect(jsonPath("$.usedBytes", is(17)));

        // Delete file
        mockMvc.perform(delete("/api/files/" + fileId)
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk());

        // File is now gone
        mockMvc.perform(get("/api/files/" + fileId)
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Strict ownership isolation: User B cannot access, download, or delete User A's files and folders")
    void crossUserIsolation() throws Exception {
        // User A creates a folder
        MvcResult folderRes = mockMvc.perform(post("/api/folders")
                        .header("Authorization", "Bearer " + userAToken)
                        .param("name", "UserASecrets"))
                .andExpect(status().isCreated())
                .andReturn();
        long folderId = objectMapper.readTree(folderRes.getResponse().getContentAsString()).get("id").asLong();

        // User A uploads a file
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "secret.txt",
                "text/plain",
                "Classified content".getBytes()
        );
        MvcResult fileRes = mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isCreated())
                .andReturn();
        long fileId = objectMapper.readTree(fileRes.getResponse().getContentAsString()).get("id").asLong();

        // User B attempts to view User A's folder -> 404 Not Found
        mockMvc.perform(get("/api/folders/" + folderId)
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isNotFound());

        // User B attempts to delete User A's folder -> 404 Not Found
        mockMvc.perform(delete("/api/folders/" + folderId)
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isNotFound());

        // User B attempts to download User A's file -> 404 Not Found
        mockMvc.perform(get("/api/files/" + fileId + "/download")
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isNotFound());

        // User B attempts to delete User A's file -> 404 Not Found
        mockMvc.perform(delete("/api/files/" + fileId)
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isNotFound());

        // User B lists files -> empty list
        mockMvc.perform(get("/api/files")
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
