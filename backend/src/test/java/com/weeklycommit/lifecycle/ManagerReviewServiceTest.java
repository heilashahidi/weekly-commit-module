package com.weeklycommit.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weeklycommit.config.PrincipalResolver;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Pure unit coverage of the manager-review overlay (U9, R16/R17) — no Spring context,
 * repositories and principal resolver mocked. Proves the one-review-per-plan upsert
 * (create when absent, update when present), the reviewer being stamped with the
 * current principal, and the plan-existence 404. Any authenticated principal may
 * review (no ownership check) — verified implicitly by reviewing as a non-owner here.
 */
@ExtendWith(MockitoExtension.class)
class ManagerReviewServiceTest {

    private static final String REVIEWER = "auth0|manager";

    @Mock
    ManagerReviewRepository reviewRepository;

    @Mock
    WeeklyPlanRepository planRepository;

    @Mock
    PrincipalResolver principalResolver;

    @InjectMocks
    ManagerReviewService service;

    private ManagerReview existingReview(UUID planId, String comment) {
        ManagerReview r = new ManagerReview();
        r.setId(UUID.randomUUID());
        r.setWeeklyPlanId(planId);
        r.setReviewer("auth0|earlier-reviewer");
        r.setComment(comment);
        return r;
    }

    @Test
    void upsertCreatesReviewWhenNoneExists() {
        UUID planId = UUID.randomUUID();
        when(planRepository.existsById(planId)).thenReturn(true);
        when(principalResolver.currentPrincipal()).thenReturn(REVIEWER);
        when(reviewRepository.findByWeeklyPlanId(planId)).thenReturn(Optional.empty());
        when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ManagerReviewDto dto = service.upsertReview(planId, "Looks good");

        assertThat(dto.weeklyPlanId()).isEqualTo(planId);
        assertThat(dto.reviewer()).isEqualTo(REVIEWER);
        assertThat(dto.comment()).isEqualTo("Looks good");

        ArgumentCaptor<ManagerReview> saved = ArgumentCaptor.forClass(ManagerReview.class);
        verify(reviewRepository).save(saved.capture());
        assertThat(saved.getValue().getWeeklyPlanId()).isEqualTo(planId);
        assertThat(saved.getValue().getReviewer()).isEqualTo(REVIEWER);
    }

    @Test
    void upsertUpdatesExistingReviewCommentOnePerPlan() {
        UUID planId = UUID.randomUUID();
        ManagerReview existing = existingReview(planId, "old comment");
        when(planRepository.existsById(planId)).thenReturn(true);
        when(principalResolver.currentPrincipal()).thenReturn(REVIEWER);
        when(reviewRepository.findByWeeklyPlanId(planId)).thenReturn(Optional.of(existing));
        when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ManagerReviewDto dto = service.upsertReview(planId, "updated comment");

        // Same row updated (not a new one): id is preserved, comment + reviewer refreshed.
        assertThat(dto.id()).isEqualTo(existing.getId());
        assertThat(dto.comment()).isEqualTo("updated comment");
        assertThat(dto.reviewer()).isEqualTo(REVIEWER);

        ArgumentCaptor<ManagerReview> saved = ArgumentCaptor.forClass(ManagerReview.class);
        verify(reviewRepository).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(existing.getId());
    }

    @Test
    void upsertOnMissingPlanRejectedWithNotFound() {
        UUID planId = UUID.randomUUID();
        when(planRepository.existsById(planId)).thenReturn(false);

        assertThatThrownBy(() -> service.upsertReview(planId, "comment"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));

        verify(reviewRepository, never()).save(any());
    }

    @Test
    void getReviewOnMissingPlanRejectedWithNotFound() {
        UUID planId = UUID.randomUUID();
        when(planRepository.existsById(planId)).thenReturn(false);

        assertThatThrownBy(() -> service.getReview(planId))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getReviewReturnsEmptyWhenPlanHasNoReview() {
        UUID planId = UUID.randomUUID();
        when(planRepository.existsById(planId)).thenReturn(true);
        when(reviewRepository.findByWeeklyPlanId(planId)).thenReturn(Optional.empty());

        assertThat(service.getReview(planId)).isEmpty();
    }

    @Test
    void getReviewReturnsExistingReview() {
        UUID planId = UUID.randomUUID();
        when(planRepository.existsById(planId)).thenReturn(true);
        when(reviewRepository.findByWeeklyPlanId(planId))
            .thenReturn(Optional.of(existingReview(planId, "a note")));

        Optional<ManagerReviewDto> dto = service.getReview(planId);

        assertThat(dto).isPresent();
        assertThat(dto.get().comment()).isEqualTo("a note");
    }
}
