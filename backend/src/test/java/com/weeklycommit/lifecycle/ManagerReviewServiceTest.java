package com.weeklycommit.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weeklycommit.config.PrincipalResolver;
import com.weeklycommit.manager.ReportingRepository;
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
 * Pure unit coverage of the manager-review overlay with workstream-F authorization —
 * no Spring context, repositories and principal resolver mocked. Proves the
 * one-review-per-plan upsert, the reviewer being stamped with the acting manager, the
 * plan-existence 404, the comment-length 400, and the manager-scope rules: write is
 * restricted to the owner's manager (owner self-review and outsiders 403); read is
 * allowed for the owner or the owner's manager (outsiders 403).
 */
@ExtendWith(MockitoExtension.class)
class ManagerReviewServiceTest {

    private static final String OWNER = "auth0|owner";
    private static final String MANAGER = "auth0|manager";
    private static final String OUTSIDER = "auth0|outsider";

    @Mock
    ManagerReviewRepository reviewRepository;

    @Mock
    WeeklyPlanRepository planRepository;

    @Mock
    ReportingRepository reportingRepository;

    @Mock
    PrincipalResolver principalResolver;

    @InjectMocks
    ManagerReviewService service;

    private WeeklyPlan planOwnedBy(UUID planId, String owner) {
        WeeklyPlan p = new WeeklyPlan();
        p.setId(planId);
        p.setOwner(owner);
        p.setStatus(PlanStatus.LOCKED);
        return p;
    }

    private ManagerReview existingReview(UUID planId, String comment) {
        ManagerReview r = new ManagerReview();
        r.setId(UUID.randomUUID());
        r.setWeeklyPlanId(planId);
        r.setReviewer("auth0|earlier-reviewer");
        r.setComment(comment);
        return r;
    }

    // --- write: manager of owner ---

    @Test
    void upsertCreatesReviewWhenNoneExists() {
        UUID planId = UUID.randomUUID();
        when(planRepository.findById(planId)).thenReturn(Optional.of(planOwnedBy(planId, OWNER)));
        when(principalResolver.currentPrincipal()).thenReturn(MANAGER);
        when(reportingRepository.existsByManagerSubAndReportSub(MANAGER, OWNER)).thenReturn(true);
        when(reviewRepository.findByWeeklyPlanId(planId)).thenReturn(Optional.empty());
        when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ManagerReviewDto dto = service.upsertReview(planId, "Looks good");

        assertThat(dto.weeklyPlanId()).isEqualTo(planId);
        assertThat(dto.reviewer()).isEqualTo(MANAGER);
        assertThat(dto.comment()).isEqualTo("Looks good");

        ArgumentCaptor<ManagerReview> saved = ArgumentCaptor.forClass(ManagerReview.class);
        verify(reviewRepository).save(saved.capture());
        assertThat(saved.getValue().getWeeklyPlanId()).isEqualTo(planId);
        assertThat(saved.getValue().getReviewer()).isEqualTo(MANAGER);
    }

    @Test
    void upsertUpdatesExistingReviewCommentOnePerPlan() {
        UUID planId = UUID.randomUUID();
        ManagerReview existing = existingReview(planId, "old comment");
        when(planRepository.findById(planId)).thenReturn(Optional.of(planOwnedBy(planId, OWNER)));
        when(principalResolver.currentPrincipal()).thenReturn(MANAGER);
        when(reportingRepository.existsByManagerSubAndReportSub(MANAGER, OWNER)).thenReturn(true);
        when(reviewRepository.findByWeeklyPlanId(planId)).thenReturn(Optional.of(existing));
        when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ManagerReviewDto dto = service.upsertReview(planId, "updated comment");

        assertThat(dto.id()).isEqualTo(existing.getId());
        assertThat(dto.comment()).isEqualTo("updated comment");
        assertThat(dto.reviewer()).isEqualTo(MANAGER);

        ArgumentCaptor<ManagerReview> saved = ArgumentCaptor.forClass(ManagerReview.class);
        verify(reviewRepository).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(existing.getId());
    }

    @Test
    void upsertOnMissingPlanRejectedWithNotFound() {
        UUID planId = UUID.randomUUID();
        when(planRepository.findById(planId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upsertReview(planId, "comment"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));

        verify(reviewRepository, never()).save(any());
    }

    @Test
    void ownerSelfReviewRejectedWithForbidden() {
        UUID planId = UUID.randomUUID();
        when(planRepository.findById(planId)).thenReturn(Optional.of(planOwnedBy(planId, OWNER)));
        when(principalResolver.currentPrincipal()).thenReturn(OWNER);
        when(reportingRepository.existsByManagerSubAndReportSub(OWNER, OWNER)).thenReturn(false);

        assertThatThrownBy(() -> service.upsertReview(planId, "self review"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN));

        verify(reviewRepository, never()).save(any());
    }

    @Test
    void nonManagerWriteRejectedWithForbidden() {
        UUID planId = UUID.randomUUID();
        when(planRepository.findById(planId)).thenReturn(Optional.of(planOwnedBy(planId, OWNER)));
        when(principalResolver.currentPrincipal()).thenReturn(OUTSIDER);
        when(reportingRepository.existsByManagerSubAndReportSub(OUTSIDER, OWNER)).thenReturn(false);

        assertThatThrownBy(() -> service.upsertReview(planId, "not mine"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN));

        verify(reviewRepository, never()).save(any());
    }

    @Test
    void tooLongCommentRejectedWithBadRequest() {
        UUID planId = UUID.randomUUID();
        when(planRepository.findById(planId)).thenReturn(Optional.of(planOwnedBy(planId, OWNER)));
        when(principalResolver.currentPrincipal()).thenReturn(MANAGER);
        when(reportingRepository.existsByManagerSubAndReportSub(MANAGER, OWNER)).thenReturn(true);

        String tooLong = "x".repeat(ManagerReviewService.MAX_COMMENT_LENGTH + 1);

        assertThatThrownBy(() -> service.upsertReview(planId, tooLong))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(reviewRepository, never()).save(any());
    }

    // --- read: owner or manager ---

    @Test
    void getReviewOnMissingPlanRejectedWithNotFound() {
        UUID planId = UUID.randomUUID();
        when(planRepository.findById(planId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getReview(planId))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getReviewReturnsEmptyWhenPlanHasNoReview() {
        UUID planId = UUID.randomUUID();
        when(planRepository.findById(planId)).thenReturn(Optional.of(planOwnedBy(planId, OWNER)));
        when(principalResolver.currentPrincipal()).thenReturn(MANAGER);
        when(reportingRepository.existsByManagerSubAndReportSub(MANAGER, OWNER)).thenReturn(true);
        when(reviewRepository.findByWeeklyPlanId(planId)).thenReturn(Optional.empty());

        assertThat(service.getReview(planId)).isEmpty();
    }

    @Test
    void ownerCanReadOwnReview() {
        UUID planId = UUID.randomUUID();
        when(planRepository.findById(planId)).thenReturn(Optional.of(planOwnedBy(planId, OWNER)));
        when(principalResolver.currentPrincipal()).thenReturn(OWNER);
        when(reviewRepository.findByWeeklyPlanId(planId))
            .thenReturn(Optional.of(existingReview(planId, "a note")));

        Optional<ManagerReviewDto> dto = service.getReview(planId);

        assertThat(dto).isPresent();
        assertThat(dto.get().comment()).isEqualTo("a note");
    }

    @Test
    void unrelatedPrincipalReadRejectedWithForbidden() {
        UUID planId = UUID.randomUUID();
        when(planRepository.findById(planId)).thenReturn(Optional.of(planOwnedBy(planId, OWNER)));
        when(principalResolver.currentPrincipal()).thenReturn(OUTSIDER);
        when(reportingRepository.existsByManagerSubAndReportSub(OUTSIDER, OWNER)).thenReturn(false);

        assertThatThrownBy(() -> service.getReview(planId))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN));
    }
}
