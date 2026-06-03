package com.weeklycommit.rcdo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Pure unit coverage of the in-memory tree assembly — no Spring context, repository
 * mocked. Proves a flat node list with parent_id links assembles into the correct
 * nested shape, sibling ordering, and the edge cases (orphan, empty, cycle).
 */
@ExtendWith(MockitoExtension.class)
class RcdoServiceTest {

    @Mock
    RcdoNodeRepository repository;

    @InjectMocks
    RcdoService service;

    private RcdoNode node(UUID id, RcdoNodeType type, String title, UUID parentId) {
        return node(id, type, title, parentId, 0);
    }

    private RcdoNode node(UUID id, RcdoNodeType type, String title, UUID parentId, int sortOrder) {
        RcdoNode n = new RcdoNode();
        n.setId(id);
        n.setNodeType(type);
        n.setTitle(title);
        n.setParentId(parentId);
        n.setSortOrder(sortOrder);
        return n;
    }

    @Test
    void assemblesFlatListIntoNestedTree() {
        UUID rally = UUID.randomUUID();
        UUID dobj = UUID.randomUUID();
        UUID outcome = UUID.randomUUID();
        UUID support = UUID.randomUUID();
        when(repository.findAll()).thenReturn(List.of(
            node(rally, RcdoNodeType.RALLY_CRY, "RC", null),
            node(dobj, RcdoNodeType.DEFINING_OBJECTIVE, "DO", rally),
            node(outcome, RcdoNodeType.OUTCOME, "O", dobj),
            node(support, RcdoNodeType.SUPPORTING_OUTCOME, "SO", outcome)));

        List<RcdoNodeDto> tree = service.getTree();

        assertThat(tree).hasSize(1);
        RcdoNodeDto root = tree.get(0);
        assertThat(root.id()).isEqualTo(rally);
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).children()).hasSize(1);
        assertThat(root.children().get(0).children().get(0).children().get(0).nodeType())
            .isEqualTo(RcdoNodeType.SUPPORTING_OUTCOME);
    }

    @Test
    void ordersSiblingsBySortOrder() {
        UUID rally = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        // Supplied out of order; expect sortOrder to drive the result order.
        when(repository.findAll()).thenReturn(List.of(
            node(rally, RcdoNodeType.RALLY_CRY, "RC", null),
            node(second, RcdoNodeType.DEFINING_OBJECTIVE, "second", rally, 1),
            node(first, RcdoNodeType.DEFINING_OBJECTIVE, "first", rally, 0)));

        RcdoNodeDto root = service.getTree().get(0);

        assertThat(root.children()).extracting(RcdoNodeDto::title).containsExactly("first", "second");
    }

    @Test
    void leafNodeHasNoChildren() {
        UUID rally = UUID.randomUUID();
        when(repository.findAll()).thenReturn(List.of(
            node(rally, RcdoNodeType.RALLY_CRY, "RC", null)));

        assertThat(service.getTree().get(0).children()).isEmpty();
    }

    @Test
    void nodeWithMissingParentIsTreatedAsRoot() {
        UUID orphan = UUID.randomUUID();
        when(repository.findAll()).thenReturn(List.of(
            node(orphan, RcdoNodeType.OUTCOME, "orphan", UUID.randomUUID())));

        List<RcdoNodeDto> tree = service.getTree();

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).id()).isEqualTo(orphan);
    }

    @Test
    void emptyRepositoryYieldsEmptyTree() {
        when(repository.findAll()).thenReturn(List.of());

        assertThat(service.getTree()).isEmpty();
    }

    @Test
    void breaksParentIdCycleInsteadOfRecursingForever() {
        // A -> B -> A cycle (neither is a root). The cycle guard must not loop or
        // overflow; the cyclic back-edge is simply not followed.
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(repository.findAll()).thenReturn(List.of(
            node(a, RcdoNodeType.OUTCOME, "A", b),
            node(b, RcdoNodeType.OUTCOME, "B", a)));

        // Both have an in-set parent, so neither is a root: getTree returns empty
        // rather than hanging. The point is that it terminates.
        assertThat(service.getTree()).isEmpty();
    }

    @Test
    void getNodeReturnsFullNestedSubtreeForExistingId() {
        UUID rally = UUID.randomUUID();
        UUID dobj = UUID.randomUUID();
        UUID outcome = UUID.randomUUID();
        when(repository.findAll()).thenReturn(List.of(
            node(rally, RcdoNodeType.RALLY_CRY, "RC", null),
            node(dobj, RcdoNodeType.DEFINING_OBJECTIVE, "DO", rally),
            node(outcome, RcdoNodeType.OUTCOME, "O", dobj)));

        RcdoNodeDto dto = service.getNode(rally).orElseThrow();

        assertThat(dto.id()).isEqualTo(rally);
        // children carries the full subtree, not just direct children.
        assertThat(dto.children()).extracting(RcdoNodeDto::id).containsExactly(dobj);
        assertThat(dto.children().get(0).children()).extracting(RcdoNodeDto::id)
            .containsExactly(outcome);
    }

    @Test
    void getNodeReturnsEmptyForUnknownId() {
        UUID unknown = UUID.randomUUID();
        when(repository.findAll()).thenReturn(List.of());

        assertThat(service.getNode(unknown)).isEmpty();
    }
}
