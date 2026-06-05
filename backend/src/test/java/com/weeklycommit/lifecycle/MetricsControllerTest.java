package com.weeklycommit.lifecycle;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * HTTP-level coverage for the metrics endpoint (U6) over the secure filter chain and
 * real embedded Postgres. Covers the AE6 computed DTO for an owned plan (accuracy
 * from planned only, the planned-vs-unplanned ratio), plus auth (401) and ownership
 * (403).
 *
 * <p>Principal source: like the other lifecycle ITs, this deliberately does NOT
 * import {@code TestPrincipalConfig}, so the real {@code JwtPrincipalResolver} is
 * active and {@code currentPrincipal()} returns the JWT subject
 * {@code auth0|test-user} (from {@link TestSecurityConfig}). The owned plan is owned
 * by that subject; the ownership test uses a different owner and asserts 403.
 */
@AutoConfigureMockMvc
@Transactional
class MetricsControllerTest extends AbstractPostgresIT {

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

    private WeeklyPlan savedPlan(String owner) {
        WeeklyPlan p = new WeeklyPlan();
        p.setOwner(owner);
        p.setWeekKey("2026-W23");
        p.setStatus(PlanStatus.RECONCILED);
        return planRepository.saveAndFlush(p);
    }

    private void savedCommitment(UUID planId, boolean planned, ReconciliationStatus status) {
        Commitment c = new Commitment();
        c.setWeeklyPlanId(planId);
        c.setRcdoNodeId(UUID.fromString(SEEDED_RCDO_NODE_ID));
        c.setTitle("Work");
        c.setPlanned(planned);
        c.setReconciliationStatus(status);
        commitmentRepository.saveAndFlush(c);
    }

    @Test
    void metricsForOwnedPlanReturnsComputedDto() throws Exception {
        // AE6: 4 planned (3 DONE, 1 NOT_DONE) + 2 unplanned -> accuracy 0.75, ratio 0.5.
        WeeklyPlan plan = savedPlan(OWNER);
        savedCommitment(plan.getId(), true, ReconciliationStatus.DONE);
        savedCommitment(plan.getId(), true, ReconciliationStatus.DONE);
        savedCommitment(plan.getId(), true, ReconciliationStatus.DONE);
        savedCommitment(plan.getId(), true, ReconciliationStatus.NOT_DONE);
        savedCommitment(plan.getId(), false, ReconciliationStatus.DONE);
        savedCommitment(plan.getId(), false, ReconciliationStatus.NOT_DONE);

        mockMvc.perform(get("/api/lifecycle/plans/" + plan.getId() + "/metrics")
                .header(HttpHeaders.AUTHORIZATION, bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.planId").value(plan.getId().toString()))
            .andExpect(jsonPath("$.plannedCount").value(4))
            .andExpect(jsonPath("$.unplannedCount").value(2))
            .andExpect(jsonPath("$.doneCount").value(3))
            .andExpect(jsonPath("$.reconciliationAccuracy").value(0.75))
            .andExpect(jsonPath("$.plannedVsUnplannedRatio").value(0.5));
    }

    @Test
    void metricsForZeroPlannedReturnsNullAccuracy() throws Exception {
        // Zero-planned convention: accuracy is null, ratio defined (0.0).
        WeeklyPlan plan = savedPlan(OWNER);
        savedCommitment(plan.getId(), false, ReconciliationStatus.DONE);

        mockMvc.perform(get("/api/lifecycle/plans/" + plan.getId() + "/metrics")
                .header(HttpHeaders.AUTHORIZATION, bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.plannedCount").value(0))
            .andExpect(jsonPath("$.reconciliationAccuracy").doesNotExist())
            .andExpect(jsonPath("$.plannedVsUnplannedRatio").value(0.0));
    }

    @Test
    void metricsWithoutTokenRejected() throws Exception {
        WeeklyPlan plan = savedPlan(OWNER);

        mockMvc.perform(get("/api/lifecycle/plans/" + plan.getId() + "/metrics"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void metricsForAnotherOwnersPlanRejectedWithForbidden() throws Exception {
        WeeklyPlan othersPlan = savedPlan(OTHER_OWNER);

        mockMvc.perform(get("/api/lifecycle/plans/" + othersPlan.getId() + "/metrics")
                .header(HttpHeaders.AUTHORIZATION, bearer()))
            .andExpect(status().isForbidden());
    }

    // Workstream F: getMetrics widened to owner-OR-manager-of-owner. A manager reading a
    // seeded report's metrics gets 200; an unrelated principal still 403 (covered above).
    @Test
    void metricsForReportPlanReadableByManager() throws Exception {
        WeeklyPlan reportPlan = savedPlan(TestSecurityConfig.REPORT_SUBJECT);
        savedCommitment(reportPlan.getId(), true, ReconciliationStatus.DONE);

        mockMvc.perform(get("/api/lifecycle/plans/" + reportPlan.getId() + "/metrics")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    TestSecurityConfig.bearerFor(TestSecurityConfig.MANAGER_SUBJECT)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.planId").value(reportPlan.getId().toString()))
            .andExpect(jsonPath("$.plannedCount").value(1));
    }
}
