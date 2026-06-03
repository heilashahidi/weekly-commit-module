package com.weeklycommit.rcdo;

import java.util.List;
import java.util.UUID;

/**
 * API response shape for an RCDO node. A record (not the entity) so responses
 * don't leak persistence concerns (audit fields, lazy associations) and the
 * frontend gets a clean nested tree.
 *
 * <p>{@code children} always holds the full nested subtree (never null, possibly
 * empty), the same shape from both {@code /tree} and {@code /nodes/{id}}.
 */
public record RcdoNodeDto(
    UUID id,
    RcdoNodeType nodeType,
    String title,
    String description,
    UUID parentId,
    List<RcdoNodeDto> children) {
}
