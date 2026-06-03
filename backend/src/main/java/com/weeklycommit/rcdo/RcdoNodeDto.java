package com.weeklycommit.rcdo;

import java.util.List;
import java.util.UUID;

/**
 * API response shape for an RCDO node. A record (not the entity) so responses
 * don't leak persistence concerns (audit fields, lazy associations) and the
 * frontend gets a clean nested tree.
 *
 * <p>For the full-tree endpoint, {@code children} holds the nested subtree. For
 * single-node resolve, {@code children} holds the node's direct children only.
 */
public record RcdoNodeDto(
    UUID id,
    RcdoNodeType nodeType,
    String title,
    String description,
    UUID parentId,
    List<RcdoNodeDto> children) {
}
