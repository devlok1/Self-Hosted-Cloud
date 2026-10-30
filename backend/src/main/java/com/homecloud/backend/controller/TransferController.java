package com.homecloud.backend.controller;

import com.homecloud.backend.dto.*;
import com.homecloud.backend.entity.User;
import com.homecloud.backend.exception.UnauthorizedException;
import com.homecloud.backend.repository.UserRepository;
import com.homecloud.backend.service.TransferService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/files/transfers")
public class TransferController {

    private final TransferService transferService;
    private final UserRepository userRepository;

    public TransferController(TransferService transferService, UserRepository userRepository) {
        this.transferService = transferService;
        this.userRepository = userRepository;
    }

    @PostMapping("/initiate")
    public ResponseEntity<TransferSessionResponse> initiateTransfer(
            @RequestBody InitiateTransferRequest request,
            Authentication authentication) {

        User user = authenticatedUser(authentication);
        TransferSessionResponse response = transferService.initiateSession(user, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{uploadId}/chunks/{chunkNumber}")
    public ResponseEntity<TransferChunkResponse> uploadChunk(
            @PathVariable String uploadId,
            @PathVariable int chunkNumber,
            @RequestParam("chunk") MultipartFile chunk,
            @RequestParam(value = "checksum", required = false) String checksumParam,
            @RequestHeader(value = "X-Chunk-Checksum", required = false) String checksumHeader,
            Authentication authentication) {

        User user = authenticatedUser(authentication);
        String checksum = checksumHeader != null && !checksumHeader.isBlank() ? checksumHeader : checksumParam;
        TransferChunkResponse response = transferService.uploadChunk(uploadId, chunkNumber, chunk, checksum, user);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{uploadId}/status")
    public ResponseEntity<TransferStatusResponse> getTransferStatus(
            @PathVariable String uploadId,
            Authentication authentication) {

        User user = authenticatedUser(authentication);
        TransferStatusResponse response = transferService.getSessionStatus(uploadId, user);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{uploadId}/complete")
    public ResponseEntity<FileResponse> completeTransfer(
            @PathVariable String uploadId,
            Authentication authentication) {

        User user = authenticatedUser(authentication);
        FileResponse response = transferService.completeSession(uploadId, user);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{uploadId}")
    public ResponseEntity<Map<String, String>> cancelTransfer(
            @PathVariable String uploadId,
            Authentication authentication) {

        User user = authenticatedUser(authentication);
        transferService.cancelSession(uploadId, user);
        return ResponseEntity.ok(Map.of("message", "Transfer session cancelled and cleaned up"));
    }

    private User authenticatedUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("Authentication required");
        }
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found"));
    }
}
