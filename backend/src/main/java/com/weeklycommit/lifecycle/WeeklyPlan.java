package com.weeklycommit.lifecycle;

import com.weeklycommit.common.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * A single IC's plan for one ISO week. Identity is (owner, week_key): one plan
 * per principal per org-wide week (KTD 7), enforced by a unique constraint. The
 * plan moves through {@link PlanStatus} ({@code DRAFT -> LOCKED -> RECONCILING ->
 * RECONCILED}); {@link #lockType} records lock provenance (R4) and {@link #noPlan}
 * flags an auto-locked empty draft (R5) — both kept as their own columns so
 * managers and metrics can read them directly (KTD 2).
 *
 * <p>{@link #statusDeadline} is the timestamp by which the current state must be
 * advanced; the deadline backstop (U7) finds overdue plans by it. Nullable so a
 * plan without a scheduled deadline is simply never swept.
 *
 * <p>Extends {@link AbstractAuditingEntity} for created/modified auditing. The
 * table is owned by Flyway ({@code V4__weekly_plan_and_commitment.sql}); this
 * mapping must match that DDL exactly because Hibernate runs in {@code validate}.
 */
@Entity
@Table(
    name = "weekly_plan",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_weekly_plan_owner_week_key",
            columnNames = {"owner", "week_key"}))
@Getter
@Setter
public class WeeklyPlan extends AbstractAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** The plan owner: the JWT principal string (KTD 7). */
    @Column(name = "owner", nullable = false, length = 255)
    private String owner;

    /** ISO-8601 week key, e.g. {@code 2026-W23} (KTD 7). */
    @Column(name = "week_key", nullable = false, length = 16)
    private String weekKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PlanStatus status;

    /** Lock provenance; null until the plan leaves {@code DRAFT} (R4). */
    @Enumerated(EnumType.STRING)
    @Column(name = "lock_type", length = 16)
    private LockType lockType;

    /** True when an auto-locked plan had zero commitments (R5). */
    @Column(name = "no_plan", nullable = false)
    private boolean noPlan;

    /** When the current state must be advanced by; backstop sweeps overdue plans (U7). */
    @Column(name = "status_deadline")
    private Instant statusDeadline;
}
