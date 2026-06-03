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

    /** Direct children of a node (index-backed by idx_rcdo_node_parent_id). */
    List<RcdoNode> findByParentId(UUID parentId);

    List<RcdoNode> findByNodeType(RcdoNodeType nodeType);
}
