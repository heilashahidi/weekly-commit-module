package com.weeklycommit.manager;

import static org.assertj.core.api.Assertions.assertThat;

import com.weeklycommit.lifecycle.Commitment;
import com.weeklycommit.lifecycle.CommitmentRepository;
import com.weeklycommit.lifecycle.WeekKey;
import com.weeklycommit.lifecycle.WeeklyPlanRepository;
import com.weeklycommit.support.AbstractPostgresIT;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies the dev demo seeder (F-U7) populates a current-week board for the seeded
 * manager's reports and is idempotent. The seeder is {@code @Profile("dev")}, so it is
 * constructed directly here (the test profile does not auto-run it) and exercised
 * against the V7-seeded reporting edges.
 */
@Transactional
class DemoDataSeederTest extends AbstractPostgresIT {

    private static final String MANAGER = "auth0|manager-mary";

    @Autowired
    ReportingRepository reportingRepository;

    @Autowired
    WeeklyPlanRepository planRepository;

    @Autowired
    CommitmentRepository commitmentRepository;

    @Autowired
    Clock clock;

    private DemoDataSeeder seeder;
    private String weekKey;

    @BeforeEach
    void setUp() {
        commitmentRepository.deleteAllInBatch();
        planRepository.deleteAllInBatch();
        seeder = new DemoDataSeeder(reportingRepository, planRepository, commitmentRepository, clock);
        weekKey = WeekKey.current(clock);
    }

    @Test
    void seedsCurrentWeekPlansSpanningBothOutcomesAndLeavesLastReportPlanless() {
        seeder.run();

        List<ReportingEdge> reports = reportingRepository.findByManagerSubOrderByReportSubAsc(MANAGER);
        assertThat(reports).hasSizeGreaterThanOrEqualTo(4); // V7 seeds ava/ben/cleo/dan

        // The first three reports get current-week plans...
        long seededPlans =
            reports.stream()
                .limit(3)
                .filter(r -> planRepository.findByOwnerAndWeekKey(r.getReportSub(), weekKey).isPresent())
                .count();
        assertThat(seededPlans).isEqualTo(3);

        // ...and at least one report is intentionally left plan-less (the "no plan" row).
        long planless =
            reports.stream()
                .filter(r -> planRepository.findByOwnerAndWeekKey(r.getReportSub(), weekKey).isEmpty())
                .count();
        assertThat(planless).isGreaterThanOrEqualTo(1);

        // Commitments span both seeded Outcomes across the team (differentiated spread).
        List<Commitment> all = commitmentRepository.findAll();
        assertThat(all).extracting(Commitment::getRcdoNodeId).doesNotContainNull();
        assertThat(all.stream().map(Commitment::getRcdoNodeId).distinct().count())
            .isGreaterThanOrEqualTo(2);
    }

    @Test
    void rerunIsIdempotent() {
        seeder.run();
        long plansAfterFirst = planRepository.count();
        long commitmentsAfterFirst = commitmentRepository.count();

        seeder.run();

        assertThat(planRepository.count()).isEqualTo(plansAfterFirst);
        assertThat(commitmentRepository.count()).isEqualTo(commitmentsAfterFirst);
    }
}
