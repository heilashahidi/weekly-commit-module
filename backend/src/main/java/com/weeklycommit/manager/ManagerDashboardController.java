package com.weeklycommit.manager;

import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for the manager dashboard (F-U3). JWT-secured by default (only
 * {@code /health} is public per SecurityConfig). Thin — the team roll-up, the
 * manager authorization (403 for non-managers), and the current-week resolution live
 * in {@link ManagerDashboardService}.
 *
 * <p>{@code GET /api/manager/team} returns the calling manager's current-week board,
 * paginated over their direct reports ({@code ?page=&size=&sort=} bound automatically
 * by Spring Data web).
 */
@RestController
@RequestMapping("/api/manager")
public class ManagerDashboardController {

    private final ManagerDashboardService service;

    public ManagerDashboardController(ManagerDashboardService service) {
        this.service = service;
    }

    @GetMapping("/team")
    public PageDto<TeamRowDto> teamWeek(Pageable pageable) {
        return service.getTeamWeek(pageable);
    }
}
