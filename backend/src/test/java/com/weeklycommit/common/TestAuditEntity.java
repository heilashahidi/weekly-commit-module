package com.weeklycommit.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Test-only entity that extends {@link AbstractAuditingEntity} so auditing
 * behaviour can be exercised against a real table. Its table is created by
 * Hibernate (ddl-auto: update) in the test profile, not by a production migration.
 */
@Entity
@Table(name = "test_audit_entity")
@Getter
@Setter
public class TestAuditEntity extends AbstractAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name")
    private String name;
}
