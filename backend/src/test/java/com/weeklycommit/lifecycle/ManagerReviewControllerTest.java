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
 * HTTP-level coverage for the non-blocking manager-review overlay (R16/R17) plus the
 * workstream-F manager-scoped authorization over the secure filter chain and real
 * embedded Postgres.
 *
 * <p>Plans here are owned by a seeded <i>report</i> ({@code auth0|report-ava}); the
 * acting reviewer is that report's seeded <i>manager</i> ({@code auth0|manager-mary},
 * from V7). The real {@code JwtPrincipalResolver} is active (no
 * {@code TestPrincipalConfig}), so the acting principal is the JWT subject selected
 * via {@link TestSecurityConfig#bearerFor(String)}.
 *
 * <p><b>Covers AE2.</b> {@link #managerReviewSucceedsAndStampsManager()},
 * {@link #ownerSelfReviewForbidden()}, and {@link #nonManagerReviewForbidden()} prove
 * the write is restricted to the owner's manager. The transition-independence case
 * (R17) and the upsert/absence/read-scope/length/auth cases round out the surface.
 */
@AutoConfigureMockMvc
@Transactional
class ManagerReviewControllerTest extends AbstractPostgresIT {

    private static final String OWNER = TestSecurityConfig.REPORT_SUBJECT;
    private static final String MANAGER = TestSecurityConfig.MANAGER_SUBJECT;
    private static final String OUTSIDER = TestSecurityConfig.OUTSIDER_SUBJECT;
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

    private String managerBearer() {
        return TestSecurityConfig.bearerFor(MANAGER);
    }

    private String ownerBearer() {
        return TestSecurityConfig.bearerFor(OWNER);
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

    // --- R17: transition is independent of review state (owner advances own plan) ---

    @Test
    void lockedPlanWithNoReviewStillAdvancesToReconciling() throws Exception {
        WeeklyPlan plan = savedPlan(PlanStatus.LOCKED);
        assertThat(reviewRepository.findByWeeklyPlanId(plan.getId())).isEmpty();

        mockMvc.perform(
                post("/api/lifecycle/plans/" + plan.getId() + "/transitions/start-reconciling")
                    .header(HttpHeaders.AUTHORIZATION, ownerBearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("RECONCILING"));

        assertThat(planRepository.findById(plan.getId()).orElseThrow().getStatus())
            .isEqualTo(PlanStatus.RECONCILING);
        assertThat(reviewRepository.findByWeeklyPlanId(plan.getId())).isEmpty();
    }

    // --- AE2: write restricted to the owner's manager ---

    @Test
    void managerReviewSucceedsAndStampsManager() throws Exception {
        WeeklyPlan plan = savedPlan(PlanStatus.LOCKED);

        mockMvc.perform(put("/api/lifecycle/plans/" + plan.getId() + "/review")
                .header(HttpHeaders.AUTHORIZATION, managerBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(reviewBody("Strong week")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.weeklyPlanId").value(plan.getId().toString()))
            .andExpect(jsonPath("$.reviewer").value(MANAGER))
            .andExpect(jsonPath("$.comment").value("Strong week"))
            .andExpect(jsonPath("$.reviewedAt").isNotEmpty());

        ManagerReview persisted =
            reviewRepository.findByWeeklyPlanId(plan.getId()).orElseThrow();
        assertThat(persisted.getReviewer()).isEqualTo(MANAGER);
        assertThat(persisted.getComment()).isEqualTo("Strong week");
        assertThat(persisted.getCreatedDate()).isNotNull();
    }

    @Test
    void ownerSelfReviewForbidden() throws Exception {
        WeeklyPlan plan = savedPlan(PlanStatus.LOCKED);

        mockMvc.perform(put("/api/lifecycle/plans/" + plan.getId() + "/review")
                .header(HttpHeaders.AUTHORIZATION, ownerBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(reviewBody("reviewing myself")))
            .andExpect(status().isForbidden());

        assertThat(reviewRepository.findByWeeklyPlanId(plan.getId())).isEmpty();
    }

    @Test
    void nonManagerReviewForbidden() throws Exception {
        WeeklyPlan plan = savedPlan(PlanStatus.LOCKED);

        mockMvc.perform(put("/api/lifecycle/plans/" + plan.getId() + "/review")
                .header(HttpHeaders.AUTHORIZATION, TestSecurityConfig.bearerFor(OUTSIDER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(reviewBody("not my report")))
            .andExpect(status().isForbidden());

        assertThat(reviewRepository.findByWeeklyPlanId(plan.getId())).isEmpty();
    }

    // --- review at LOCKED does not mutate the plan or its commitments (R16, Q(c)) ---

    @Test
    void reviewAtLockedDoesNotMutatePlanOrCommitments() throws Exception {
        WeeklyPlan plan = savedPlan(PlanStatus.LOCKED);
        Commitment commitment = savedCommitment(plan.getId());

        mockMvc.perform(put("/api/lifecycle/plans/" + plan.getId() + "/review")
                .header(HttpHeaders.AUTHORIZATION, managerBearer())
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
                .header(HttpHeaders.AUTHORIZATION, managerBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(reviewBody("first")))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String firstId = objectMapper.readTree(first).get("id").asText();

        mockMvc.perform(put("/api/lifecycle/plans/" + plan.getId() + "/review")
                .header(HttpHeaders.AUTHORIZATION, managerBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(reviewBody("second")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(firstId))
            .andExpect(jsonPath("$.comment").value("second"));

        assertThat(reviewRepository.count()).isEqualTo(1L);
    }

    // --- comment length bound ---

    @Test
    void tooLongCommentRejected() throws Exception {
        WeeklyPlan plan = savedPlan(PlanStatus.LOCKED);
        String tooLong = "x".repeat(ManagerReviewService.MAX_COMMENT_LENGTH + 1);

        mockMvc.perform(put("/api/lifecycle/plans/" + plan.getId() + "/review")
                .header(HttpHeaders.AUTHORIZATION, managerBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(reviewBody(tooLong)))
            .andExpect(status().isBadRequest());
    }

    // --- read scope: owner or manager 200; unrelated 403; absence 204 ---

    @Test
    void getReviewWhenNoneExistsReturnsNoContent() throws Exception {
        WeeklyPlan plan = savedPlan(PlanStatus.LOCKED);

        mockMvc.perform(get("/api/lifecycle/plans/" + plan.getId() + "/review")
                .header(HttpHeaders.AUTHORIZATION, managerBearer()))
            .andExpect(status().isNoContent());
    }

    @Test
    void managerAndOwnerCanReadReviewUnrelatedCannot() throws Exception {
        WeeklyPlan plan = savedPlan(PlanStatus.LOCKED);
        mockMvc.perform(put("/api/lifecycle/plans/" + plan.getId() + "/review")
                .header(HttpHeaders.AUTHORIZATION, managerBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(reviewBody("hello")))
            .andExpect(status().isOk());

        // Manager reads.
        mockMvc.perform(get("/api/lifecycle/plans/" + plan.getId() + "/review")
                .header(HttpHeaders.AUTHORIZATION, managerBearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.comment").value("hello"));

        // Owner (the IC) reads their own review.
        mockMvc.perform(get("/api/lifecycle/plans/" + plan.getId() + "/review")
                .header(HttpHeaders.AUTHORIZATION, ownerBearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.comment").value("hello"));

        // An unrelated principal cannot.
        mockMvc.perform(get("/api/lifecycle/plans/" + plan.getId() + "/review")
                .header(HttpHeaders.AUTHORIZATION, TestSecurityConfig.bearerFor(OUTSIDER)))
            .andExpect(status().isForbidden());
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
