package com.weeklycommit.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weeklycommit.config.PrincipalResolver;
import com.weeklycommit.lifecycle.support.MutableClock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Pure unit coverage of the lifecycle state machine — no Spring context,
 * repositories/principal/clock mocked. Proves get-or-create idempotence, the legal
 * forward transitions with their provenance (USER_LOCKED, no_plan — R4/R5), the
 * transition table rejecting skips and backward moves (409), and ownership (403).
 */
@ExtendWith(MockitoExtension.class)
class LifecycleServiceTest {

    private static final String OWNER = "auth0|owner";
    // 2026-06-03T12:00:00Z is ISO week 2026-W23.
    private static final Instant NOW = Instant.parse("2026-06-03T12:00:00Z");
    private static final String WEEK_KEY = "2026-W23";

    @Mock
    WeeklyPlanRepository planRepository;

    @Mock
    CommitmentRepository commitmentRepository;

    @Mock
    PrincipalResolver principalResolver;

    private LifecycleService service() {
        return new LifecycleService(
            planRepository,
            commitmentRepository,
            principalResolver,
            new MutableClock(NOW, ZoneOffset.UTC));
    }

    private WeeklyPlan plan(UUID id, String owner, PlanStatus status) {
        WeeklyPlan p = new WeeklyPlan();
        p.setId(id);
        p.setOwner(owner);
        p.setWeekKey(WEEK_KEY);
        p.setStatus(status);
        return p;
    }

    private Commitment commitment() {
        Commitment c = new Commitment();
        c.setId(UUID.randomUUID());
        return c;
    }

    // --- get-or-create ---

    @Test
    void getOrCreateCreatesDraftWhenAbsent() {
        when(principalResolver.currentPrincipal()).thenReturn(OWNER);
        when(planRepository.findByOwnerAndWeekKey(OWNER, WEEK_KEY)).thenReturn(Optional.empty());
        when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(commitmentRepository.findByWeeklyPlanId(any())).thenReturn(List.of());

        WeeklyPlanDto dto = service().getOrCreateCurrentPlan();

        assertThat(dto.status()).isEqualTo(PlanStatus.DRAFT);
        assertThat(dto.owner()).isEqualTo(OWNER);
        assertThat(dto.weekKey()).isEqualTo(WEEK_KEY);
        assertThat(dto.statusDeadline()).isEqualTo(NOW.plus(LifecycleService.LOCK_OFFSET));
        verify(planRepository).save(any());
    }

    @Test
    void getOrCreateReturnsExistingWithoutSaving() {
        WeeklyPlan existing = plan(UUID.randomUUID(), OWNER, PlanStatus.DRAFT);
        when(principalResolver.currentPrincipal()).thenReturn(OWNER);
        when(planRepository.findByOwnerAndWeekKey(OWNER, WEEK_KEY)).thenReturn(Optional.of(existing));
        when(commitmentRepository.findByWeeklyPlanId(existing.getId())).thenReturn(List.of());

        WeeklyPlanDto dto = service().getOrCreateCurrentPlan();

        assertThat(dto.id()).isEqualTo(existing.getId());
        verify(planRepository, never()).save(any());
    }

    // --- lock provenance (R4, R5) ---

    @Test
    void lockSetsLockedAndUserLocked() {
        UUID id = UUID.randomUUID();
        when(principalResolver.currentPrincipal()).thenReturn(OWNER);
        when(planRepository.findById(id)).thenReturn(Optional.of(plan(id, OWNER, PlanStatus.DRAFT)));
        when(commitmentRepository.findByWeeklyPlanId(id)).thenReturn(List.of(commitment()));
        when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WeeklyPlanDto dto = service().lock(id);

        assertThat(dto.status()).isEqualTo(PlanStatus.LOCKED);
        assertThat(dto.lockType()).isEqualTo(LockType.USER_LOCKED);
        assertThat(dto.noPlan()).isFalse();
        assertThat(dto.statusDeadline()).isEqualTo(NOW.plus(LifecycleService.RECONCILE_START_OFFSET));
    }

    @Test
    void lockEmptyPlanSetsNoPlanTrueAndStillLocks() {
        UUID id = UUID.randomUUID();
        when(principalResolver.currentPrincipal()).thenReturn(OWNER);
        when(planRepository.findById(id)).thenReturn(Optional.of(plan(id, OWNER, PlanStatus.DRAFT)));
        when(commitmentRepository.findByWeeklyPlanId(id)).thenReturn(List.of());
        when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WeeklyPlanDto dto = service().lock(id);

        assertThat(dto.status()).isEqualTo(PlanStatus.LOCKED);
        assertThat(dto.lockType()).isEqualTo(LockType.USER_LOCKED);
        assertThat(dto.noPlan()).isTrue();
    }

    // --- legal forward transitions ---

    @Test
    void startReconcilingAdvancesLockedToReconciling() {
        UUID id = UUID.randomUUID();
        when(principalResolver.currentPrincipal()).thenReturn(OWNER);
        when(planRepository.findById(id)).thenReturn(Optional.of(plan(id, OWNER, PlanStatus.LOCKED)));
        when(commitmentRepository.findByWeeklyPlanId(id)).thenReturn(List.of());
        when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WeeklyPlanDto dto = service().startReconciling(id);

        assertThat(dto.status()).isEqualTo(PlanStatus.RECONCILING);
        assertThat(dto.statusDeadline()).isEqualTo(NOW.plus(LifecycleService.RECONCILE_CLOSE_OFFSET));
    }

    @Test
    void submitReconciledAdvancesReconcilingToReconciledAndClearsDeadline() {
        UUID id = UUID.randomUUID();
        when(principalResolver.currentPrincipal()).thenReturn(OWNER);
        when(planRepository.findById(id))
            .thenReturn(Optional.of(plan(id, OWNER, PlanStatus.RECONCILING)));
        when(commitmentRepository.findByWeeklyPlanId(id)).thenReturn(List.of());
        when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WeeklyPlanDto dto = service().submitReconciled(id);

        assertThat(dto.status()).isEqualTo(PlanStatus.RECONCILED);
        assertThat(dto.statusDeadline()).isNull();
    }

    // --- illegal transitions (409) ---

    @Test
    void skipAheadDraftToReconciledRejected() {
        UUID id = UUID.randomUUID();
        when(principalResolver.currentPrincipal()).thenReturn(OWNER);
        when(planRepository.findById(id)).thenReturn(Optional.of(plan(id, OWNER, PlanStatus.DRAFT)));

        // submitReconciled targets RECONCILED, illegal from DRAFT.
        assertThatThrownBy(() -> service().submitReconciled(id))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT));
        verify(planRepository, never()).save(any());
    }

    @Test
    void backwardReconcilingToDraftRejected() {
        // lock() targets LOCKED; from RECONCILING that is a backward move.
        UUID id = UUID.randomUUID();
        when(principalResolver.currentPrincipal()).thenReturn(OWNER);
        when(planRepository.findById(id))
            .thenReturn(Optional.of(plan(id, OWNER, PlanStatus.RECONCILING)));

        assertThatThrownBy(() -> service().lock(id))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT));
        verify(planRepository, never()).save(any());
    }

    @Test
    void reLockingAlreadyLockedRejected() {
        UUID id = UUID.randomUUID();
        when(principalResolver.currentPrincipal()).thenReturn(OWNER);
        when(planRepository.findById(id)).thenReturn(Optional.of(plan(id, OWNER, PlanStatus.LOCKED)));

        assertThatThrownBy(() -> service().lock(id))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT));
    }

    // --- ownership / not found ---

    @Test
    void transitionOnAnotherOwnersPlanRejectedWithForbidden() {
        UUID id = UUID.randomUUID();
        when(principalResolver.currentPrincipal()).thenReturn("auth0|intruder");
        when(planRepository.findById(id)).thenReturn(Optional.of(plan(id, OWNER, PlanStatus.DRAFT)));

        assertThatThrownBy(() -> service().lock(id))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN));
        verify(planRepository, never()).save(any());
    }

    @Test
    void transitionOnMissingPlanRejectedWithNotFound() {
        UUID id = UUID.randomUUID();
        when(planRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().lock(id))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }
}
