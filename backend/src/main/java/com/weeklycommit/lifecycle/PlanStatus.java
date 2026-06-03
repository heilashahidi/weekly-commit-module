package com.weeklycommit.lifecycle;

/**
 * The four resting states of a {@code WeeklyPlan}, in forward order:
 * {@code DRAFT -> LOCKED -> RECONCILING -> RECONCILED}. "Carry Forward" (origin
 * R1) is deliberately not a status — it is a seeding action performed at/after
 * {@code RECONCILED} that populates the next week's {@code DRAFT} (KTD 1), so
 * every value here is a real state a plan can rest in.
 */
public enum PlanStatus {
    DRAFT,
    LOCKED,
    RECONCILING,
    RECONCILED
}
