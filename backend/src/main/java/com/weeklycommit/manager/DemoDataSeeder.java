package com.weeklycommit.manager;

import com.weeklycommit.lifecycle.Commitment;
import com.weeklycommit.lifecycle.CommitmentRepository;
import com.weeklycommit.lifecycle.PlanStatus;
import com.weeklycommit.lifecycle.WeekKey;
import com.weeklycommit.lifecycle.WeeklyPlan;
import com.weeklycommit.lifecycle.WeeklyPlanRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Dev-only demo data so the manager dashboard is populated and differentiated out of
 * the box (F-U7). Runs once at startup under the {@code dev} profile.
 *
 * <p>Weekly plans are created at runtime keyed by the current ISO week (a static
 * Flyway seed can't know which week "now" is), and only the manager -&gt; report
 * <em>edges</em> are seeded by migration (V7). This runner closes that gap: for the
 * seeded reports of {@code auth0|manager-mary} it creates a current-week plan with
 * varied statuses and RCDO-linked commitments that span both seeded Outcomes (so the
 * outcome spread differentiates), and deliberately leaves the last report plan-less so
 * the board's "no plan yet" row is demonstrable. Idempotent: a report whose
 * current-week plan already exists is skipped, so restarts never duplicate.
 *
 * <p>Not a correctness dependency — tests use their own fixtures, not this runner.
 */
@Component
@Profile("dev")
public class DemoDataSeeder implements CommandLineRunner {

    private static final String DEMO_MANAGER = "auth0|manager-mary";

    // V3-seeded Supporting Outcomes under two different Outcomes (ARR vs NPS).
    private static final UUID SUP_ARR = UUID.fromString("44444444-4444-4444-4444-444444444441");
    private static final UUID SUP_NPS = UUID.fromString("44444444-4444-4444-4444-444444444442");

    /** Per-report demo shape, applied in reporting-edge order; reports beyond this list get no plan. */
    private static final List<DemoPlan> DEMO_PLANS =
        List.of(
            new DemoPlan(PlanStatus.LOCKED, List.of(SUP_ARR, SUP_ARR, SUP_NPS)),
            new DemoPlan(PlanStatus.DRAFT, List.of(SUP_NPS, SUP_NPS)),
            new DemoPlan(PlanStatus.RECONCILED, List.of(SUP_ARR)));

    private final ReportingRepository reportingRepository;
    private final WeeklyPlanRepository planRepository;
    private final CommitmentRepository commitmentRepository;
    private final Clock clock;

    public DemoDataSeeder(
            ReportingRepository reportingRepository,
            WeeklyPlanRepository planRepository,
            CommitmentRepository commitmentRepository,
            Clock clock) {
        this.reportingRepository = reportingRepository;
        this.planRepository = planRepository;
        this.commitmentRepository = commitmentRepository;
        this.clock = clock;
    }

    @Override
    public void run(String... args) {
        String weekKey = WeekKey.current(clock);
        List<ReportingEdge> reports = reportingRepository.findByManagerSub(DEMO_MANAGER);
        for (int i = 0; i < reports.size() && i < DEMO_PLANS.size(); i++) {
            seedPlan(reports.get(i).getReportSub(), weekKey, DEMO_PLANS.get(i));
        }
    }

    private void seedPlan(String owner, String weekKey, DemoPlan demo) {
        if (planRepository.findByOwnerAndWeekKey(owner, weekKey).isPresent()) {
            return; // idempotent — already seeded this week
        }
        WeeklyPlan plan = new WeeklyPlan();
        plan.setOwner(owner);
        plan.setWeekKey(weekKey);
        plan.setStatus(demo.status());
        WeeklyPlan saved = planRepository.save(plan);

        for (UUID nodeId : demo.rcdoNodeIds()) {
            Commitment c = new Commitment();
            c.setWeeklyPlanId(saved.getId());
            c.setRcdoNodeId(nodeId);
            c.setTitle("Demo commitment");
            c.setPlanned(true);
            commitmentRepository.save(c);
        }
    }

    private record DemoPlan(PlanStatus status, List<UUID> rcdoNodeIds) {}
}
