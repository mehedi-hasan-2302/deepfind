package com.deepfind.index;

import com.deepfind.config.DeepFindReconciliationProperties;
import com.deepfind.jobs.IndexingJobService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public final class ReconciliationScheduler {

    private final IndexingJobService jobs;
    private final IndexWatchCoordinator watches;
    private final DeepFindReconciliationProperties properties;
    private final Clock clock;
    private Instant lastStartedAt = Instant.EPOCH;

    @Autowired
    public ReconciliationScheduler(
            IndexingJobService jobs, IndexWatchCoordinator watches, DeepFindReconciliationProperties properties) {
        this(jobs, watches, properties, Clock.systemUTC());
    }

    ReconciliationScheduler(
            IndexingJobService jobs,
            IndexWatchCoordinator watches,
            DeepFindReconciliationProperties properties,
            Clock clock) {
        this.jobs = Objects.requireNonNull(jobs, "jobs must not be null");
        this.watches = Objects.requireNonNull(watches, "watches must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Scheduled(
            initialDelayString = "${deepfind.reconciliation.initial-delay:30s}",
            fixedDelayString = "${deepfind.reconciliation.poll-interval:30s}")
    synchronized void poll() {
        Instant now = clock.instant();
        IndexWatchStatus watchStatus = watches.status();
        boolean uncertain = watchStatus.state() == IndexWatchState.RECONCILIATION_REQUIRED;
        boolean intervalElapsed = Duration.between(lastStartedAt, now).compareTo(properties.interval()) >= 0;
        if ((uncertain || intervalElapsed) && jobs.reconcileSelectedRootIfIdle()) {
            lastStartedAt = now;
        }
    }
}
