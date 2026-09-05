package com.deepfind.index;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepfind.config.DeepFindReconciliationProperties;
import com.deepfind.jobs.IndexingJobService;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ReconciliationSchedulerTests {

    @Test
    void startsWhenDueAndDoesNotStartAgainBeforeTheInterval() {
        IndexingJobService jobs = mock(IndexingJobService.class);
        IndexWatchCoordinator watches = mock(IndexWatchCoordinator.class);
        when(watches.status())
                .thenReturn(new IndexWatchStatus(Path.of("C:/root"), IndexWatchState.WATCHING, "watching"));
        when(jobs.reconcileSelectedRootIfIdle()).thenReturn(true);
        ReconciliationScheduler scheduler = new ReconciliationScheduler(
                jobs,
                watches,
                new DeepFindReconciliationProperties(Duration.ofMinutes(15)),
                Clock.fixed(Instant.parse("2026-09-05T08:00:00Z"), ZoneOffset.UTC));

        scheduler.poll();
        scheduler.poll();

        verify(jobs, times(1)).reconcileSelectedRootIfIdle();
    }

    @Test
    void retriesUncertaintyUntilTheJobCanStart() {
        IndexingJobService jobs = mock(IndexingJobService.class);
        IndexWatchCoordinator watches = mock(IndexWatchCoordinator.class);
        when(watches.status())
                .thenReturn(
                        new IndexWatchStatus(Path.of("C:/root"), IndexWatchState.RECONCILIATION_REQUIRED, "uncertain"));
        when(jobs.reconcileSelectedRootIfIdle()).thenReturn(false, true);
        ReconciliationScheduler scheduler = new ReconciliationScheduler(
                jobs,
                watches,
                new DeepFindReconciliationProperties(Duration.ofDays(1)),
                Clock.fixed(Instant.parse("2026-09-05T08:00:00Z"), ZoneOffset.UTC));

        scheduler.poll();
        scheduler.poll();

        verify(jobs, times(2)).reconcileSelectedRootIfIdle();
    }
}
