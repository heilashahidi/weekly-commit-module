package com.weeklycommit.manager;

import com.weeklycommit.lifecycle.PlanStatus;
import java.util.List;
import java.util.UUID;

/**
 * One row of the manager's current-week team board (F-U3): a direct report, the id and
 * status of their current-week plan (both {@code null} when they have no plan yet — a
 * first-class "no plan" signal; status is NOT a {@code PlanStatus} value in that case),
 * the spread of RCDO Outcomes their week points at, and whether a manager review
 * exists. {@code planId} is what the dashboard drills into (U6).
 */
public record TeamRowDto(
    String reportSub,
    String displayName,
    UUID planId,
    PlanStatus status,
    List<OutcomeCountDto> outcomeSpread,
    boolean reviewExists) {}
