package com.weeklycommit.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weeklycommit.config.PrincipalResolver;
import com.weeklycommit.lifecycle.support.MutableClock;
import com.weeklycommit.manager.ReportingRepository;
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
 * Pure unit coverage of the reconciliation pass (U5) — no Spring context;
 * repositories and principal mocked. The real {@link LifecycleService} is wired in
 * (over the same mocks) so the gated submit composes over the genuine
 * {@link LifecycleService#transition} primitive rather than a stub. Proves:
 * setStatus persists status+note only while RECONCILING (R9), rejects the
 * system-only {@code UNRECONCILED} (KTD 5/R19), the submit gate blocks while any
 * commitment is unstatused (AE5/R10) and advances when all are statused, and
 * ownership (403).
 */
@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    private static final String OWNER = "auth0|owner";
    private static final Instant NOW = Instant.parse("2026-06-03T12:00:00Z");

    @Mock
    CommitmentRepository commitmentRepository;

    @Mock
    WeeklyPlanRepository planRepository;

    @Mock
    PrincipalResolver principalResolver;

    @Mock
    ReportingRepository reportingRepository;

    private ReconciliationService service() {
        OwnedPlanLoader ownedPlanLoader =
            new OwnedPlanLoader(
                planRepository, commitmentRepository, reportingRepository, principalResolver);
        LifecycleService lifecycle =
            new LifecycleService(
                planRepository,
                commitmentRepository,
                principalResolver,
                ownedPlanLoader,
                new MutableClock(NOW, ZoneOffset.UTC));
        return new ReconciliationService(
            commitmentRepository, planRepository, ownedPlanLoader, lifecycle);
    }

    private WeeklyPlan plan(UUID id, String owner, PlanStatus status) {
        WeeklyPlan p = new WeeklyPlan();
        p.setId(id);
        p.setOwner(owner);
        p.setWeekKey("2026-W23");
        p.setStatus(status);
        return p;
    }

    private Commitment commitment(UUID planId, ReconciliationStatus status) {
        Commitment c = new Commitment();
        c.setId(UUID.randomUUID());
        c.setWeeklyPlanId(planId);
        c.setReconciliationStatus(status);
        return c;
    }

    // --- setStatus (R9) ---

    @Test
    void setStatusOnReconcilingPlanPersistsStatusAndNote() {
        UUID planId = UUID.randomUUID();
        Commitment c = commitment(planId, null);
        when(principalResolver.currentPrincipal()).thenReturn(OWNER);
        when(commitmentRepository.findById(c.getId())).thenReturn(Optional.of(c));
        when(planRepository.findById(planId))
            .thenReturn(Optional.of(plan(planId, OWNER, PlanStatus.RECONCILING)));
        when(commitmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CommitmentDto dto = service().setStatus(c.getId(), ReconciliationStatus.PARTIAL, "halfway");

        assertThat(dto.reconciliationStatus()).isEqualTo(ReconciliationStatus.PARTIAL);
        assertThat(dto.reconciliationNote()).isEqualTo("halfway");
    }

    @Test
    void setStatusWithNullNoteIsAllowed() {
        UUID planId = UUID.randomUUID();
        Commitment c = commitment(planId, null);
        when(principalResolver.currentPrincipal()).thenReturn(OWNER);
        when(commitmentRepository.findById(c.getId())).thenReturn(Optional.of(c));
        when(planRepository.findById(planId))
            .thenReturn(Optional.of(plan(planId, OWNER, PlanStatus.RECONCILING)));
        when(commitmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CommitmentDto dto = service().setStatus(c.getId(), ReconciliationStatus.DONE, null);

        assertThat(dto.reconciliationStatus()).isEqualTo(ReconciliationStatus.DONE);
        assertThat(dto.reconciliationNote()).isNull();
    }

    @Test
    void setStatusOnLockedPlanRejectedWithConflict() {
        UUID planId = UUID.randomUUID();
        Commitment c = commitment(planId, null);
        when(principalResolver.currentPrincipal()).thenReturn(OWNER);
        when(commitmentRepository.findById(c.getId())).thenReturn(Optional.of(c));
        when(planRepository.findById(planId))
            .thenReturn(Optional.of(plan(planId, OWNER, PlanStatus.LOCKED)));

        assertThatThrownBy(() -> service().setStatus(c.getId(), ReconciliationStatus.DONE, null))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT));
        verify(commitmentRepository, never()).save(any());
    }

    @Test
    void setStatusUnreconciledRejectedAsSystemOnly() {
        // UNRECONCILED is system-only (KTD 5/R19) — never IC-settable.
        assertThatThrownBy(
                () -> service().setStatus(
                    UUID.randomUUID(), ReconciliationStatus.UNRECONCILED, null))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
        verify(commitmentRepository, never()).save(any());
    }

    // --- submit gate (AE5, R10) ---

    @Test
    void submitWithOneUnstatusedCommitmentRejectedAndPlanStaysReconciling() {
        UUID planId = UUID.randomUUID();
        WeeklyPlan plan = plan(planId, OWNER, PlanStatus.RECONCILING);
        when(principalResolver.currentPrincipal()).thenReturn(OWNER);
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(commitmentRepository.findByWeeklyPlanId(planId))
            .thenReturn(List.of(
                commitment(planId, ReconciliationStatus.DONE),
                commitment(planId, null)));

        assertThatThrownBy(() -> service().submit(planId))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        assertThat(plan.getStatus()).isEqualTo(PlanStatus.RECONCILING);
        verify(planRepository, never()).save(any());
    }

    @Test
    void submitWithAllStatusedAdvancesToReconciled() {
        UUID planId = UUID.randomUUID();
        WeeklyPlan plan = plan(planId, OWNER, PlanStatus.RECONCILING);
        when(principalResolver.currentPrincipal()).thenReturn(OWNER);
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(commitmentRepository.findByWeeklyPlanId(planId))
            .thenReturn(List.of(
                commitment(planId, ReconciliationStatus.DONE),
                commitment(planId, ReconciliationStatus.NOT_DONE)));
        when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WeeklyPlanDto dto = service().submit(planId);

        assertThat(dto.status()).isEqualTo(PlanStatus.RECONCILED);
        verify(planRepository).save(any());
    }

    @Test
    void submitOnNonReconcilingPlanRejectedWithConflict() {
        UUID planId = UUID.randomUUID();
        when(principalResolver.currentPrincipal()).thenReturn(OWNER);
        when(planRepository.findById(planId))
            .thenReturn(Optional.of(plan(planId, OWNER, PlanStatus.LOCKED)));

        assertThatThrownBy(() -> service().submit(planId))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT));
        verify(planRepository, never()).save(any());
    }

    // --- ownership ---

    @Test
    void submitOnAnotherOwnersPlanRejectedWithForbidden() {
        UUID planId = UUID.randomUUID();
        when(principalResolver.currentPrincipal()).thenReturn("auth0|intruder");
        when(planRepository.findById(planId))
            .thenReturn(Optional.of(plan(planId, OWNER, PlanStatus.RECONCILING)));

        assertThatThrownBy(() -> service().submit(planId))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN));
        verify(planRepository, never()).save(any());
    }

    @Test
    void setStatusOnAnotherOwnersPlanRejectedWithForbidden() {
        UUID planId = UUID.randomUUID();
        Commitment c = commitment(planId, null);
        when(principalResolver.currentPrincipal()).thenReturn("auth0|intruder");
        when(commitmentRepository.findById(c.getId())).thenReturn(Optional.of(c));
        when(planRepository.findById(planId))
            .thenReturn(Optional.of(plan(planId, OWNER, PlanStatus.RECONCILING)));

        assertThatThrownBy(() -> service().setStatus(c.getId(), ReconciliationStatus.DONE, null))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN));
        verify(commitmentRepository, never()).save(any());
    }
}
