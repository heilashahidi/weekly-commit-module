package com.weeklycommit.common;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TestAuditEntityRepository extends JpaRepository<TestAuditEntity, Long> {
}
