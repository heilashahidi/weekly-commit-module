package com.weeklycommit.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.weeklycommit.support.AbstractPostgresIT;
import com.weeklycommit.support.TestSecurityConfig;
import java.util.List;
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
 * HTTP-level coverage for carry-forward (U8) over the secure filter chain and real
 * embedded Postgres. Covers AE7 end-to-end: a RECONCILED plan with PARTIAL /
 * NOT_DONE / DROPPED planned commitments lists only the two non-dropped candidates,
 * and carrying the selected ids seeds next week's DRAFT with planned copies that
 * carry the same RCDO link, lineage, and an incremented carry-week-count. Plus the
 * RECONCILED gate (409), auth (401), and ownership (403).
 *
 * <p>Principal source: like the other lifecycle ITs, this deliberately does NOT
 * import {@code TestPrincipalConfig}, so the real {@code JwtPrincipalResolver} is
 * active and {@code currentPrincipal()} is the JWT subject {@code auth0|test-user}.
 */
@AutoConfigureMockMvc
@Transactional
class CarryForwardControllerTest extends AbstractPostgresIT {

    private static final String OWNER = TestSecurityConfig.TEST_SUBJECT;
    private static final String OTHER_OWNER = "auth0|someone-else";

    // A SUPPORTING_OUTCOME seeded by V3__rcdo_seed.sql with a fixed UUID.
    private static final UUID SEEDED_RCDO =
        UUID.fromString("44444444-4444-4444-4444-444444444441");

    private static final String THIS_WEEK = "2026-W23";
    private static final String NEXT_WEEK = "2026-W24";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    WeeklyPlanRepository planRepository;

    @Autowired
    CommitmentRepository commitmentRepository;

    @BeforeEach
    void clear() {
        // Child first; rolled back by @Transactional. Do NOT touch rcdo_node seed.
        commitmentRepository.deleteAllInBatch();
        planRepository.deleteAllInBatch();
    }

    private String bearer() {
        return "Bearer " + TestSecurityConfig.VALID_TOKEN;
    }

    private WeeklyPlan savedPlan(String owner, PlanStatus status, String weekKey) {
        WeeklyPlan p = new WeeklyPlan();
        p.setOwner(owner);
        p.setWeekKey(weekKey);
        p.setStatus(status);
        return planRepository.saveAndFlush(p);
    }

    private Commitment savedCommitment(
            UUID planId, ReconciliationStatus status, int carryWeekCount) {
        Commitment c = new Commitment();
        c.setWeeklyPlanId(planId);
        c.setRcdoNodeId(SEEDED_RCDO);
        c.setTitle("Work " + status);
        c.setPlanned(true);
        c.setReconciliationStatus(status);
        c.setCarryWeekCount(carryWeekCount);
        return commitmentRepository.saveAndFlush(c);
    }

    // --- AE7: candidates + carry end-to-end ---

    @Test
    void candidatesReturnPartialAndNotDoneNotDropped() throws Exception {
        WeeklyPlan plan = savedPlan(OWNER, PlanStatus.RECONCILED, THIS_WEEK);
        Commitment partial = savedCommitment(plan.getId(), ReconciliationStatus.PARTIAL, 0);
        Commitment notDone = savedCommitment(plan.getId(), ReconciliationStatus.NOT_DONE, 0);
        savedCommitment(plan.getId(), ReconciliationStatus.DROPPED, 0);

        mockMvc.perform(get("/api/lifecycle/plans/" + plan.getId() + "/carry-candidates")
                .header(HttpHeaders.AUTHORIZATION, bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[?(@.id=='" + partial.getId() + "')]").exists())
            .andExpect(jsonPath("$[?(@.id=='" + notDone.getId() + "')]").exists());
    }

    @Test
    void carrySeedsNextWeekDraftWithLineageAndIncrementedWeekCount() throws Exception {
        WeeklyPlan plan = savedPlan(OWNER, PlanStatus.RECONCILED, THIS_WEEK);
        Commitment partial = savedCommitment(plan.getId(), ReconciliationStatus.PARTIAL, 1);
        Commitment notDone = savedCommitment(plan.getId(), ReconciliationStatus.NOT_DONE, 0);
        savedCommitment(plan.getId(), ReconciliationStatus.DROPPED, 0);

        // IC selects both non-dropped candidates.
        String body = "{\"commitmentIds\":[\"" + partial.getId() + "\",\""
            + notDone.getId() + "\"]}";

        mockMvc.perform(post("/api/lifecycle/plans/" + plan.getId() + "/carry")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));

        // Next week's DRAFT was created and seeded.
        WeeklyPlan nextPlan =
            planRepository.findByOwnerAndWeekKey(OWNER, NEXT_WEEK).orElseThrow();
        assertThat(nextPlan.getStatus()).isEqualTo(PlanStatus.DRAFT);

        List<Commitment> seeded = commitmentRepository.findByWeeklyPlanId(nextPlan.getId());
        assertThat(seeded).hasSize(2);
        assertThat(seeded).allSatisfy(c -> {
            assertThat(c.isPlanned()).isTrue();
            assertThat(c.getRcdoNodeId()).isEqualTo(SEEDED_RCDO);
            assertThat(c.getReconciliationStatus()).isNull();
        });
        // Lineage: each seeded copy points back to a source and bumps its week-count.
        Commitment fromPartial = seeded.stream()
            .filter(c -> partial.getId().equals(c.getCarriedFromId())).findFirst().orElseThrow();
        assertThat(fromPartial.getCarryWeekCount()).isEqualTo(2);
        Commitment fromNotDone = seeded.stream()
            .filter(c -> notDone.getId().equals(c.getCarriedFromId())).findFirst().orElseThrow();
        assertThat(fromNotDone.getCarryWeekCount()).isEqualTo(1);

        // The DROPPED commitment was never carried.
        assertThat(seeded).noneSatisfy(c ->
            assertThat(c.getCarryWeekCount()).isEqualTo(0));
    }

    @Test
    void carryWithOnlyOneSelectedIdSeedsOnlyThatOne() throws Exception {
        WeeklyPlan plan = savedPlan(OWNER, PlanStatus.RECONCILED, THIS_WEEK);
        Commitment partial = savedCommitment(plan.getId(), ReconciliationStatus.PARTIAL, 0);
        savedCommitment(plan.getId(), ReconciliationStatus.NOT_DONE, 0);

        String body = "{\"commitmentIds\":[\"" + partial.getId() + "\"]}";

        mockMvc.perform(post("/api/lifecycle/plans/" + plan.getId() + "/carry")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));

        WeeklyPlan nextPlan =
            planRepository.findByOwnerAndWeekKey(OWNER, NEXT_WEEK).orElseThrow();
        assertThat(commitmentRepository.findByWeeklyPlanId(nextPlan.getId()))
            .singleElement()
            .satisfies(c -> assertThat(c.getCarriedFromId()).isEqualTo(partial.getId()));
    }

    // --- gate + auth + ownership ---

    @Test
    void candidatesOnNonReconciledPlanRejectedWithConflict() throws Exception {
        WeeklyPlan plan = savedPlan(OWNER, PlanStatus.RECONCILING, THIS_WEEK);

        mockMvc.perform(get("/api/lifecycle/plans/" + plan.getId() + "/carry-candidates")
                .header(HttpHeaders.AUTHORIZATION, bearer()))
            .andExpect(status().isConflict());
    }

    @Test
    void carryOnNonReconciledPlanRejectedWithConflict() throws Exception {
        WeeklyPlan plan = savedPlan(OWNER, PlanStatus.RECONCILING, THIS_WEEK);

        mockMvc.perform(post("/api/lifecycle/plans/" + plan.getId() + "/carry")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"commitmentIds\":[]}"))
            .andExpect(status().isConflict());

        assertThat(planRepository.findByOwnerAndWeekKey(OWNER, NEXT_WEEK)).isEmpty();
    }

    @Test
    void candidatesWithoutTokenRejected() throws Exception {
        WeeklyPlan plan = savedPlan(OWNER, PlanStatus.RECONCILED, THIS_WEEK);

        mockMvc.perform(get("/api/lifecycle/plans/" + plan.getId() + "/carry-candidates"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void carryWithoutTokenRejected() throws Exception {
        WeeklyPlan plan = savedPlan(OWNER, PlanStatus.RECONCILED, THIS_WEEK);

        mockMvc.perform(post("/api/lifecycle/plans/" + plan.getId() + "/carry")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"commitmentIds\":[]}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void candidatesOnAnotherOwnersPlanRejectedWithForbidden() throws Exception {
        WeeklyPlan othersPlan = savedPlan(OTHER_OWNER, PlanStatus.RECONCILED, THIS_WEEK);

        mockMvc.perform(get("/api/lifecycle/plans/" + othersPlan.getId() + "/carry-candidates")
                .header(HttpHeaders.AUTHORIZATION, bearer()))
            .andExpect(status().isForbidden());
    }

    @Test
    void carryOnAnotherOwnersPlanRejectedWithForbidden() throws Exception {
        WeeklyPlan othersPlan = savedPlan(OTHER_OWNER, PlanStatus.RECONCILED, THIS_WEEK);

        mockMvc.perform(post("/api/lifecycle/plans/" + othersPlan.getId() + "/carry")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"commitmentIds\":[]}"))
            .andExpect(status().isForbidden());
    }
}
