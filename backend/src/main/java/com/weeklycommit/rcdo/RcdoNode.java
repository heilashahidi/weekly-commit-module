package com.weeklycommit.rcdo;

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
 * A single node in the RCDO strategy hierarchy. All four levels share one table
 * (adjacency list): the {@link RcdoNodeType} discriminator records the level and
 * {@code parentId} links a node to its parent (null for a Rally Cry root). One
 * table means a later weekly commitment links to strategy through a single
 * {@code rcdo_node_id} foreign key regardless of which level it targets.
 *
 * <p>Extends {@link AbstractAuditingEntity} for created/modified auditing (PRD
 * requirement). The table is owned by Flyway ({@code V2__rcdo_tables.sql}); this
 * mapping must match that DDL exactly because Hibernate runs in {@code validate}.
 */
@Entity
@Table(name = "rcdo_node")
@Getter
@Setter
public class RcdoNode extends AbstractAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "node_type", nullable = false, length = 32)
    private RcdoNodeType nodeType;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description")
    private String description;

    /** Parent node id; null for a {@link RcdoNodeType#RALLY_CRY} root. */
    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
