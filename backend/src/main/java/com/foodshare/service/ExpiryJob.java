package com.foodshare.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

// Runs automatically every minute and CLEANS UP old listings.
// The API methods (claim / complete / cancel) check all rules by themselves,
// so the app is correct even between two runs of this job.
//
// This class has NO @Transactional on purpose. It only calls ExpiryService, which is a different bean, so every call
// goes through Spring's transaction proxy and gets its own short transaction (see the note in ExpiryService).
@Component
public class ExpiryJob {

    static final int BATCH_SIZE = 200;        // claims released per transaction
    static final int MAX_BATCHES_PER_RUN = 100;   // safety stop (20,000 claims per run); the rest waits for the next run

    private final ExpiryService expiryService;

    public ExpiryJob(ExpiryService expiryService) {
        this.expiryService = expiryService;
    }

    // The real entry point used by the scheduler
    @Scheduled(fixedDelay = 60000)
    public void run() {
        cleanUp(Instant.now());
    }

    // "now" is a parameter so the tests can check the exact boundary moments.
    public void cleanUp(Instant now) {
        // 1. expire first (see ExpiryService.markExpired)
        expiryService.markExpired(now);

        // 2. then release missed pickups, one batch (= one transaction) at a time
        for (int i = 0; i < MAX_BATCHES_PER_RUN; i++) {
            if (expiryService.releaseAbandonedBatch(now, BATCH_SIZE) < BATCH_SIZE) break;
        }
    }
}
