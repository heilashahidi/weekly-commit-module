import { useState } from 'react';
import { Button } from 'flowbite-react';
import {
  useGetPlanCommitmentsQuery,
  useStartReconcilingMutation,
  type WeeklyPlanDto,
} from '../../store/api';
import { problemDetailMessage } from '../../lib/problemDetail';
import { formatTimestamp } from '../../lib/formatTimestamp';
import CommitmentForm from '../../components/CommitmentForm';
import CommitmentRow from '../../components/CommitmentRow';
import ManagerReviewNote from '../../components/ManagerReviewNote';

interface ModeViewProps {
  plan: WeeklyPlanDto;
}

/**
 * Absolute deadline display (UX-R2). Mirrors `StatusDeadline` in MyWeek.tsx — a
 * fixed, readable timestamp (no ticking countdown, deferred). Renders nothing
 * when `statusDeadline` is null.
 */
function AutoAdvanceDeadline({ deadline }: { deadline: string | null }) {
  if (!deadline) {
    return null;
  }
  return (
    <p className="text-sm text-gray-500">
      Auto-advance deadline: <time dateTime={deadline}>{formatTimestamp(deadline)}</time>
    </p>
  );
}

/**
 * LOCKED mode sub-view (U6). Planned commitments are visibly immutable — rendered
 * via {@link CommitmentRow} in `frozen` mode, so they show no edit/delete controls
 * (legible immutability, not error-on-edit — UX-R8/AE-D4). The IC can still append
 * unplanned commitments (link-required) which the server flags `planned=false`,
 * surfacing with an `unplanned` badge (AE-D5). The auto-advance deadline is shown
 * (UX-R2), a **Start reconciling** action transitions the plan, and a manager
 * review (if any) is displayed read-only (UX-R13/AE-D9). Mutations invalidate tags
 * → the shell refetches → the view updates.
 */
export default function LockedView({ plan }: ModeViewProps) {
  const { data: commitments, isLoading, isError, error } = useGetPlanCommitmentsQuery(plan.id);

  const [startReconciling, startState] = useStartReconcilingMutation();
  const [adding, setAdding] = useState(false);

  const isEmpty = !commitments || commitments.length === 0;

  async function handleStartReconciling() {
    try {
      await startReconciling(plan.id).unwrap();
    } catch {
      // Surfaced via startState.error below.
    }
  }

  return (
    <section aria-label="Locked view" className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-medium text-gray-700">Locked — week in progress</h2>
        <p className="text-sm text-gray-500">Week {plan.weekKey}</p>
      </div>

      <AutoAdvanceDeadline deadline={plan.statusDeadline} />

      <ManagerReviewNote planId={plan.id} />

      {isLoading && <p className="text-gray-500">Loading commitments…</p>}

      {isError && (
        <p role="alert" className="text-red-600">
          {problemDetailMessage(error)}
        </p>
      )}

      {!isLoading && !isError && (
        <>
          {isEmpty ? (
            <p className="text-gray-500">No commitments this week.</p>
          ) : (
            <ul className="space-y-2">
              {commitments!.map((c) => (
                <CommitmentRow key={c.id} commitment={c} mode="frozen" />
              ))}
            </ul>
          )}

          {adding ? (
            <CommitmentForm
              planId={plan.id}
              onDone={() => setAdding(false)}
              titleLabel="Unplanned commitment title"
              inputId="unplanned-commitment-title"
              placeholder="What came up this week?"
              submitLabel="Add unplanned commitment"
            />
          ) : (
            <Button type="button" onClick={() => setAdding(true)}>
              Add unplanned commitment
            </Button>
          )}

          <div className="border-t border-gray-200 pt-4">
            {startState.error && (
              <p role="alert" className="mb-2 text-sm text-red-600">
                {problemDetailMessage(startState.error)}
              </p>
            )}
            <Button
              type="button"
              color="warning"
              onClick={() => void handleStartReconciling()}
              disabled={startState.isLoading}
            >
              Start reconciling
            </Button>
          </div>
        </>
      )}
    </section>
  );
}
