package com.weeklycommit.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * HTTP-level coverage for the non-blocking manager-review overlay (U9, R16/R17) over
 * the secure filter chain and real embedded Postgres.
 *
 * <p><b>Covers AE8.</b> {@link #lockedPlanWithNoReviewStillAdvancesToReconciling()}
 * drives a LOCKED plan that has NO manager review through LOCKED -&gt; RECONCILING via
 * the lifecycle endpoint and asserts it succeeds — proving the transition is
 * independent of review state (R17). Other tests assert: a PUT persists
 * reviewer+comment+timestamp; adding a review at LOCKED does not mutate the plan or
 * its commitments (overlay-only, R16 + Outstanding Question (c)); the upsert is
 * one-review-per-plan; review absence is valid throughout; and no JWT -&gt; 401.
 *
 * <p>Principal source: like {@code LifecycleControllerTest}, this IT does not import
 * {@code TestPrincipalConfig}, so the real {@code JwtPrincipalResolver} is active and
 * {@code currentPrincipal()} returns the JWT subject {@code auth0|test-user}.
 */
@AutoConfigureMockMvc
@Transactional
class ManagerReviewControllerTest extends AbstractPostgresIT {

    private static final String OWNER = TestSecurityConfig.TEST_SUBJECT;
    private static final String SEEDED_RCDO_NODE_ID = "44444444-4444-4444-4444-444444444441";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    WeeklyPlanRepository planRepository;

    @Autowired
    CommitmentRepository commitmentRepository;

    @Autowired
    ManagerReviewRepository reviewRepository;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void clear() {
        reviewRepository.deleteAllInBatch();
        commitmentRepository.deleteAllInBatch();
        planRepository.deleteAllInBatch();
    }

    private String bearer() {
        return "Bearer " + TestSecurityConfig.VALID_TOKEN;
    }

    private WeeklyPlan savedPlan(PlanStatus status) {
        WeeklyPlan p = new WeeklyPlan();
        p.setOwner(OWNER);
        p.setWeekKey("2026-W23");
        p.setStatus(status);
        return planRepository.saveAndFlush(p);
    }

    private Commitment savedCommitment(UUID planId) {
        Commitment c = new Commitment();
        c.setWeeklyPlanId(planId);
        c.setRcdoNodeId(UUID.fromString(SEEDED_RCDO_NODE_ID));
        c.setTitle("Existing");
        c.setPlanned(true);
        return commitmentRepository.saveAndFlush(c);
    }

    private String reviewBody(String comment) throws Exception {
        return objectMapper.writeValueAsString(
            new ManagerReviewController.ReviewRequest(comment));
    }

    // --- AE8: transition is independent of review state ---

    @Test
    void lockedPlanWithNoReviewStillAdvancesToReconciling() throws Exception {
        WeeklyPlan plan = savedPlan(PlanStatus.LOCKED);

        // Precondition: no review exists for this plan.
        assertThat(reviewRepository.findByWeeklyPlanId(plan.getId())).isEmpty();

        // The lifecycle transition succeeds with no review present (R17).
        mockMvc.perform(
                post("/api/lifecycle/plans/" + plan.getId() + "/transitions/start-reconciling")
                    .header(HttpHeaders.AUTHORIZATION, bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("RECONCILING"));

        assertThat(planRepository.findById(plan.getId()).orElseThrow().getStatus())
            .isEqualTo(PlanStatus.RECONCILING);
        // Still no review — the transition never created or required one.
        assertThat(reviewRepository.findByWeeklyPlanId(plan.getId())).isEmpty();
    }

    // --- PUT persists reviewer + comment + timestamp ---

    @Test
    void putReviewPersistsReviewerCommentAndTimestamp() throws Exception {
        WeeklyPlan plan = savedPlan(PlanStatus.LOCKED);

        mockMvc.perform(put("/api/lifecycle/plans/" + plan.getId() + "/review")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(reviewBody("Strong week")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.weeklyPlanId").value(plan.getId().toString()))
            .andExpect(jsonPath("$.reviewer").value(OWNER))
            .andExpect(jsonPath("$.comment").value("Strong week"))
            .andExpect(jsonPath("$.reviewedAt").isNotEmpty());

        ManagerReview persisted =
            reviewRepository.findByWeeklyPlanId(plan.getId()).orElseThrow();
        assertThat(persisted.getReviewer()).isEqualTo(OWNER);
        assertThat(persisted.getComment()).isEqualTo("Strong week");
        assertThat(persisted.getCreatedDate()).isNotNull();
    }

    // --- review at LOCKED does not mutate the plan or its commitments (R16, Q(c)) ---

    @Test
    void reviewAtLockedDoesNotMutatePlanOrCommitments() throws Exception {
        WeeklyPlan plan = savedPlan(PlanStatus.LOCKED);
        Commitment commitment = savedCommitment(plan.getId());

        mockMvc.perform(put("/api/lifecycle/plans/" + plan.getId() + "/review")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(reviewBody("Reviewed at lock")))
            .andExpect(status().isOk());

        WeeklyPlan after = planRepository.findById(plan.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(PlanStatus.LOCKED);

        List<Commitment> commitments = commitmentRepository.findByWeeklyPlanId(plan.getId());
        assertThat(commitments).hasSize(1);
        Commitment c = commitments.get(0);
        assertThat(c.getId()).isEqualTo(commitment.getId());
        assertThat(c.getTitle()).isEqualTo("Existing");
        assertThat(c.isPlanned()).isTrue();
        assertThat(c.getReconciliationStatus()).isNull();
    }

    // --- one review per plan: PUT upserts ---

    @Test
    void secondPutUpdatesTheSameReviewOnePerPlan() throws Exception {
        WeeklyPlan plan = savedPlan(PlanStatus.LOCKED);

        String first = mockMvc.perform(put("/api/lifecycle/plans/" + plan.getId() + "/review")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(reviewBody("first")))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String firstId = objectMapper.readTree(first).get("id").asText();

        mockMvc.perform(put("/api/lifecycle/plans/" + plan.getId() + "/review")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(reviewBody("second")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(firstId))
            .andExpect(jsonPath("$.comment").value("second"));

        assertThat(reviewRepository.count()).isEqualTo(1L);
    }

    // --- review absence is a valid state (GET -> 204) ---

    @Test
    void getReviewWhenNoneExistsReturnsNoContent() throws Exception {
        WeeklyPlan plan = savedPlan(PlanStatus.LOCKED);

        mockMvc.perform(get("/api/lifecycle/plans/" + plan.getId() + "/review")
                .header(HttpHeaders.AUTHORIZATION, bearer()))
            .andExpect(status().isNoContent());
    }

    @Test
    void getReviewReturnsTheReviewWhenPresent() throws Exception {
        WeeklyPlan plan = savedPlan(PlanStatus.LOCKED);
        mockMvc.perform(put("/api/lifecycle/plans/" + plan.getId() + "/review")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(reviewBody("hello")))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/lifecycle/plans/" + plan.getId() + "/review")
                .header(HttpHeaders.AUTHORIZATION, bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.comment").value("hello"));
    }

    // --- auth ---

    @Test
    void putReviewWithoutTokenRejected() throws Exception {
        WeeklyPlan plan = savedPlan(PlanStatus.LOCKED);

        mockMvc.perform(put("/api/lifecycle/plans/" + plan.getId() + "/review")
                .contentType(MediaType.APPLICATION_JSON)
                .content(reviewBody("nope")))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getReviewWithoutTokenRejected() throws Exception {
        WeeklyPlan plan = savedPlan(PlanStatus.LOCKED);

        mockMvc.perform(get("/api/lifecycle/plans/" + plan.getId() + "/review"))
            .andExpect(status().isUnauthorized());
    }
}
