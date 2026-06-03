package com.weeklycommit.lifecycle;

/**
 * Per-commitment reconciliation outcome (KTD 5). The first four values are
 * IC-settable during the {@code RECONCILING} phase; {@link #UNRECONCILED} is
 * system-only, applied by the deadline backstop (U7) to any commitment the IC
 * left unstatused at auto-close so an auto-closed week never silently reads as
 * {@code DONE} (R19). The column is nullable until reconciliation begins.
 */
public enum ReconciliationStatus {
    DONE,
    PARTIAL,
    NOT_DONE,
    DROPPED,
    UNRECONCILED
}
