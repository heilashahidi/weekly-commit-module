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
 * Pure unit coverage of carry-forward (U8) — no Spring context; repositories and
 * principal mocked. The real {@link LifecycleService} is wired in (over the same
 * mocks) so {@code carry} composes over the genuine
 * {@link LifecycleService#getOrCreatePlan} get-or-create rather than a stub.
 *
 * <p>Proves AE7 (PARTIAL + NOT_DONE candidates, DROPPED excluded), that DONE /
 * UNRECONCILED / unstatused / unplanned are never candidates, that carry seeds the
 * next week's DRAFT with lineage + incremented week-count (incl. two-week rollover),
 * IC-selected only (R13), the RECONCILED gate (409), invalid-id rejection (422), and
 * ownership (403).
 */
@ExtendWith(MockitoExtension.class)
class CarryForwardServiceTest {

    private static final String OWNER = "auth0|owner";
    private static final Instant NOW = Instant.parse("2026-06-03T12:00:00Z");
    private static final UUID RCDO = UUID.fromString("44444444-4444-4444-4444-444444444441");

    @Mock
    CommitmentRepository commitmentRepository;

    @Mock
    WeeklyPlanRepository planRepository;

    @Mock
    PrincipalResolver principalResolver;

    private CarryForwardService service() {
        LifecycleService lifecycle =
            new LifecycleService(
                planRepository,
                commitmentRepository,
                principalResolver,
                new MutableClock(NOW, ZoneOffset.UTC));
        return new CarryForwardService(
            commitmentRepository, planRepository, principalResolver, lifecycle);
    }

    private WeeklyPlan plan(UUID id, String owner, PlanStatus status, String weekKey) {
        WeeklyPlan p = new WeeklyPlan();
        p.setId(id);
        p.setOwner(owner);
        p.setWeekKey(weekKey);
        p.setStatus(status);
        return p;
    }

    private Commitment commitment(
            UUID planId, boolean planned, ReconciliationStatus status, int carryWeekCount) {
        Commitment c = new Commitment();
        c.setId(UUID.randomUUID());
        c.setWeeklyPlanId(planId);
        c.setRcdoNodeId(RCDO);
        c.setTitle("Work");
        c.setPlanned(planned);
        c.setReconciliationStatus(status);
        c.setCarryWeekCount(carryWeekCount);
        return c;
    }

    // --- listCandidates (AE7, R12, R15) ---

    @Test
    void candidatesIncludePartialAndNotDoneButNotDropped() {
        UUID planId = UUID.randomUUID();
        Commitment partial = commitment(planId, true, ReconciliationStatus.PARTIAL, 0);
        Commitment notDone = commitment(planId, true, ReconciliationStatus.NOT_DONE, 0);
        Commitment dropped = commitment(planId, true, ReconciliationStatus.DROPPED, 0);
        when(principalResolver.currentPrincipal()).thenReturn(OWNER);
        when(planRepository.findById(planId))
            .thenReturn(Optional.of(plan(planId, OWNER, PlanStatus.RECONCILED, "2026-W23")));
        when(commitmentRepository.findByWeeklyPlanIdAndPlanned(planId, true))
            .thenReturn(List.of(partial, notDone, dropped));

        List<CarryCandidateDto> candidates = service().listCandidates(planId);

        assertThat(candidates).extracting(CarryCandidateDto::id)
            .containsExactlyInAnyOrder(partial.getId(), notDone.getId())
            .doesNotContain(dropped.getId());
    }

    @Test
    void candidatesExcludeDoneUnreconciledUnstatusedAndUnplanned() {
        UUID planId = UUID.randomUUID();
        Commitment done = commitment(planId, true, ReconciliationStatus.DONE, 0);
        Commitment unrec = commitment(planId, true, ReconciliationStatus.UNRECONCILED, 0);
        Commitment unstatused = commitment(planId, true, null, 0);
        // Unplanned commitments are never returned by findByWeeklyPlanIdAndPlanned(planId, true).
        when(principalResolver.currentPrincipal()).thenReturn(OWNER);
        when(planRepository.findById(planId))
            .thenReturn(Optional.of(plan(planId, OWNER, PlanStatus.RECONCILED, "2026-W23")));
        when(commitmentRepository.findByWeeklyPlanIdAndPlanned(planId, true))
            .thenReturn(List.of(done, unrec, unstatused));

        assertThat(service().listCandidates(planId)).isEmpty();
    }

    @Test
    void listCandidatesOnNonReconciledPlanRejectedWithConflict() {
        UUID planId = UUID.randomUUID();
        when(principalResolver.currentPrincipal()).thenReturn(OWNER);
        when(planRepository.findById(planId))
            .thenReturn(Optional.of(plan(planId, OWNER, PlanStatus.RECONCILING, "2026-W23")));

        assertThatThrownBy(() -> service().listCandidates(planId))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT));
    }

    // --- carry (R12, R13, R14) ---

    @Test
    void carrySeedsNextWeekDraftWithLineageAndIncrementedWeekCount() {
        UUID planId = UUID.randomUUID();
        UUID nextPlanId = UUID.randomUUID();
        Commitment partial = commitment(planId, true, ReconciliationStatus.PARTIAL, 0);
        when(principalResolver.currentPrincipal()).thenReturn(OWNER);
        when(planRepository.findById(planId))
            .thenReturn(Optional.of(plan(planId, OWNER, PlanStatus.RECONCILED, "2026-W23")));
        when(commitmentRepository.findByWeeklyPlanIdAndPlanned(planId, true))
            .thenReturn(List.of(partial));
        // get-or-create next week (2026-W24) returns an existing DRAFT.
        when(planRepository.findByOwnerAndWeekKey(OWNER, "2026-W24"))
            .thenReturn(Optional.of(plan(nextPlanId, OWNER, PlanStatus.DRAFT, "2026-W24")));
        when(commitmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<CommitmentDto> carried = service().carry(planId, List.of(partial.getId()));

        assertThat(carried).hasSize(1);
        CommitmentDto c = carried.get(0);
        assertThat(c.weeklyPlanId()).isEqualTo(nextPlanId);
        assertThat(c.planned()).isTrue();
        assertThat(c.rcdoNodeId()).isEqualTo(RCDO);
        assertThat(c.carriedFromId()).isEqualTo(partial.getId());
        assertThat(c.carryWeekCount()).isEqualTo(1);
        assertThat(c.reconciliationStatus()).isNull();
    }

    @Test
    void carryFromAlreadyCarriedCommitmentReachesWeekCountTwo() {
        UUID planId = UUID.randomUUID();
        UUID nextPlanId = UUID.randomUUID();
        // Source already rolled over once (carryWeekCount = 1).
        Commitment once = commitment(planId, true, ReconciliationStatus.NOT_DONE, 1);
        when(principalResolver.currentPrincipal()).thenReturn(OWNER);
        when(planRepository.findById(planId))
            .thenReturn(Optional.of(plan(planId, OWNER, PlanStatus.RECONCILED, "2026-W23")));
        when(commitmentRepository.findByWeeklyPlanIdAndPlanned(planId, true))
            .thenReturn(List.of(once));
        when(planRepository.findByOwnerAndWeekKey(OWNER, "2026-W24"))
            .thenReturn(Optional.of(plan(nextPlanId, OWNER, PlanStatus.DRAFT, "2026-W24")));
        when(commitmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<CommitmentDto> carried = service().carry(planId, List.of(once.getId()));

        assertThat(carried).singleElement()
            .satisfies(c -> assertThat(c.carryWeekCount()).isEqualTo(2));
    }

    @Test
    void onlyIcSelectedCandidatesAreCarried() {
        UUID planId = UUID.randomUUID();
        UUID nextPlanId = UUID.randomUUID();
        Commitment partial = commitment(planId, true, ReconciliationStatus.PARTIAL, 0);
        Commitment notDone = commitment(planId, true, ReconciliationStatus.NOT_DONE, 0);
        when(principalResolver.currentPrincipal()).thenReturn(OWNER);
        when(planRepository.findById(planId))
            .thenReturn(Optional.of(plan(planId, OWNER, PlanStatus.RECONCILED, "2026-W23")));
        when(commitmentRepository.findByWeeklyPlanIdAndPlanned(planId, true))
            .thenReturn(List.of(partial, notDone));
        when(planRepository.findByOwnerAndWeekKey(OWNER, "2026-W24"))
            .thenReturn(Optional.of(plan(nextPlanId, OWNER, PlanStatus.DRAFT, "2026-W24")));
        when(commitmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Select only one of the two candidates (R13).
        List<CommitmentDto> carried = service().carry(planId, List.of(partial.getId()));

        assertThat(carried).hasSize(1);
        assertThat(carried.get(0).carriedFromId()).isEqualTo(partial.getId());
        // Exactly one commitment saved — the unselected candidate was not carried.
        verify(commitmentRepository).save(any());
    }

    @Test
    void carryGetsOrCreatesNextWeekDraftWhenNoneExists() {
        UUID planId = UUID.randomUUID();
        Commitment partial = commitment(planId, true, ReconciliationStatus.PARTIAL, 0);
        when(principalResolver.currentPrincipal()).thenReturn(OWNER);
        when(planRepository.findById(planId))
            .thenReturn(Optional.of(plan(planId, OWNER, PlanStatus.RECONCILED, "2026-W23")));
        when(commitmentRepository.findByWeeklyPlanIdAndPlanned(planId, true))
            .thenReturn(List.of(partial));
        // No next-week plan exists yet -> createDraft saves one and returns it.
        when(planRepository.findByOwnerAndWeekKey(OWNER, "2026-W24")).thenReturn(Optional.empty());
        UUID createdPlanId = UUID.randomUUID();
        when(planRepository.save(any(WeeklyPlan.class))).thenAnswer(inv -> {
            WeeklyPlan p = inv.getArgument(0);
            p.setId(createdPlanId);
            return p;
        });
        when(commitmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<CommitmentDto> carried = service().carry(planId, List.of(partial.getId()));

        assertThat(carried).singleElement()
            .satisfies(c -> assertThat(c.weeklyPlanId()).isEqualTo(createdPlanId));
        // A new DRAFT plan for next week was persisted.
        verify(planRepository).save(any(WeeklyPlan.class));
    }

    @Test
    void carryFromNonReconciledPlanRejectedWithConflict() {
        UUID planId = UUID.randomUUID();
        when(principalResolver.currentPrincipal()).thenReturn(OWNER);
        when(planRepository.findById(planId))
            .thenReturn(Optional.of(plan(planId, OWNER, PlanStatus.RECONCILING, "2026-W23")));

        assertThatThrownBy(() -> service().carry(planId, List.of(UUID.randomUUID())))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT));
        verify(commitmentRepository, never()).save(any());
    }

    @Test
    void carryingAnIdThatIsNotAValidCandidateRejected() {
        UUID planId = UUID.randomUUID();
        Commitment partial = commitment(planId, true, ReconciliationStatus.PARTIAL, 0);
        Commitment dropped = commitment(planId, true, ReconciliationStatus.DROPPED, 0);
        when(principalResolver.currentPrincipal()).thenReturn(OWNER);
        when(planRepository.findById(planId))
            .thenReturn(Optional.of(plan(planId, OWNER, PlanStatus.RECONCILED, "2026-W23")));
        when(commitmentRepository.findByWeeklyPlanIdAndPlanned(planId, true))
            .thenReturn(List.of(partial, dropped));

        // The DROPPED commitment is not a candidate -> selecting it is a 422.
        assertThatThrownBy(() -> service().carry(planId, List.of(dropped.getId())))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
        verify(commitmentRepository, never()).save(any());
        // No next-week draft was created either.
        verify(planRepository, never()).save(any(WeeklyPlan.class));
    }

    // --- ownership ---

    @Test
    void listCandidatesOnAnotherOwnersPlanRejectedWithForbidden() {
        UUID planId = UUID.randomUUID();
        when(principalResolver.currentPrincipal()).thenReturn("auth0|intruder");
        when(planRepository.findById(planId))
            .thenReturn(Optional.of(plan(planId, OWNER, PlanStatus.RECONCILED, "2026-W23")));

        assertThatThrownBy(() -> service().listCandidates(planId))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void carryOnAnotherOwnersPlanRejectedWithForbidden() {
        UUID planId = UUID.randomUUID();
        when(principalResolver.currentPrincipal()).thenReturn("auth0|intruder");
        when(planRepository.findById(planId))
            .thenReturn(Optional.of(plan(planId, OWNER, PlanStatus.RECONCILED, "2026-W23")));

        assertThatThrownBy(() -> service().carry(planId, List.of(UUID.randomUUID())))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN));
        verify(commitmentRepository, never()).save(any());
    }

    @Test
    void carryWithNullSelectionCarriesNothing() {
        UUID planId = UUID.randomUUID();
        Commitment partial = commitment(planId, true, ReconciliationStatus.PARTIAL, 0);
        when(principalResolver.currentPrincipal()).thenReturn(OWNER);
        when(planRepository.findById(planId))
            .thenReturn(Optional.of(plan(planId, OWNER, PlanStatus.RECONCILED, "2026-W23")));
        when(commitmentRepository.findByWeeklyPlanIdAndPlanned(planId, true))
            .thenReturn(List.of(partial));
        when(planRepository.findByOwnerAndWeekKey(OWNER, "2026-W24"))
            .thenReturn(Optional.of(plan(UUID.randomUUID(), OWNER, PlanStatus.DRAFT, "2026-W24")));

        assertThat(service().carry(planId, null)).isEmpty();
        verify(commitmentRepository, never()).save(any());
    }
}
