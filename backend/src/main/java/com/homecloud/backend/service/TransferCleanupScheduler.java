package com.homecloud.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TransferCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(TransferCleanupScheduler.class);

    private final TransferService transferService;

    public TransferCleanupScheduler(TransferService transferService) {
        this.transferService = transferService;
    }

    @Scheduled(cron = "${homecloud.transfers.cleanup-cron:0 0 * * * *}")
    public void cleanupExpiredTransfers() {
        log.info("Running scheduled cleanup for expired and stuck transfer sessions...");
        try {
            transferService.cleanupExpiredSessions();
        } catch (Exception e) {
            log.error("Error during scheduled transfer cleanup", e);
        }
    }
}
