import type { PlanStatus, ReconciliationStatus } from '../store/api';

/**
 * KTD 6 — a small shared token map for the 9 lifecycle status values (four
 * {@link PlanStatus} + five {@link ReconciliationStatus}). Maps each value to a
 * human label and Flowbite/Tailwind badge classes so plan state reads
 * consistently across the shell, commitment rows, and reconcile/summary views
 * (UX-R3). No design system; just enough visual vocabulary to keep state
 * legible and prevent the styling from drifting per view.
 */
export interface StatusStyle {
  /** Human-readable label shown to the IC. */
  label: string;
  /** Tailwind classes for a small pill/badge (background + text). */
  badgeClass: string;
}

const PLAN_STATUS_STYLES: Record<PlanStatus, StatusStyle> = {
  DRAFT: { label: 'Draft', badgeClass: 'bg-gray-100 text-gray-700' },
  LOCKED: { label: 'Locked', badgeClass: 'bg-blue-100 text-blue-700' },
  RECONCILING: { label: 'Reconciling', badgeClass: 'bg-amber-100 text-amber-700' },
  RECONCILED: { label: 'Reconciled', badgeClass: 'bg-green-100 text-green-700' },
};

const RECONCILIATION_STATUS_STYLES: Record<ReconciliationStatus, StatusStyle> = {
  DONE: { label: 'Done', badgeClass: 'bg-green-100 text-green-700' },
  PARTIAL: { label: 'Partial', badgeClass: 'bg-amber-100 text-amber-700' },
  NOT_DONE: { label: 'Not done', badgeClass: 'bg-red-100 text-red-700' },
  DROPPED: { label: 'Dropped', badgeClass: 'bg-gray-100 text-gray-500' },
  // System-set only (UX-R9): styled distinctly so it never reads as "done".
  UNRECONCILED: { label: 'Unreconciled', badgeClass: 'bg-orange-100 text-orange-700' },
};

/** All 9 status values keyed together for a single lookup by either union. */
export const STATUS_STYLES: Record<PlanStatus | ReconciliationStatus, StatusStyle> = {
  ...PLAN_STATUS_STYLES,
  ...RECONCILIATION_STATUS_STYLES,
};

/** Resolves the {@link StatusStyle} for any plan or reconciliation status. */
export function statusStyle(status: PlanStatus | ReconciliationStatus): StatusStyle {
  return STATUS_STYLES[status];
}
