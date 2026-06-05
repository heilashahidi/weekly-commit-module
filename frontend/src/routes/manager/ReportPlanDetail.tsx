import { useEffect, useRef } from 'react';
import {
  useGetPlanCommitmentsQuery,
  useGetPlanMetricsQuery,
  useGetPlanQuery,
  type PlanMetricsDto,
} from '../../store/api';
import { problemDetailMessage } from '../../lib/problemDetail';
import { formatAccuracy } from '../../lib/metricsFormat';
import CommitmentRow from '../../components/CommitmentRow';
import StatusBadge from '../../components/StatusBadge';
import ManagerReviewNote from '../../components/ManagerReviewNote';
import ManagerReviewForm from '../../components/ManagerReviewForm';

interface ReportPlanDetailProps {
  planId: string;
  displayName: string;
  onBack: () => void;
}

/** Planned-vs-actual block, shown only once the plan is RECONCILED. */
function PlannedVsActual({ metrics }: { metrics: PlanMetricsDto }) {
  return (
    <dl
      aria-label="Planned vs actual"
      className="grid grid-cols-2 gap-4 rounded border border-gray-200 p-4"
    >
      <div>
        <dt className="text-xs uppercase tracking-wide text-gray-400">Reconciliation accuracy</dt>
        <dd className="text-lg font-medium text-gray-800">
          {formatAccuracy(metrics.reconciliationAccuracy)}
        </dd>
      </div>
      <div>
        <dt className="text-xs uppercase tracking-wide text-gray-400">Planned vs unplanned</dt>
        <dd className="text-lg font-medium text-gray-800">
          {metrics.plannedVsUnplannedRatio.toFixed(2)}
        </dd>
      </div>
    </dl>
  );
}

/**
 * Read-only view of a report's plan with review writing (F-U6, F2). Renders the plan
 * status, its commitments (immutable, via {@link CommitmentRow} in `frozen` mode —
 * no edit/delete/transition controls), the planned-vs-actual metrics once RECONCILED,
 * the existing review (read-only {@link ManagerReviewNote}), and the
 * {@link ManagerReviewForm} to write/update it. All reads go through the
 * manager-authorized endpoints. Focus moves to the heading on mount (no-router focus
 * management); a Back control returns to the board.
 */
export default function ReportPlanDetail({ planId, displayName, onBack }: ReportPlanDetailProps) {
  const headingRef = useRef<HTMLHeadingElement>(null);
  useEffect(() => {
    headingRef.current?.focus();
  }, []);

  const { data: plan, isLoading, isError, error } = useGetPlanQuery(planId);
  const { data: commitments } = useGetPlanCommitmentsQuery(planId);
  const reconciled = plan?.status === 'RECONCILED';
  const { data: metrics } = useGetPlanMetricsQuery(planId, { skip: !reconciled });

  return (
    <section className="space-y-4 text-left" aria-label="Report plan detail">
      <button
        type="button"
        onClick={onBack}
        className="text-sm text-indigo-700 hover:underline"
      >
        ← Back to team
      </button>

      <header className="flex items-center gap-2">
        <h1 ref={headingRef} tabIndex={-1} className="text-xl font-semibold text-gray-800">
          {displayName}’s week
        </h1>
        {plan && <StatusBadge status={plan.status} />}
      </header>

      {isLoading && <p className="text-gray-500">Loading plan…</p>}
      {isError && (
        <p role="alert" className="text-red-600">
          {problemDetailMessage(error)}
        </p>
      )}

      {plan && !isError && (
        <>
          {reconciled && metrics && <PlannedVsActual metrics={metrics} />}

          {!commitments || commitments.length === 0 ? (
            <p className="text-gray-500">No commitments this week.</p>
          ) : (
            <ul className="space-y-2">
              {commitments.map((c) => (
                <CommitmentRow key={c.id} commitment={c} mode="frozen" />
              ))}
            </ul>
          )}

          <section className="space-y-3 border-t border-gray-200 pt-4">
            <ManagerReviewNote planId={planId} />
            <ManagerReviewForm planId={planId} />
          </section>
        </>
      )}
    </section>
  );
}
