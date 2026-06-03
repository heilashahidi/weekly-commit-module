package com.weeklycommit.rcdo;

/**
 * The four levels of the RCDO strategy hierarchy, from top to bottom. A node's
 * legal parent is determined by its type: a DEFINING_OBJECTIVE hangs off a
 * RALLY_CRY, an OUTCOME off a DEFINING_OBJECTIVE, a SUPPORTING_OUTCOME off an
 * OUTCOME. RALLY_CRY is a root (no parent). Weekly commitments may link to any
 * level, but SUPPORTING_OUTCOME is the common path.
 */
public enum RcdoNodeType {
    RALLY_CRY,
    DEFINING_OBJECTIVE,
    OUTCOME,
    SUPPORTING_OUTCOME
}
