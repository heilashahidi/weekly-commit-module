package com.weeklycommit.lifecycle;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weeklycommit.lifecycle.support.MutableClock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Per-plan failure-isolation coverage of the deadline backstop orchestrator (FIX 1).
 * The job's three collaborators are mocked so a single plan's auto-advance can be
 * made to throw deterministically; the IT ({@link DeadlineBackstopJobTest}) still
 * covers the real auto-transition behavior end to end.
 *
 * <p>Proves: (a) one plan's failure does not abort the batch — the other plans in the
 * same edge still advance; (b) an exception thrown out of an advance never escapes
 * {@link DeadlineBackstopJob#sweep()} to kill the {@code @Scheduled} future.
 */
@ExtendWith(MockitoExtension.class)
class DeadlineBackstopJobUnitTest {

    private static final Instant NOW = Instant.parse("2026-06-03T12:00:00Z");

    @Mock
    WeeklyPlanRepository planRepository;

    @Mock
    LifecycleAutoAdvancer autoAdvancer;

    private DeadlineBackstopJob job() {
        return new DeadlineBackstopJob(
            planRepository, autoAdvancer, new MutableClock(NOW, ZoneOffset.UTC));
    }

    private WeeklyPlan plan(UUID id, PlanStatus status) {
        WeeklyPlan p = new WeeklyPlan();
        p.setId(id);
        p.setStatus(status);
        return p;
    }

    @Test
    void oneFailingPlanDoesNotAbortTheRestOfTheBatch() {
        UUID bad = UUID.randomUUID();
        UUID good = UUID.randomUUID();
        when(planRepository.findByStatusAndStatusDeadlineBefore(eq(PlanStatus.DRAFT), any()))
            .thenReturn(List.of(plan(bad, PlanStatus.DRAFT), plan(good, PlanStatus.DRAFT)));
        when(planRepository.findByStatusAndStatusDeadlineBefore(eq(PlanStatus.LOCKED), any()))
            .thenReturn(List.of());
        when(planRepository.findByStatusAndStatusDeadlineBefore(eq(PlanStatus.RECONCILING), any()))
            .thenReturn(List.of());
        // The first plan was concurrently advanced -> its transition is now a 409.
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Illegal transition"))
            .when(autoAdvancer).autoLock(bad);
        doNothing().when(autoAdvancer).autoLock(good);

        job().sweep();

        // The failure was isolated: the good plan was still advanced.
        verify(autoAdvancer).autoLock(bad);
        verify(autoAdvancer).autoLock(good);
    }

    @Test
    void unexpectedErrorNeverEscapesSweep() {
        when(planRepository.findByStatusAndStatusDeadlineBefore(any(), any()))
            .thenThrow(new RuntimeException("DB blew up"));

        // sweep() swallows it so the @Scheduled future is not cancelled.
        assertThatCode(() -> job().sweep()).doesNotThrowAnyException();
        verify(autoAdvancer, never()).autoLock(any());
    }
}
