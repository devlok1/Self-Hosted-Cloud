package com.homecloud.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.homecloud.backend.dto.InitiateTransferRequest;
import com.homecloud.backend.dto.LoginRequest;
import com.homecloud.backend.dto.RegisterRequest;
import com.homecloud.backend.repository.FileRepository;
import com.homecloud.backend.repository.FolderRepository;
import com.homecloud.backend.repository.UploadChunkRepository;
import com.homecloud.backend.repository.UploadSessionRepository;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class TransferControllerIntegrationTest {

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

    @Autowired
    private UploadSessionRepository uploadSessionRepository;

    @Autowired
    private UploadChunkRepository uploadChunkRepository;

    private String userAToken;
    private String userBToken;

    @BeforeEach
    void setUp() throws Exception {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        uploadChunkRepository.deleteAll();
        uploadSessionRepository.deleteAll();
        fileRepository.deleteAll();
        folderRepository.deleteAll();
        userRepository.deleteAll();

        userAToken = registerAndLogin("userA_transfer@example.com", "passwordA123");
        userBToken = registerAndLogin("userB_transfer@example.com", "passwordB123");
    }

    private String registerAndLogin(String email, String password) throws Exception {
        RegisterRequest registerReq = new RegisterRequest(email, password);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        LoginRequest loginReq = new LoginRequest(email, password);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    @DisplayName("End-to-end resumable upload with multiple chunks and final verification")
    void testEndToEndResumableUpload() throws Exception {
        byte[] chunk0Bytes = "First chunk part 1".getBytes(StandardCharsets.UTF_8); // 18 bytes
        byte[] chunk1Bytes = "Second chunk part".getBytes(StandardCharsets.UTF_8); // 17 bytes
        long totalSize = chunk0Bytes.length + chunk1Bytes.length; // 35 bytes
        long chunkSize = chunk0Bytes.length; // 18 bytes

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String chunk0Sha = HexFormat.of().formatHex(digest.digest(chunk0Bytes));
        digest.reset();
        String chunk1Sha = HexFormat.of().formatHex(digest.digest(chunk1Bytes));
        digest.reset();
        byte[] totalBytes = "First chunk part 1Second chunk part".getBytes(StandardCharsets.UTF_8);
        String finalSha = HexFormat.of().formatHex(digest.digest(totalBytes));

        // 1. Initiate transfer
        InitiateTransferRequest initReq = new InitiateTransferRequest(
                "video.mp4", totalSize, chunkSize, null, "video/mp4", finalSha
        );

        MvcResult initResult = mockMvc.perform(post("/api/files/transfers/initiate")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uploadId").isNotEmpty())
                .andExpect(jsonPath("$.filename").value("video.mp4"))
                .andExpect(jsonPath("$.fileSize").value(totalSize))
                .andExpect(jsonPath("$.totalChunks").value(2))
                .andExpect(jsonPath("$.status").value("INITIATED"))
                .andReturn();

        String uploadId = objectMapper.readTree(initResult.getResponse().getContentAsString()).get("uploadId").asText();

        // 2. Upload chunk 0
        MockMultipartFile chunk0File = new MockMultipartFile("chunk", "chunk_0.part", "application/octet-stream", chunk0Bytes);
        mockMvc.perform(multipart("/api/files/transfers/" + uploadId + "/chunks/0")
                        .file(chunk0File)
                        .with(req -> { req.setMethod("PUT"); return req; })
                        .header("Authorization", "Bearer " + userAToken)
                        .header("X-Chunk-Checksum", chunk0Sha))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chunkNumber").value(0))
                .andExpect(jsonPath("$.uploadedChunksCount").value(1))
                .andExpect(jsonPath("$.status").value("UPLOADING"));

        // 3. Duplicate upload of chunk 0 should be idempotent
        mockMvc.perform(multipart("/api/files/transfers/" + uploadId + "/chunks/0")
                        .file(chunk0File)
                        .with(req -> { req.setMethod("PUT"); return req; })
                        .header("Authorization", "Bearer " + userAToken)
                        .header("X-Chunk-Checksum", chunk0Sha))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chunkNumber").value(0));

        // 4. Query status (resume query)
        mockMvc.perform(get("/api/files/transfers/" + uploadId + "/status")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadedChunks", hasItem(0)))
                .andExpect(jsonPath("$.uploadedChunksCount").value(1))
                .andExpect(jsonPath("$.status").value("UPLOADING"));

        // 5. Premature complete call should fail with 400
        mockMvc.perform(post("/api/files/transfers/" + uploadId + "/complete")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Missing chunks")));

        // 6. Upload chunk 1
        MockMultipartFile chunk1File = new MockMultipartFile("chunk", "chunk_1.part", "application/octet-stream", chunk1Bytes);
        mockMvc.perform(multipart("/api/files/transfers/" + uploadId + "/chunks/1")
                        .file(chunk1File)
                        .with(req -> { req.setMethod("PUT"); return req; })
                        .header("Authorization", "Bearer " + userAToken)
                        .header("X-Chunk-Checksum", chunk1Sha))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chunkNumber").value(1))
                .andExpect(jsonPath("$.uploadedChunksCount").value(2));

        // 7. Complete transfer
        MvcResult completeResult = mockMvc.perform(post("/api/files/transfers/" + uploadId + "/complete")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.filename").value("video.mp4"))
                .andExpect(jsonPath("$.size").value(totalSize))
                .andReturn();

        long fileId = objectMapper.readTree(completeResult.getResponse().getContentAsString()).get("id").asLong();

        // 8. Verify file is in user list and can be downloaded
        mockMvc.perform(get("/api/files/" + fileId + "/download")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(content().bytes(totalBytes));
    }

    @Test
    @DisplayName("User B cannot upload chunks or complete user A's transfer session")
    void testTransferSessionIsolationBetweenUsers() throws Exception {
        InitiateTransferRequest initReq = new InitiateTransferRequest(
                "secret.doc", 1000L, 1000L, null, "text/plain", null
        );

        MvcResult initResult = mockMvc.perform(post("/api/files/transfers/initiate")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String uploadId = objectMapper.readTree(initResult.getResponse().getContentAsString()).get("uploadId").asText();

        // User B tries to upload chunk to User A's session -> should return 404
        MockMultipartFile chunkFile = new MockMultipartFile("chunk", "chunk.part", "text/plain", new byte[1000]);
        mockMvc.perform(multipart("/api/files/transfers/" + uploadId + "/chunks/0")
                        .file(chunkFile)
                        .with(req -> { req.setMethod("PUT"); return req; })
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isNotFound());

        // User B tries to cancel User A's session -> should return 404
        mockMvc.perform(delete("/api/files/transfers/" + uploadId)
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Upload cancellation purges session and prevents further chunk uploads")
    void testCancelTransferSession() throws Exception {
        InitiateTransferRequest initReq = new InitiateTransferRequest(
                "abort.txt", 2000L, 1000L, null, "text/plain", null
        );

        MvcResult initResult = mockMvc.perform(post("/api/files/transfers/initiate")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String uploadId = objectMapper.readTree(initResult.getResponse().getContentAsString()).get("uploadId").asText();

        // Cancel session
        mockMvc.perform(delete("/api/files/transfers/" + uploadId)
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("cancelled")));

        // Attempting to upload to cancelled session should return 409 Conflict
        MockMultipartFile chunkFile = new MockMultipartFile("chunk", "chunk.part", "text/plain", new byte[1000]);
        mockMvc.perform(multipart("/api/files/transfers/" + uploadId + "/chunks/0")
                        .file(chunkFile)
                        .with(req -> { req.setMethod("PUT"); return req; })
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isConflict());
    }
}
