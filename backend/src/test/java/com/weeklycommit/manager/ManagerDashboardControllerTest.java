package com.weeklycommit.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklycommit.lifecycle.Commitment;
import com.weeklycommit.lifecycle.CommitmentRepository;
import com.weeklycommit.lifecycle.ManagerReview;
import com.weeklycommit.lifecycle.ManagerReviewRepository;
import com.weeklycommit.lifecycle.PlanStatus;
import com.weeklycommit.lifecycle.WeekKey;
import com.weeklycommit.lifecycle.WeeklyPlan;
import com.weeklycommit.lifecycle.WeeklyPlanRepository;
import com.weeklycommit.support.AbstractPostgresIT;
import com.weeklycommit.support.TestSecurityConfig;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * HTTP-level coverage for the manager team roll-up (F-U3) over the secure filter chain
 * and real embedded Postgres, against the V7-seeded reporting edges (manager-mary -&gt;
 * ava/ben/cleo/dan).
 *
 * <p><b>Covers AE3</b> (a report with no current-week plan appears with a null status)
 * and <b>AE4</b> (the outcome spread groups commitments by their Outcome ancestor,
 * with above-Outcome links bucketed as "Other"), plus the non-manager 403, pagination,
 * and the review-done flag.
 */
@AutoConfigureMockMvc
@Transactional
class ManagerDashboardControllerTest extends AbstractPostgresIT {

    private static final String MANAGER = TestSecurityConfig.MANAGER_SUBJECT;
    private static final String AVA = "auth0|report-ava";
    private static final String BEN = "auth0|report-ben";
    private static final String CLEO = "auth0|report-cleo";
    private static final String DAN = "auth0|report-dan";

    // V3 seed nodes.
    private static final UUID SUP_ARR = UUID.fromString("44444444-4444-4444-4444-444444444441");
    private static final UUID SUP_NPS = UUID.fromString("44444444-4444-4444-4444-444444444442");
    private static final UUID DEFINING_OBJECTIVE =
        UUID.fromString("22222222-2222-2222-2222-222222222221");
    private static final String OUTCOME_ARR = "Increase ARR by 20%";
    private static final String OUTCOME_NPS = "Lift NPS to 50";

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

    @Autowired
    Clock clock;

    private String weekKey;

    @BeforeEach
    void setUp() {
        reviewRepository.deleteAllInBatch();
        commitmentRepository.deleteAllInBatch();
        planRepository.deleteAllInBatch();
        weekKey = WeekKey.current(clock);
    }

    private WeeklyPlan plan(String owner, PlanStatus status) {
        WeeklyPlan p = new WeeklyPlan();
        p.setOwner(owner);
        p.setWeekKey(weekKey);
        p.setStatus(status);
        return planRepository.saveAndFlush(p);
    }

    private void commitment(UUID planId, UUID rcdoNodeId) {
        Commitment c = new Commitment();
        c.setWeeklyPlanId(planId);
        c.setRcdoNodeId(rcdoNodeId);
        c.setTitle("c-" + rcdoNodeId);
        c.setPlanned(true);
        commitmentRepository.saveAndFlush(c);
    }

    private JsonNode getBoard(String sort) throws Exception {
        String body = mockMvc.perform(get("/api/manager/team" + sort)
                .header(HttpHeaders.AUTHORIZATION, TestSecurityConfig.bearerFor(MANAGER)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private JsonNode rowFor(JsonNode board, String reportSub) {
        for (JsonNode row : board.get("content")) {
            if (reportSub.equals(row.get("reportSub").asText())) {
                return row;
            }
        }
        throw new AssertionError("no row for " + reportSub);
    }

    @Test
    void boardShowsStatusSpreadReviewAndNoPlanRow() throws Exception {
        // Ava: LOCKED, 2 commitments under the ARR outcome + 1 under NPS, with a review.
        WeeklyPlan ava = plan(AVA, PlanStatus.LOCKED);
        commitment(ava.getId(), SUP_ARR);
        commitment(ava.getId(), SUP_ARR);
        commitment(ava.getId(), SUP_NPS);
        // Stamp a review directly so the flag is set without going through the write API.
        ManagerReview review = new ManagerReview();
        review.setWeeklyPlanId(ava.getId());
        review.setReviewer(MANAGER);
        review.setComment("seen");
        reviewRepository.saveAndFlush(review);

        // Ben: DRAFT, one commitment linked above Outcome level -> "Other".
        WeeklyPlan ben = plan(BEN, PlanStatus.DRAFT);
        commitment(ben.getId(), DEFINING_OBJECTIVE);

        // Cleo: RECONCILED, no commitments. Dan: no plan at all.
        plan(CLEO, PlanStatus.RECONCILED);

        JsonNode board = getBoard("?sort=reportSub,asc");
        assertThat(board.get("totalElements").asLong()).isEqualTo(4);

        JsonNode avaRow = rowFor(board, AVA);
        assertThat(avaRow.get("status").asText()).isEqualTo("LOCKED");
        assertThat(avaRow.get("planId").asText()).isEqualTo(ava.getId().toString());
        assertThat(avaRow.get("displayName").asText()).isEqualTo("Ava Stone");
        assertThat(avaRow.get("reviewExists").asBoolean()).isTrue();
        // Spread sorted by count desc: ARR (2) before NPS (1).
        JsonNode spread = avaRow.get("outcomeSpread");
        assertThat(spread.get(0).get("outcome").asText()).isEqualTo(OUTCOME_ARR);
        assertThat(spread.get(0).get("count").asLong()).isEqualTo(2);
        assertThat(spread.get(1).get("outcome").asText()).isEqualTo(OUTCOME_NPS);
        assertThat(spread.get(1).get("count").asLong()).isEqualTo(1);

        JsonNode benRow = rowFor(board, BEN);
        assertThat(benRow.get("status").asText()).isEqualTo("DRAFT");
        assertThat(benRow.get("outcomeSpread").get(0).get("outcome").asText()).isEqualTo("Other");
        assertThat(benRow.get("reviewExists").asBoolean()).isFalse();

        JsonNode cleoRow = rowFor(board, CLEO);
        assertThat(cleoRow.get("status").asText()).isEqualTo("RECONCILED");
        assertThat(cleoRow.get("outcomeSpread")).isEmpty();

        // AE3: Dan has no current-week plan -> null planId + status, empty spread.
        JsonNode danRow = rowFor(board, DAN);
        assertThat(danRow.get("planId").isNull()).isTrue();
        assertThat(danRow.get("status").isNull()).isTrue();
        assertThat(danRow.get("outcomeSpread")).isEmpty();
        assertThat(danRow.get("reviewExists").asBoolean()).isFalse();
    }

    @Test
    void nonManagerForbidden() throws Exception {
        mockMvc.perform(get("/api/manager/team")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    TestSecurityConfig.bearerFor(TestSecurityConfig.OUTSIDER_SUBJECT)))
            .andExpect(status().isForbidden());
    }

    @Test
    void paginationSlicesReports() throws Exception {
        JsonNode firstPage = getBoard("?page=0&size=2&sort=reportSub,asc");
        assertThat(firstPage.get("content")).hasSize(2);
        assertThat(firstPage.get("totalElements").asLong()).isEqualTo(4);
        assertThat(firstPage.get("totalPages").asInt()).isEqualTo(2);
        assertThat(firstPage.get("content").get(0).get("reportSub").asText()).isEqualTo(AVA);
        assertThat(firstPage.get("content").get(1).get("reportSub").asText()).isEqualTo(BEN);
    }

    @Test
    void withoutTokenRejected() throws Exception {
        mockMvc.perform(get("/api/manager/team"))
            .andExpect(status().isUnauthorized());
    }
}
