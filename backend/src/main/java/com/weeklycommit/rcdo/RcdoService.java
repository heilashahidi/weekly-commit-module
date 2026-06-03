package com.weeklycommit.rcdo;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Read-only access to the RCDO hierarchy. Both endpoints assemble nested
 * {@link RcdoNodeDto} subtrees in memory from a single
 * {@link RcdoNodeRepository#findAll()} (O(n), no per-node lazy loads or recursive
 * queries), which suits a small fixed-depth tree. {@code children} carries the
 * full nested subtree in every response — {@link #getTree()} returns the roots,
 * {@link #getNode(UUID)} returns one node — so consumers see one consistent shape.
 */
@Service
public class RcdoService {

    private final RcdoNodeRepository repository;

    public RcdoService(RcdoNodeRepository repository) {
        this.repository = repository;
    }

    /**
     * The full hierarchy as a list of root nodes, each with its nested subtree.
     * A node whose {@code parentId} resolves to no loaded node is treated as a
     * root (defensive: a well-formed tree has only Rally Cries at the top, and
     * the self-FK makes dangling parents impossible).
     */
    public List<RcdoNodeDto> getTree() {
        List<RcdoNode> all = repository.findAll();
        Map<UUID, List<RcdoNode>> childrenByParent = childrenByParent(all);
        Set<UUID> ids = all.stream().map(RcdoNode::getId).collect(Collectors.toSet());

        return all.stream()
            .filter(n -> n.getParentId() == null || !ids.contains(n.getParentId()))
            .sorted(Comparator.comparingInt(RcdoNode::getSortOrder))
            .map(root -> toDto(root, childrenByParent, new HashSet<>()))
            .toList();
    }

    /**
     * A single node with its full nested subtree, or empty if no node has that id.
     */
    public Optional<RcdoNodeDto> getNode(UUID id) {
        List<RcdoNode> all = repository.findAll();
        Map<UUID, List<RcdoNode>> childrenByParent = childrenByParent(all);

        return all.stream()
            .filter(n -> id.equals(n.getId()))
            .findFirst()
            .map(node -> toDto(node, childrenByParent, new HashSet<>()));
    }

    private Map<UUID, List<RcdoNode>> childrenByParent(List<RcdoNode> all) {
        return all.stream()
            .filter(n -> n.getParentId() != null)
            .collect(Collectors.groupingBy(RcdoNode::getParentId));
    }

    /**
     * Recursively builds the nested subtree rooted at {@code node}, ordering
     * siblings by {@code sortOrder}. {@code ancestors} tracks the current DFS path
     * so a {@code parent_id} cycle (which the self-FK alone does not prevent, and
     * which a future write path could introduce) breaks the recursion instead of
     * looping forever or overflowing the stack — the cyclic edge is simply not
     * followed.
     */
    private RcdoNodeDto toDto(
            RcdoNode node, Map<UUID, List<RcdoNode>> childrenByParent, Set<UUID> ancestors) {
        ancestors.add(node.getId());
        List<RcdoNodeDto> children = childrenByParent.getOrDefault(node.getId(), List.of()).stream()
            .filter(child -> !ancestors.contains(child.getId()))
            .sorted(Comparator.comparingInt(RcdoNode::getSortOrder))
            .map(child -> toDto(child, childrenByParent, ancestors))
            .toList();
        ancestors.remove(node.getId());
        return new RcdoNodeDto(
            node.getId(), node.getNodeType(), node.getTitle(),
            node.getDescription(), node.getParentId(), children);
    }
}
