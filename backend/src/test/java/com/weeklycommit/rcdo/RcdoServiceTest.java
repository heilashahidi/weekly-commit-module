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
 * nested shape, and the edge cases (orphan, empty).
 */
@ExtendWith(MockitoExtension.class)
class RcdoServiceTest {

    @Mock
    RcdoNodeRepository repository;

    @InjectMocks
    RcdoService service;

    private RcdoNode node(UUID id, RcdoNodeType type, String title, UUID parentId) {
        RcdoNode n = new RcdoNode();
        n.setId(id);
        n.setNodeType(type);
        n.setTitle(title);
        n.setParentId(parentId);
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
    void getNodeReturnsDtoWithDirectChildrenForExistingId() {
        UUID rally = UUID.randomUUID();
        UUID dobj = UUID.randomUUID();
        when(repository.findById(rally)).thenReturn(java.util.Optional.of(
            node(rally, RcdoNodeType.RALLY_CRY, "RC", null)));
        when(repository.findAll()).thenReturn(List.of(
            node(rally, RcdoNodeType.RALLY_CRY, "RC", null),
            node(dobj, RcdoNodeType.DEFINING_OBJECTIVE, "DO", rally)));

        RcdoNodeDto dto = service.getNode(rally).orElseThrow();

        assertThat(dto.id()).isEqualTo(rally);
        assertThat(dto.children()).extracting(RcdoNodeDto::id).containsExactly(dobj);
    }

    @Test
    void getNodeReturnsEmptyForUnknownId() {
        UUID unknown = UUID.randomUUID();
        when(repository.findById(unknown)).thenReturn(java.util.Optional.empty());

        assertThat(service.getNode(unknown)).isEmpty();
    }
}
