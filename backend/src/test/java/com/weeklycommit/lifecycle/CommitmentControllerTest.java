package com.weeklycommit.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
 * HTTP-level coverage for the commitment endpoints over the secure filter chain
 * and real embedded Postgres. Covers AE3 (every commitment requires a valid RCDO
 * link) and AE4 (locked plan: planned commitments immutable, new ones appendable
 * as unplanned), plus auth (401) and ownership (403).
 *
 * <p>Principal source: this IT deliberately does NOT import {@code TestPrincipalConfig},
 * so the real {@code JwtPrincipalResolver} is active and {@code currentPrincipal()}
 * returns the JWT subject {@code auth0|test-user} (from {@link TestSecurityConfig}).
 * For ownership to pass, the plans under test are owned by that subject; the
 * ownership-failure test creates a plan owned by a different string and asserts 403.
 */
@AutoConfigureMockMvc
@Transactional
class CommitmentControllerTest extends AbstractPostgresIT {

    // The JWT subject the secure chain authenticates as (TestSecurityConfig).
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
        // Child first; rolled back by @Transactional. Do NOT touch rcdo_node seed.
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

    private Commitment savedCommitment(UUID planId, boolean planned) {
        Commitment c = new Commitment();
        c.setWeeklyPlanId(planId);
        c.setRcdoNodeId(UUID.fromString(SEEDED_RCDO_NODE_ID));
        c.setTitle("Existing");
        c.setPlanned(planned);
        return commitmentRepository.saveAndFlush(c);
    }

    // --- AE3: RCDO link required at entry ---

    @Test
    void createWithNoRcdoNodeIdRejectedAndNothingPersisted() throws Exception {
        WeeklyPlan plan = savedPlan(OWNER, PlanStatus.DRAFT);

        mockMvc.perform(post("/api/plans/" + plan.getId() + "/commitments")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"No link\"}"))
            .andExpect(status().isBadRequest());

        assertThat(commitmentRepository.findByWeeklyPlanId(plan.getId())).isEmpty();
    }

    @Test
    void createWithNonExistentRcdoNodeIdRejected() throws Exception {
        WeeklyPlan plan = savedPlan(OWNER, PlanStatus.DRAFT);

        mockMvc.perform(post("/api/plans/" + plan.getId() + "/commitments")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rcdoNodeId\":\"99999999-9999-9999-9999-999999999999\","
                    + "\"title\":\"Dangling\"}"))
            .andExpect(status().isBadRequest());

        assertThat(commitmentRepository.findByWeeklyPlanId(plan.getId())).isEmpty();
    }

    @Test
    void createOnDraftPlanWithValidLinkPersistsPlanned() throws Exception {
        WeeklyPlan plan = savedPlan(OWNER, PlanStatus.DRAFT);

        mockMvc.perform(post("/api/plans/" + plan.getId() + "/commitments")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rcdoNodeId\":\"" + SEEDED_RCDO_NODE_ID + "\","
                    + "\"title\":\"Ship billing slice\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.planned").value(true))
            .andExpect(jsonPath("$.rcdoNodeId").value(SEEDED_RCDO_NODE_ID))
            .andExpect(jsonPath("$.title").value("Ship billing slice"));

        assertThat(commitmentRepository.findByWeeklyPlanId(plan.getId())).hasSize(1);
    }

    // --- AE4: locked plan immutability + appendable unplanned ---

    @Test
    void editPlannedCommitmentOnLockedPlanRejected() throws Exception {
        WeeklyPlan plan = savedPlan(OWNER, PlanStatus.LOCKED);
        Commitment planned = savedCommitment(plan.getId(), true);

        mockMvc.perform(put("/api/commitments/" + planned.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rcdoNodeId\":\"" + SEEDED_RCDO_NODE_ID + "\","
                    + "\"title\":\"Edited\"}"))
            .andExpect(status().isConflict());

        assertThat(commitmentRepository.findById(planned.getId()).orElseThrow().getTitle())
            .isEqualTo("Existing");
    }

    @Test
    void deletePlannedCommitmentOnLockedPlanRejected() throws Exception {
        WeeklyPlan plan = savedPlan(OWNER, PlanStatus.LOCKED);
        Commitment planned = savedCommitment(plan.getId(), true);

        mockMvc.perform(delete("/api/commitments/" + planned.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer()))
            .andExpect(status().isConflict());

        assertThat(commitmentRepository.findById(planned.getId())).isPresent();
    }

    @Test
    void createOnLockedPlanAcceptedAsUnplanned() throws Exception {
        WeeklyPlan plan = savedPlan(OWNER, PlanStatus.LOCKED);

        mockMvc.perform(post("/api/plans/" + plan.getId() + "/commitments")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rcdoNodeId\":\"" + SEEDED_RCDO_NODE_ID + "\","
                    + "\"title\":\"Reactive work\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.planned").value(false));

        assertThat(commitmentRepository.findByWeeklyPlanId(plan.getId())).hasSize(1);
    }

    @Test
    void createOnReconcilingPlanRejected() throws Exception {
        WeeklyPlan plan = savedPlan(OWNER, PlanStatus.RECONCILING);

        mockMvc.perform(post("/api/plans/" + plan.getId() + "/commitments")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rcdoNodeId\":\"" + SEEDED_RCDO_NODE_ID + "\","
                    + "\"title\":\"Too late\"}"))
            .andExpect(status().isConflict());

        assertThat(commitmentRepository.findByWeeklyPlanId(plan.getId())).isEmpty();
    }

    // --- auth + ownership ---

    @Test
    void createWithoutTokenRejected() throws Exception {
        WeeklyPlan plan = savedPlan(OWNER, PlanStatus.DRAFT);

        mockMvc.perform(post("/api/plans/" + plan.getId() + "/commitments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rcdoNodeId\":\"" + SEEDED_RCDO_NODE_ID + "\",\"title\":\"x\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void editWithoutTokenRejected() throws Exception {
        WeeklyPlan plan = savedPlan(OWNER, PlanStatus.DRAFT);
        Commitment c = savedCommitment(plan.getId(), true);

        mockMvc.perform(put("/api/commitments/" + c.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rcdoNodeId\":\"" + SEEDED_RCDO_NODE_ID + "\",\"title\":\"x\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void createOnAnotherOwnersPlanRejectedWithForbidden() throws Exception {
        WeeklyPlan othersPlan = savedPlan(OTHER_OWNER, PlanStatus.DRAFT);

        mockMvc.perform(post("/api/plans/" + othersPlan.getId() + "/commitments")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rcdoNodeId\":\"" + SEEDED_RCDO_NODE_ID + "\",\"title\":\"x\"}"))
            .andExpect(status().isForbidden());

        assertThat(commitmentRepository.findByWeeklyPlanId(othersPlan.getId())).isEmpty();
    }
}
