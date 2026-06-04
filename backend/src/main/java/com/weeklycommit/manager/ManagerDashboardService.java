package com.weeklycommit.manager;

import com.weeklycommit.config.PrincipalResolver;
import com.weeklycommit.lifecycle.Commitment;
import com.weeklycommit.lifecycle.CommitmentRepository;
import com.weeklycommit.lifecycle.ManagerReviewRepository;
import com.weeklycommit.lifecycle.WeekKey;
import com.weeklycommit.lifecycle.WeeklyPlan;
import com.weeklycommit.lifecycle.WeeklyPlanRepository;
import com.weeklycommit.rcdo.RcdoService;
import java.time.Clock;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The manager's current-week team roll-up (F-U3). For the calling manager it returns
 * one {@link TeamRowDto} per direct report: the report's current-week plan status (or
 * {@code null} for "no plan yet"), the spread of RCDO Outcomes their commitments point
 * at, and whether a review exists.
 *
 * <p><b>Authorization.</b> A principal with no reports is not a manager and gets a 403
 * (not a silent empty page) — the endpoint never reveals another manager's team, and a
 * non-manager cannot probe it for an empty-but-200 response.
 *
 * <p><b>No N+1.</b> The board is assembled from a bounded set of queries regardless of
 * report count: one paged reporting lookup, one batched plan lookup, one batched
 * commitment lookup, one batched review-id projection, and one in-memory RCDO tree load
 * (reused for every Outcome resolution). Commitments linked at or above Outcome level
 * bucket under {@code "Other"}.
 */
@Service
public class ManagerDashboardService {

    private static final String OTHER_OUTCOME = "Other";

    private final ReportingRepository reportingRepository;
    private final WeeklyPlanRepository planRepository;
    private final CommitmentRepository commitmentRepository;
    private final ManagerReviewRepository reviewRepository;
    private final RcdoService rcdoService;
    private final PrincipalResolver principalResolver;
    private final Clock clock;

    public ManagerDashboardService(
            ReportingRepository reportingRepository,
            WeeklyPlanRepository planRepository,
            CommitmentRepository commitmentRepository,
            ManagerReviewRepository reviewRepository,
            RcdoService rcdoService,
            PrincipalResolver principalResolver,
            Clock clock) {
        this.reportingRepository = reportingRepository;
        this.planRepository = planRepository;
        this.commitmentRepository = commitmentRepository;
        this.reviewRepository = reviewRepository;
        this.rcdoService = rcdoService;
        this.principalResolver = principalResolver;
        this.clock = clock;
    }

    /** The calling manager's current-week team board, paginated over their direct reports. */
    @Transactional(readOnly = true)
    public PageDto<TeamRowDto> getTeamWeek(Pageable pageable) {
        String manager = principalResolver.currentPrincipal();
        if (!reportingRepository.existsByManagerSub(manager)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a manager");
        }

        Page<ReportingEdge> edges = reportingRepository.findByManagerSub(manager, pageable);
        List<String> reportSubs =
            edges.getContent().stream().map(ReportingEdge::getReportSub).toList();

        String weekKey = WeekKey.current(clock);
        List<WeeklyPlan> plans =
            reportSubs.isEmpty()
                ? List.of()
                : planRepository.findByOwnerInAndWeekKey(reportSubs, weekKey);
        Map<String, WeeklyPlan> planByOwner =
            plans.stream().collect(Collectors.toMap(WeeklyPlan::getOwner, Function.identity()));
        List<UUID> planIds = plans.stream().map(WeeklyPlan::getId).toList();

        Map<UUID, List<Commitment>> commitmentsByPlan =
            planIds.isEmpty()
                ? Map.of()
                : commitmentRepository.findByWeeklyPlanIdIn(planIds).stream()
                    .collect(Collectors.groupingBy(Commitment::getWeeklyPlanId));
        Set<UUID> reviewedPlanIds =
            planIds.isEmpty()
                ? Set.of()
                : new HashSet<>(reviewRepository.findReviewedPlanIds(planIds));
        Map<UUID, String> outcomeTitles = rcdoService.outcomeTitlesByNodeId();

        List<TeamRowDto> rows =
            edges.getContent().stream()
                .map(edge -> toRow(edge, planByOwner, commitmentsByPlan, reviewedPlanIds, outcomeTitles))
                .toList();

        return PageDto.from(edges, rows);
    }

    private TeamRowDto toRow(
            ReportingEdge edge,
            Map<String, WeeklyPlan> planByOwner,
            Map<UUID, List<Commitment>> commitmentsByPlan,
            Set<UUID> reviewedPlanIds,
            Map<UUID, String> outcomeTitles) {
        WeeklyPlan plan = planByOwner.get(edge.getReportSub());
        if (plan == null) {
            return new TeamRowDto(
                edge.getReportSub(), edge.getReportDisplayName(), null, List.of(), false);
        }
        List<OutcomeCountDto> spread =
            outcomeSpread(commitmentsByPlan.getOrDefault(plan.getId(), List.of()), outcomeTitles);
        return new TeamRowDto(
            edge.getReportSub(),
            edge.getReportDisplayName(),
            plan.getStatus(),
            spread,
            reviewedPlanIds.contains(plan.getId()));
    }

    /** Groups a plan's commitments by their OUTCOME ancestor title ("Other" if none). */
    private List<OutcomeCountDto> outcomeSpread(
            List<Commitment> commitments, Map<UUID, String> outcomeTitles) {
        Map<String, Long> counts =
            commitments.stream()
                .collect(
                    Collectors.groupingBy(
                        c -> outcomeTitles.getOrDefault(c.getRcdoNodeId(), OTHER_OUTCOME),
                        Collectors.counting()));
        return counts.entrySet().stream()
            .sorted(
                Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue)
                    .reversed()
                    .thenComparing(Map.Entry::getKey))
            .map(e -> new OutcomeCountDto(e.getKey(), e.getValue()))
            .toList();
    }
}
