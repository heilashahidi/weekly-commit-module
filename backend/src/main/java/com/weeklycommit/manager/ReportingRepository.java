package com.weeklycommit.manager;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for the manager -> report mapping ({@link ReportingEdge}).
 * Backs the manager-scoped authorization check (workstream F) and the team
 * roll-up.
 */
public interface ReportingRepository extends JpaRepository<ReportingEdge, UUID> {

    /** True iff {@code managerSub} manages {@code reportSub} (the authorization predicate). */
    boolean existsByManagerSubAndReportSub(String managerSub, String reportSub);

    /** True iff the principal is a manager at all (has at least one report). */
    boolean existsByManagerSub(String managerSub);

    /** A manager's direct reports, paginated, for the team roll-up. */
    Page<ReportingEdge> findByManagerSub(String managerSub, Pageable pageable);

    /**
     * A manager's direct reports (unpaged), deterministically ordered — used by the dev
     * demo seeder, whose per-report demo shape is assigned by position.
     */
    List<ReportingEdge> findByManagerSubOrderByReportSubAsc(String managerSub);
}
