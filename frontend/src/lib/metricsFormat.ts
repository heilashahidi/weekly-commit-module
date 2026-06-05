/**
 * Formats a {@link PlanMetricsDto} `reconciliationAccuracy` value, honoring the
 * null convention: `null` (zero planned commitments) is "not applicable" and must
 * NOT render as `0%` (UX-R11/AE-D10). A non-null value is a 0..1 fraction shown as a
 * percentage. Shared by the IC summary (SummaryView) and the manager plan detail
 * (ReportPlanDetail) so the convention can never drift between the two surfaces.
 */
export function formatAccuracy(accuracy: number | null): string {
  if (accuracy === null) {
    return 'N/A (not applicable)';
  }
  return `${Math.round(accuracy * 100)}%`;
}
