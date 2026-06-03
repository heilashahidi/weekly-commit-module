import {
  useGetPlanCommitmentsQuery,
  useGetPlanMetricsQuery,
  type CommitmentDto,
  type PlanMetricsDto,
  type WeeklyPlanDto,
} from '../../store/api';
import { problemDetailMessage } from '../../lib/problemDetail';
import CommitmentRow from '../../components/CommitmentRow';
import StatusBadge from '../../components/StatusBadge';
import CarryForwardPanel from './CarryForwardPanel';

interface ModeViewProps {
  plan: WeeklyPlanDto;
}

/**
 * Formats `reconciliationAccuracy` honoring the PlanMetricsDto null convention:
 * `null` (zero planned) is "not applicable" and must NOT read as `0%`
 * (UX-R11/AE-D10). A non-null value is a 0..1 fraction rendered as a percentage.
 */
function formatAccuracy(accuracy: number | null): string {
  if (accuracy === null) {
    return 'N/A (not applicable)';
  }
  return `${Math.round(accuracy * 100)}%`;
}

/**
 * The two-axis metrics surface (UX-R11). Reconciliation accuracy answers "did
 * planned work get done as planned" — with the null/zero-planned case shown as
 * "not applicable" rather than a misleading 0%. The planned-vs-unplanned ratio
 * (plus the raw counts as context) answers "how much of the week was planned".
 */
function MetricsSummary({ metrics }: { metrics: PlanMetricsDto }) {
  return (
    <dl
      aria-label="Week metrics"
      className="grid grid-cols-2 gap-4 rounded border border-gray-200 p-4"
    >
      <div>
        <dt className="text-xs uppercase tracking-wide text-gray-400">
          Reconciliation accuracy
        </dt>
        <dd className="text-lg font-medium text-gray-800" data-testid="reconciliation-accuracy">
          {formatAccuracy(metrics.reconciliationAccuracy)}
        </dd>
      </div>
      <div>
        <dt className="text-xs uppercase tracking-wide text-gray-400">
          Planned vs unplanned ratio
        </dt>
        <dd className="text-lg font-medium text-gray-800" data-testid="planned-vs-unplanned-ratio">
          {metrics.plannedVsUnplannedRatio.toFixed(2)}
        </dd>
      </div>
      <div className="col-span-2 text-sm text-gray-500">
        <span data-testid="planned-count">{metrics.plannedCount} planned</span>
        {' · '}
        <span data-testid="unplanned-count">{metrics.unplannedCount} unplanned</span>
        {' · '}
        <span data-testid="done-count">{metrics.doneCount} done</span>
      </div>
    </dl>
  );
}

/**
 * A single commitment in the summary. We reuse {@link CommitmentRow} in `frozen`
 * mode for the always-visible RCDO Supporting Outcome spine (UX-R5) + title +
 * unplanned badge, then render the final {@link StatusBadge} alongside so the
 * planned-vs-actual outcome reads honestly. Frozen mode shows no status badge of
 * its own (it has no controls), so the reconciliation StatusBadge is rendered in
 * a small adjacent summary line wrapping the row — keeping composition explicit
 * rather than threading a status slot through CommitmentRow.
 *
 * `UNRECONCILED` is styled distinctly by statusStyles (orange) so it never reads
 * as "done" (UX-R11/AE-D10). A null status (defensive — RECONCILED plans status
 * every commitment server-side) falls back to UNRECONCILED for the same honest
 * read rather than showing nothing.
 */
function SummaryCommitment({ commitment }: { commitment: CommitmentDto }) {
  const status = commitment.reconciliationStatus ?? 'UNRECONCILED';
  return (
    <li className="space-y-2" data-testid="summary-commitment">
      <div className="flex items-center justify-between gap-2">
        <span className="text-xs uppercase tracking-wide text-gray-400">Outcome</span>
        <StatusBadge status={status} />
      </div>
      {/* CommitmentRow renders its own <li>; nesting it under our summary <li>
          keeps the spine + unplanned badge identical to the other modes. */}
      <ul>
        <CommitmentRow commitment={commitment} mode="frozen" />
      </ul>
    </li>
  );
}

/**
 * RECONCILED mode sub-view (U8). After reconciliation it shows, per commitment,
 * the planned intent vs the actual outcome (final `reconciliationStatus` via
 * {@link StatusBadge}) with planned/unplanned distinguishable, plus the two-axis
 * metrics from `getPlanMetrics` — handling the null accuracy convention so a
 * zero-planned week reads "not applicable", not "0%" (UX-R11/AE-D10). Any
 * `UNRECONCILED` work is shown honestly (distinct orange badge, never "done").
 *
 * The carry-forward entry point (U9): the `CarryForwardPanel` below renders its
 * own "Carry forward" heading, so the wrapping <section> keeps only the divider
 * to set it apart from the summary above.
 */
export default function SummaryView({ plan }: ModeViewProps) {
  const {
    data: commitments,
    isLoading: commitmentsLoading,
    isError: commitmentsError,
    error: commitmentsErr,
  } = useGetPlanCommitmentsQuery(plan.id);

  const {
    data: metrics,
    isLoading: metricsLoading,
    isError: metricsError,
    error: metricsErr,
  } = useGetPlanMetricsQuery(plan.id);

  const isLoading = commitmentsLoading || metricsLoading;
  const isError = commitmentsError || metricsError;

  return (
    <section aria-label="Summary view" className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-medium text-gray-700">Reconciled — week summary</h2>
        <p className="text-sm text-gray-500">Week {plan.weekKey}</p>
      </div>

      {isLoading && <p className="text-gray-500">Loading summary…</p>}

      {isError && (
        <p role="alert" className="text-red-600">
          {problemDetailMessage(commitmentsError ? commitmentsErr : metricsErr)}
        </p>
      )}

      {!isLoading && !isError && (
        <>
          {metrics && <MetricsSummary metrics={metrics} />}

          {!commitments || commitments.length === 0 ? (
            <p className="text-gray-500">No commitments this week.</p>
          ) : (
            <ul className="space-y-4">
              {commitments.map((c) => (
                <SummaryCommitment key={c.id} commitment={c} />
              ))}
            </ul>
          )}

          {/* Carry-forward entry point (U9). The panel owns its own heading. */}
          <section aria-label="Carry forward" className="border-t border-gray-200 pt-4">
            <CarryForwardPanel planId={plan.id} />
          </section>
        </>
      )}
    </section>
  );
}
