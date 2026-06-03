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
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * A single weekly commitment belonging to a {@link WeeklyPlan}. Every commitment
 * links to strategy through a single {@code rcdo_node_id} FK (KTD 3) — a plain
 * UUID with a DB-level foreign key to {@code rcdo_node}, mirroring
 * {@code RcdoNode.parentId}, not a JPA association. The link target's existence
 * is validated in the service layer before save (R6).
 *
 * <p>{@link #planned} distinguishes commitments created while the plan was in
 * {@code DRAFT} ({@code true}, immutable once locked — R7) from unplanned
 * commitments appended after lock ({@code false} — R8). {@link #reconciliationStatus}
 * is null until reconciliation, then one of {@link ReconciliationStatus} (KTD 5).
 * {@link #carriedFromId} + {@link #carryWeekCount} record carry-forward lineage
 * (R12–R15): the source commitment a carried one descends from and how many weeks
 * it has rolled over.
 *
 * <p>Extends {@link AbstractAuditingEntity} for created/modified auditing. The
 * table is owned by Flyway ({@code V4__weekly_plan_and_commitment.sql}); this
 * mapping must match that DDL exactly because Hibernate runs in {@code validate}.
 */
@Entity
@Table(name = "commitment")
@Getter
@Setter
public class Commitment extends AbstractAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Owning plan (DB FK to weekly_plan); plain UUID per the repo convention. */
    @Column(name = "weekly_plan_id", nullable = false)
    private UUID weeklyPlanId;

    /** The linked RCDO node (DB FK to rcdo_node), the single strategy link (KTD 3, R6). */
    @Column(name = "rcdo_node_id", nullable = false)
    private UUID rcdoNodeId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    /** True = planned in DRAFT (immutable once locked); false = unplanned add after lock. */
    @Column(name = "planned", nullable = false)
    private boolean planned;

    /** Reconciliation outcome; null until reconciliation (KTD 5). */
    @Enumerated(EnumType.STRING)
    @Column(name = "reconciliation_status", length = 16)
    private ReconciliationStatus reconciliationStatus;

    /** Optional free-text reconciliation note. */
    @Column(name = "reconciliation_note")
    private String reconciliationNote;

    /** Source commitment this one was carried from; null if fresh (R14). */
    @Column(name = "carried_from_id")
    private UUID carriedFromId;

    /** Number of weeks this commitment has rolled over (R14). */
    @Column(name = "carry_week_count", nullable = false)
    private int carryWeekCount;
}
