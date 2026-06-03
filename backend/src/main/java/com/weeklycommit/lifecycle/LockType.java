package com.weeklycommit.lifecycle;

/**
 * Provenance of a plan's lock (origin R4): {@link #USER_LOCKED} when the IC
 * locked manually, {@link #AUTO_LOCKED} when the deadline backstop locked it.
 * Null until the plan leaves {@code DRAFT}. Kept as its own column rather than
 * folded into {@link PlanStatus} so managers and metrics can query "what state"
 * and "how it got there" independently (KTD 2).
 */
public enum LockType {
    USER_LOCKED,
    AUTO_LOCKED
}
