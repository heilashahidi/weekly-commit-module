package com.weeklycommit.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.weeklycommit.support.AbstractPostgresIT;
import com.weeklycommit.support.TestSecurityConfig;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * HTTP-level coverage for the reconciliation endpoints (U5) over the secure filter
 * chain and real embedded Postgres. Covers AE5 (submit blocked while a commitment
 * is unstatused; the plan stays RECONCILING), the all-statused submit advancing to
 * RECONCILED, the RECONCILING-only window for setStatus, the system-only
 * UNRECONCILED rejection (KTD 5/R19), status+note persistence, plus auth (401) and
 * ownership (403).
 *
 * <p>Principal source: like the other lifecycle ITs, this deliberately does NOT
 * import {@code TestPrincipalConfig}, so the real {@code JwtPrincipalResolver} is
 * active and {@code currentPrincipal()} returns the JWT subject
 * {@code auth0|test-user} (from {@link TestSecurityConfig}). Plans under test are
 * owned by that subject; the ownership test uses a different owner and asserts 403.
 */
@AutoConfigureMockMvc
@Transactional
class ReconciliationControllerTest extends AbstractPostgresIT {

    private static final String OWNER = TestSecurityConfig.TEST_SUBJECT;
    private static final String OTHER_OWNER = "auth0|someone-else";
    // A SUPPORTING_OUTCOME seeded by V3__rcdo_seed.sql with a fixed UUID.
    private static final String SEEDED_RCDO_NODE_ID = "44444444-4444-4444-4444-444444444441";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    WeeklyPlanRepository planRepository;

    @Autowired
    CommitmentRepository commitmentRepository;

    @BeforeEach
    void clear() {
        commitmentRepository.deleteAllInBatch();
        planRepository.deleteAllInBatch();
    }

    private String bearer() {
        return "Bearer " + TestSecurityConfig.VALID_TOKEN;
    }

    private WeeklyPlan savedPlan(String owner, PlanStatus status) {
        WeeklyPlan p = new WeeklyPlan();
        p.setOwner(owner);
        p.setWeekKey("2026-W23");
        p.setStatus(status);
        return planRepository.saveAndFlush(p);
    }

    private Commitment savedCommitment(UUID planId, ReconciliationStatus status) {
        Commitment c = new Commitment();
        c.setWeeklyPlanId(planId);
        c.setRcdoNodeId(UUID.fromString(SEEDED_RCDO_NODE_ID));
        c.setTitle("Work");
        c.setPlanned(true);
        c.setReconciliationStatus(status);
        return commitmentRepository.saveAndFlush(c);
    }

    // --- AE5: submit gate ---

    @Test
    void submitWithOneUnstatusedCommitmentRejectedAndPlanStaysReconciling() throws Exception {
        WeeklyPlan plan = savedPlan(OWNER, PlanStatus.RECONCILING);
        savedCommitment(plan.getId(), ReconciliationStatus.DONE);
        savedCommitment(plan.getId(), null);

        mockMvc.perform(
                post("/api/lifecycle/plans/" + plan.getId() + "/transitions/submit-reconciled")
                    .header(HttpHeaders.AUTHORIZATION, bearer()))
            .andExpect(status().isUnprocessableEntity());

        assertThat(planRepository.findById(plan.getId()).orElseThrow().getStatus())
            .isEqualTo(PlanStatus.RECONCILING);
    }

    @Test
    void submitWithAllStatusedAdvancesToReconciled() throws Exception {
        WeeklyPlan plan = savedPlan(OWNER, PlanStatus.RECONCILING);
        savedCommitment(plan.getId(), ReconciliationStatus.DONE);
        savedCommitment(plan.getId(), ReconciliationStatus.PARTIAL);

        mockMvc.perform(
                post("/api/lifecycle/plans/" + plan.getId() + "/transitions/submit-reconciled")
                    .header(HttpHeaders.AUTHORIZATION, bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("RECONCILED"));

        assertThat(planRepository.findById(plan.getId()).orElseThrow().getStatus())
            .isEqualTo(PlanStatus.RECONCILED);
    }

    // --- setStatus rules ---

    @Test
    void setStatusWhilePlanLockedRejectedWithConflict() throws Exception {
        WeeklyPlan plan = savedPlan(OWNER, PlanStatus.LOCKED);
        Commitment c = savedCommitment(plan.getId(), null);

        mockMvc.perform(put("/api/lifecycle/commitments/" + c.getId() + "/status")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DONE\"}"))
            .andExpect(status().isConflict());

        assertThat(commitmentRepository.findById(c.getId()).orElseThrow().getReconciliationStatus())
            .isNull();
    }

    @Test
    void setStatusUnreconciledRejectedAsSystemOnly() throws Exception {
        WeeklyPlan plan = savedPlan(OWNER, PlanStatus.RECONCILING);
        Commitment c = savedCommitment(plan.getId(), null);

        mockMvc.perform(put("/api/lifecycle/commitments/" + c.getId() + "/status")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"UNRECONCILED\"}"))
            .andExpect(status().isUnprocessableEntity());

        assertThat(commitmentRepository.findById(c.getId()).orElseThrow().getReconciliationStatus())
            .isNull();
    }

    @Test
    void setStatusWithNotePersistsBoth() throws Exception {
        WeeklyPlan plan = savedPlan(OWNER, PlanStatus.RECONCILING);
        Commitment c = savedCommitment(plan.getId(), null);

        mockMvc.perform(put("/api/lifecycle/commitments/" + c.getId() + "/status")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"PARTIAL\",\"note\":\"got halfway\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reconciliationStatus").value("PARTIAL"))
            .andExpect(jsonPath("$.reconciliationNote").value("got halfway"));

        Commitment reloaded = commitmentRepository.findById(c.getId()).orElseThrow();
        assertThat(reloaded.getReconciliationStatus()).isEqualTo(ReconciliationStatus.PARTIAL);
        assertThat(reloaded.getReconciliationNote()).isEqualTo("got halfway");
    }

    @Test
    void setStatusWithNoteOmittedIsAccepted() throws Exception {
        WeeklyPlan plan = savedPlan(OWNER, PlanStatus.RECONCILING);
        Commitment c = savedCommitment(plan.getId(), null);

        mockMvc.perform(put("/api/lifecycle/commitments/" + c.getId() + "/status")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DONE\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reconciliationStatus").value("DONE"));

        Commitment reloaded = commitmentRepository.findById(c.getId()).orElseThrow();
        assertThat(reloaded.getReconciliationStatus()).isEqualTo(ReconciliationStatus.DONE);
        assertThat(reloaded.getReconciliationNote()).isNull();
    }

    // --- auth + ownership ---

    @Test
    void submitWithoutTokenRejected() throws Exception {
        WeeklyPlan plan = savedPlan(OWNER, PlanStatus.RECONCILING);

        mockMvc.perform(
                post("/api/lifecycle/plans/" + plan.getId() + "/transitions/submit-reconciled"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void setStatusWithoutTokenRejected() throws Exception {
        WeeklyPlan plan = savedPlan(OWNER, PlanStatus.RECONCILING);
        Commitment c = savedCommitment(plan.getId(), null);

        mockMvc.perform(put("/api/lifecycle/commitments/" + c.getId() + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DONE\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void submitOnAnotherOwnersPlanRejectedWithForbidden() throws Exception {
        WeeklyPlan othersPlan = savedPlan(OTHER_OWNER, PlanStatus.RECONCILING);
        savedCommitment(othersPlan.getId(), ReconciliationStatus.DONE);

        mockMvc.perform(
                post("/api/lifecycle/plans/" + othersPlan.getId()
                        + "/transitions/submit-reconciled")
                    .header(HttpHeaders.AUTHORIZATION, bearer()))
            .andExpect(status().isForbidden());

        assertThat(planRepository.findById(othersPlan.getId()).orElseThrow().getStatus())
            .isEqualTo(PlanStatus.RECONCILING);
    }

    @Test
    void setStatusOnAnotherOwnersPlanRejectedWithForbidden() throws Exception {
        WeeklyPlan othersPlan = savedPlan(OTHER_OWNER, PlanStatus.RECONCILING);
        Commitment c = savedCommitment(othersPlan.getId(), null);

        mockMvc.perform(put("/api/lifecycle/commitments/" + c.getId() + "/status")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DONE\"}"))
            .andExpect(status().isForbidden());
    }
}
