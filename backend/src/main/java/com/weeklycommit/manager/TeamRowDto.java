package com.weeklycommit.manager;

import com.weeklycommit.lifecycle.PlanStatus;
import java.util.List;

/**
 * One row of the manager's current-week team board (F-U3): a direct report, the
 * status of their current-week plan ({@code null} when they have no plan yet — a
 * first-class "no plan" signal, NOT a {@code PlanStatus} value), the spread of RCDO
 * Outcomes their week points at, and whether a manager review exists.
 */
public record TeamRowDto(
    String reportSub,
    String displayName,
    PlanStatus status,
    List<OutcomeCountDto> outcomeSpread,
    boolean reviewExists) {}
