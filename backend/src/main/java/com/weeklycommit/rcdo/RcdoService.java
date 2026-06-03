package com.weeklycommit.rcdo;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Read-only access to the RCDO hierarchy. The tree is assembled in memory from a
 * single {@link RcdoNodeRepository#findAll()} (O(n), no per-node lazy loads or
 * recursive queries), which suits a small fixed-depth tree.
 */
@Service
public class RcdoService {

    private final RcdoNodeRepository repository;

    public RcdoService(RcdoNodeRepository repository) {
        this.repository = repository;
    }

    /**
     * The full hierarchy as a list of root nodes, each with its subtree nested
     * under {@code children}. A node whose {@code parentId} resolves to no loaded
     * node is treated as a root (defensive: a well-formed tree has only Rally
     * Cries at the top, and the self-FK makes true orphans impossible).
     */
    public List<RcdoNodeDto> getTree() {
        List<RcdoNode> all = repository.findAll();
        Set<UUID> ids = all.stream().map(RcdoNode::getId).collect(Collectors.toSet());
        Map<UUID, List<RcdoNode>> childrenByParent = all.stream()
            .filter(n -> n.getParentId() != null && ids.contains(n.getParentId()))
            .collect(Collectors.groupingBy(RcdoNode::getParentId));

        return all.stream()
            .filter(n -> n.getParentId() == null || !ids.contains(n.getParentId()))
            .map(root -> toDto(root, childrenByParent))
            .toList();
    }

    /**
     * A single node with its direct children, or empty if no node has that id.
     */
    public Optional<RcdoNodeDto> getNode(UUID id) {
        return repository.findById(id).map(node -> {
            List<RcdoNodeDto> children = repository.findByParentId(id).stream()
                .map(child -> dto(child, List.of()))
                .toList();
            return dto(node, children);
        });
    }

    /** Recursively builds the nested subtree rooted at {@code node}. */
    private RcdoNodeDto toDto(RcdoNode node, Map<UUID, List<RcdoNode>> childrenByParent) {
        List<RcdoNodeDto> children = childrenByParent.getOrDefault(node.getId(), List.of()).stream()
            .map(child -> toDto(child, childrenByParent))
            .toList();
        return dto(node, children);
    }

    private static RcdoNodeDto dto(RcdoNode node, List<RcdoNodeDto> children) {
        return new RcdoNodeDto(
            node.getId(), node.getNodeType(), node.getTitle(),
            node.getDescription(), node.getParentId(), children);
    }
}
