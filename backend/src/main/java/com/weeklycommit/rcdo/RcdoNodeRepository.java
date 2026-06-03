package com.weeklycommit.rcdo;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link RcdoNode}. The tree is small and read-mostly,
 * so {@link #findAll()} (inherited) backs in-memory tree assembly in the service
 * layer — no recursive query or lazy association traversal needed.
 */
public interface RcdoNodeRepository extends JpaRepository<RcdoNode, UUID> {

    /** Root nodes (the Rally Cries). */
    List<RcdoNode> findByParentIdIsNull();

    List<RcdoNode> findByNodeType(RcdoNodeType nodeType);
}
