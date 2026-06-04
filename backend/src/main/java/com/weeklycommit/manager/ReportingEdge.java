package com.weeklycommit.manager;

import com.weeklycommit.common.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * One manager -> direct-report edge (workstream F). The org model is a seeded
 * mapping keyed by principal (JWT sub) strings rather than a User entity: a
 * principal is a manager iff it appears as a {@code managerSub} on at least one
 * edge, and a manager may act on a plan iff its owner appears as a
 * {@code reportSub} under that manager.
 *
 * <p>The table is owned by Flyway ({@code V7__reporting_mapping_and_seed.sql});
 * this mapping must match that DDL exactly because Hibernate runs in
 * {@code validate}. Extends {@link AbstractAuditingEntity} for created/modified
 * auditing (PRD requirement); the seed migration sets the audit columns
 * explicitly since auditing listeners do not fire on raw SQL inserts.
 */
@Entity
@Table(name = "reporting_edge")
@Getter
@Setter
public class ReportingEdge extends AbstractAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "manager_sub", nullable = false, length = 255)
    private String managerSub;

    @Column(name = "report_sub", nullable = false, length = 255)
    private String reportSub;

    @Column(name = "report_display_name", nullable = false, length = 255)
    private String reportDisplayName;
}
