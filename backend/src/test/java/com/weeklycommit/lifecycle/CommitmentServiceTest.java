package com.weeklycommit.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weeklycommit.config.PrincipalResolver;
import com.weeklycommit.rcdo.RcdoNodeRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Pure unit coverage of the commitment business rules — no Spring context,
 * repositories and principal resolver mocked. Proves the RCDO-link enforcement
 * (R6), planned/unplanned classification by plan status (R8), post-lock
 * immutability of planned commitments (R7), illegal add-after-lock, and ownership.
 */
@ExtendWith(MockitoExtension.class)
class CommitmentServiceTest {

    private static final String OWNER = "auth0|owner";
    private static final UUID RCDO_NODE_ID = UUID.randomUUID();

    @Mock
    CommitmentRepository commitmentRepository;

    @Mock
    WeeklyPlanRepository planRepository;

    @Mock
    RcdoNodeRepository rcdoNodeRepository;

    @Mock
    PrincipalResolver principalResolver;

    @InjectMocks
    CommitmentService service;

    private WeeklyPlan plan(UUID id, String owner, PlanStatus status) {
        WeeklyPlan p = new WeeklyPlan();
        p.setId(id);
        p.setOwner(owner);
        p.setStatus(status);
        return p;
    }

    private Commitment commitment(UUID id, UUID planId, boolean planned) {
        Commitment c = new Commitment();
        c.setId(id);
        c.setWeeklyPlanId(planId);
        c.setRcdoNodeId(RCDO_NODE_ID);
        c.setTitle("Existing");
        c.setPlanned(planned);
        return c;
    }

    private void principalIsOwner() {
        when(principalResolver.currentPrincipal()).thenReturn(OWNER);
    }

    // --- R6: RCDO link required ---

    @Test
    void createRejectsNullRcdoNodeId() {
        UUID planId = UUID.randomUUID();
        principalIsOwner();
        when(planRepository.findById(planId))
            .thenReturn(Optional.of(plan(planId, OWNER, PlanStatus.DRAFT)));

        assertThatThrownBy(() -> service.create(planId, null, "title"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(commitmentRepository, never()).save(any());
    }

    @Test
    void createRejectsRcdoNodeThatDoesNotExist() {
        UUID planId = UUID.randomUUID();
        principalIsOwner();
        when(planRepository.findById(planId))
            .thenReturn(Optional.of(plan(planId, OWNER, PlanStatus.DRAFT)));
        when(rcdoNodeRepository.existsById(RCDO_NODE_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.create(planId, RCDO_NODE_ID, "title"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(commitmentRepository, never()).save(any());
    }

    // --- R8: classification by plan status ---

    @Test
    void createOnDraftPlanFlagsPlannedTrue() {
        UUID planId = UUID.randomUUID();
        principalIsOwner();
        when(planRepository.findById(planId))
            .thenReturn(Optional.of(plan(planId, OWNER, PlanStatus.DRAFT)));
        when(rcdoNodeRepository.existsById(RCDO_NODE_ID)).thenReturn(true);
        when(commitmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CommitmentDto dto = service.create(planId, RCDO_NODE_ID, "title");

        assertThat(dto.planned()).isTrue();
        assertThat(dto.rcdoNodeId()).isEqualTo(RCDO_NODE_ID);
    }

    @Test
    void createOnLockedPlanFlagsPlannedFalse() {
        UUID planId = UUID.randomUUID();
        principalIsOwner();
        when(planRepository.findById(planId))
            .thenReturn(Optional.of(plan(planId, OWNER, PlanStatus.LOCKED)));
        when(rcdoNodeRepository.existsById(RCDO_NODE_ID)).thenReturn(true);
        when(commitmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CommitmentDto dto = service.create(planId, RCDO_NODE_ID, "title");

        assertThat(dto.planned()).isFalse();
    }

    @Test
    void createOnReconcilingPlanRejected() {
        UUID planId = UUID.randomUUID();
        principalIsOwner();
        when(planRepository.findById(planId))
            .thenReturn(Optional.of(plan(planId, OWNER, PlanStatus.RECONCILING)));

        assertThatThrownBy(() -> service.create(planId, RCDO_NODE_ID, "title"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT));

        verify(commitmentRepository, never()).save(any());
    }

    // --- R7: immutability of planned commitments once locked ---

    @Test
    void editPlannedCommitmentOnLockedPlanRejected() {
        UUID planId = UUID.randomUUID();
        UUID commitId = UUID.randomUUID();
        principalIsOwner();
        when(commitmentRepository.findById(commitId))
            .thenReturn(Optional.of(commitment(commitId, planId, true)));
        when(planRepository.findById(planId))
            .thenReturn(Optional.of(plan(planId, OWNER, PlanStatus.LOCKED)));

        assertThatThrownBy(() -> service.edit(commitId, RCDO_NODE_ID, "new title"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT));

        verify(commitmentRepository, never()).save(any());
    }

    @Test
    void editUnplannedCommitmentOnLockedPlanAllowed() {
        UUID planId = UUID.randomUUID();
        UUID commitId = UUID.randomUUID();
        principalIsOwner();
        when(commitmentRepository.findById(commitId))
            .thenReturn(Optional.of(commitment(commitId, planId, false)));
        when(planRepository.findById(planId))
            .thenReturn(Optional.of(plan(planId, OWNER, PlanStatus.LOCKED)));
        when(rcdoNodeRepository.existsById(RCDO_NODE_ID)).thenReturn(true);
        when(commitmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CommitmentDto dto = service.edit(commitId, RCDO_NODE_ID, "new title");

        assertThat(dto.title()).isEqualTo("new title");
        verify(commitmentRepository).save(any());
    }

    @Test
    void editPlannedCommitmentOnDraftPlanAllowed() {
        UUID planId = UUID.randomUUID();
        UUID commitId = UUID.randomUUID();
        principalIsOwner();
        when(commitmentRepository.findById(commitId))
            .thenReturn(Optional.of(commitment(commitId, planId, true)));
        when(planRepository.findById(planId))
            .thenReturn(Optional.of(plan(planId, OWNER, PlanStatus.DRAFT)));
        when(rcdoNodeRepository.existsById(RCDO_NODE_ID)).thenReturn(true);
        when(commitmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CommitmentDto dto = service.edit(commitId, RCDO_NODE_ID, "new title");

        assertThat(dto.title()).isEqualTo("new title");
    }

    // --- ownership ---

    @Test
    void createOnAnotherOwnersPlanRejectedWithForbidden() {
        UUID planId = UUID.randomUUID();
        when(principalResolver.currentPrincipal()).thenReturn("auth0|intruder");
        when(planRepository.findById(planId))
            .thenReturn(Optional.of(plan(planId, OWNER, PlanStatus.DRAFT)));

        assertThatThrownBy(() -> service.create(planId, RCDO_NODE_ID, "title"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN));

        verify(commitmentRepository, never()).save(any());
    }

    @Test
    void createOnMissingPlanRejectedWithNotFound() {
        UUID planId = UUID.randomUUID();
        when(planRepository.findById(planId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(planId, RCDO_NODE_ID, "title"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void deletePlannedCommitmentOnLockedPlanRejected() {
        UUID planId = UUID.randomUUID();
        UUID commitId = UUID.randomUUID();
        principalIsOwner();
        when(commitmentRepository.findById(commitId))
            .thenReturn(Optional.of(commitment(commitId, planId, true)));
        when(planRepository.findById(planId))
            .thenReturn(Optional.of(plan(planId, OWNER, PlanStatus.LOCKED)));

        assertThatThrownBy(() -> service.delete(commitId))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT));

        verify(commitmentRepository, never()).delete(any());
    }

    @Test
    void deleteUnplannedCommitmentOnLockedPlanAllowed() {
        UUID planId = UUID.randomUUID();
        UUID commitId = UUID.randomUUID();
        principalIsOwner();
        Commitment c = commitment(commitId, planId, false);
        when(commitmentRepository.findById(commitId)).thenReturn(Optional.of(c));
        when(planRepository.findById(planId))
            .thenReturn(Optional.of(plan(planId, OWNER, PlanStatus.LOCKED)));

        service.delete(commitId);

        verify(commitmentRepository).delete(c);
    }
}
