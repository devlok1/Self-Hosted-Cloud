package com.homecloud.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TransferCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(TransferCleanupScheduler.class);

    private final TransferService transferService;
    private final FileService fileService;

    public TransferCleanupScheduler(TransferService transferService, FileService fileService) {
        this.transferService = transferService;
        this.fileService = fileService;
    }

    @Scheduled(cron = "${homecloud.transfers.cleanup-cron:0 0 * * * *}")
    public void cleanupExpiredTransfersAndPendingDeletions() {
        log.info("Running scheduled cleanup for expired transfers and pending deletions...");
        try {
            transferService.cleanupExpiredSessions();
        } catch (Exception e) {
            log.error("Error during scheduled transfer cleanup", e);
        }

        try {
            int purged = fileService.purgePendingDeletions();
            if (purged > 0) {
                log.info("Purged {} stuck pending file deletion(s)", purged);
            }
        } catch (Exception e) {
            log.error("Error during scheduled pending deletion purge", e);
        }
    }
}
