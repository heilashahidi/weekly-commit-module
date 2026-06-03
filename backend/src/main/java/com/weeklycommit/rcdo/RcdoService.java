package com.weeklycommit.rcdo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Read-only access to the RCDO hierarchy. Assembles the tree in memory from a
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
     * under {@code children}. Nodes whose {@code parentId} resolves to no loaded
     * node are treated as roots (defensive: a well-formed tree has only Rally
     * Cries at the top).
     */
    public List<RcdoNodeDto> getTree() {
        List<RcdoNode> all = repository.findAll();

        // First pass: a mutable DTO holder per node id.
        Map<UUID, MutableNode> byId = new HashMap<>();
        for (RcdoNode node : all) {
            byId.put(node.getId(), new MutableNode(node));
        }

        // Second pass: link each node to its parent; collect roots.
        List<MutableNode> roots = new ArrayList<>();
        for (MutableNode current : byId.values()) {
            UUID parentId = current.source.getParentId();
            MutableNode parent = parentId == null ? null : byId.get(parentId);
            if (parent == null) {
                roots.add(current);
            } else {
                parent.children.add(current);
            }
        }

        List<RcdoNodeDto> result = new ArrayList<>();
        for (MutableNode root : roots) {
            result.add(root.toDto());
        }
        return result;
    }

    /**
     * A single node with its direct children, or empty if no node has that id.
     */
    public java.util.Optional<RcdoNodeDto> getNode(UUID id) {
        return repository.findById(id).map(node -> {
            List<RcdoNodeDto> children = new ArrayList<>();
            for (RcdoNode child : repository.findAll()) {
                if (id.equals(child.getParentId())) {
                    children.add(leafDto(child));
                }
            }
            return new RcdoNodeDto(
                node.getId(), node.getNodeType(), node.getTitle(),
                node.getDescription(), node.getParentId(), children);
        });
    }

    private static RcdoNodeDto leafDto(RcdoNode node) {
        return new RcdoNodeDto(
            node.getId(), node.getNodeType(), node.getTitle(),
            node.getDescription(), node.getParentId(), List.of());
    }

    /** Mutable assembly holder so children can be appended during the link pass. */
    private static final class MutableNode {
        private final RcdoNode source;
        private final List<MutableNode> children = new ArrayList<>();

        private MutableNode(RcdoNode source) {
            this.source = source;
        }

        private RcdoNodeDto toDto() {
            List<RcdoNodeDto> childDtos = new ArrayList<>();
            for (MutableNode child : children) {
                childDtos.add(child.toDto());
            }
            return new RcdoNodeDto(
                source.getId(), source.getNodeType(), source.getTitle(),
                source.getDescription(), source.getParentId(), childDtos);
        }
    }
}
