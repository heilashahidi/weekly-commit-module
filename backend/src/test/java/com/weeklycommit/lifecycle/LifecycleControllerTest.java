package com.weeklycommit.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * HTTP-level coverage for the lifecycle endpoints over the secure filter chain and
 * real embedded Postgres. Covers the manual transitions (get-or-create current
 * plan, lock with USER_LOCKED, empty-plan no_plan, legal LOCKED->RECONCILING),
 * illegal-transition 409, plus auth (401) and ownership (403).
 *
 * <p>Principal source: this IT deliberately does NOT import {@code TestPrincipalConfig},
 * so the real {@code JwtPrincipalResolver} is active and {@code currentPrincipal()}
 * returns the JWT subject {@code auth0|test-user} (from {@link TestSecurityConfig}).
 * Happy-path plans are owned by that subject; the ownership test uses a different
 * owner and asserts 403.
 */
@AutoConfigureMockMvc
@Transactional
class LifecycleControllerTest extends AbstractPostgresIT {

    private static final String OWNER = TestSecurityConfig.TEST_SUBJECT;
    private static final String OTHER_OWNER = "auth0|someone-else";
    private static final String SEEDED_RCDO_NODE_ID = "44444444-4444-4444-4444-444444444441";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    WeeklyPlanRepository planRepository;

    @Autowired
    CommitmentRepository commitmentRepository;

    @Autowired
    ObjectMapper objectMapper;

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

    private void savedCommitment(UUID planId) {
        Commitment c = new Commitment();
        c.setWeeklyPlanId(planId);
        c.setRcdoNodeId(UUID.fromString(SEEDED_RCDO_NODE_ID));
        c.setTitle("Existing");
        c.setPlanned(true);
        commitmentRepository.saveAndFlush(c);
    }

    // --- get-or-create current plan ---

    @Test
    void getCurrentPlanCreatesDraftThenReturnsSameIdIdempotently() throws Exception {
        String first = mockMvc.perform(get("/api/lifecycle/plans/current")
                .header(HttpHeaders.AUTHORIZATION, bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.owner").value(OWNER))
            .andExpect(jsonPath("$.statusDeadline").isNotEmpty())
            .andReturn().getResponse().getContentAsString();
        JsonNode firstNode = objectMapper.readTree(first);

        String second = mockMvc.perform(get("/api/lifecycle/plans/current")
                .header(HttpHeaders.AUTHORIZATION, bearer()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode secondNode = objectMapper.readTree(second);

        assertThat(secondNode.get("id").asText()).isEqualTo(firstNode.get("id").asText());
        assertThat(planRepository.findByOwnerAndWeekKey(OWNER, "2026-W23")).isPresent();
    }

    // --- lock ---

    @Test
    void lockTransitionsToLockedWithUserLocked() throws Exception {
        WeeklyPlan plan = savedPlan(OWNER, PlanStatus.DRAFT);
        savedCommitment(plan.getId());

        mockMvc.perform(post("/api/lifecycle/plans/" + plan.getId() + "/transitions/lock")
                .header(HttpHeaders.AUTHORIZATION, bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("LOCKED"))
            .andExpect(jsonPath("$.lockType").value("USER_LOCKED"))
            .andExpect(jsonPath("$.noPlan").value(false));
    }

    @Test
    void lockingEmptyPlanSetsNoPlanTrue() throws Exception {
        WeeklyPlan plan = savedPlan(OWNER, PlanStatus.DRAFT);

        mockMvc.perform(post("/api/lifecycle/plans/" + plan.getId() + "/transitions/lock")
                .header(HttpHeaders.AUTHORIZATION, bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("LOCKED"))
            .andExpect(jsonPath("$.noPlan").value(true));
    }

    // --- legal + illegal transitions ---

    @Test
    void startReconcilingFromLockedSucceeds() throws Exception {
        WeeklyPlan plan = savedPlan(OWNER, PlanStatus.LOCKED);

        mockMvc.perform(
                post("/api/lifecycle/plans/" + plan.getId() + "/transitions/start-reconciling")
                    .header(HttpHeaders.AUTHORIZATION, bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("RECONCILING"));
    }

    @Test
    void illegalSkipAheadTransitionRejectedWithConflict() throws Exception {
        // DRAFT -> RECONCILED via submit-reconciled is an illegal skip.
        WeeklyPlan plan = savedPlan(OWNER, PlanStatus.DRAFT);

        mockMvc.perform(
                post("/api/lifecycle/plans/" + plan.getId() + "/transitions/submit-reconciled")
                    .header(HttpHeaders.AUTHORIZATION, bearer()))
            .andExpect(status().isConflict());

        assertThat(planRepository.findById(plan.getId()).orElseThrow().getStatus())
            .isEqualTo(PlanStatus.DRAFT);
    }

    // --- auth + ownership ---

    @Test
    void transitionOnAnotherOwnersPlanRejectedWithForbidden() throws Exception {
        WeeklyPlan othersPlan = savedPlan(OTHER_OWNER, PlanStatus.DRAFT);

        mockMvc.perform(post("/api/lifecycle/plans/" + othersPlan.getId() + "/transitions/lock")
                .header(HttpHeaders.AUTHORIZATION, bearer()))
            .andExpect(status().isForbidden());

        assertThat(planRepository.findById(othersPlan.getId()).orElseThrow().getStatus())
            .isEqualTo(PlanStatus.DRAFT);
    }

    @Test
    void currentPlanWithoutTokenRejected() throws Exception {
        mockMvc.perform(get("/api/lifecycle/plans/current"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void lockWithoutTokenRejected() throws Exception {
        WeeklyPlan plan = savedPlan(OWNER, PlanStatus.DRAFT);

        mockMvc.perform(post("/api/lifecycle/plans/" + plan.getId() + "/transitions/lock"))
            .andExpect(status().isUnauthorized());
    }
}
