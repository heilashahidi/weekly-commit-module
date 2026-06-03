import { useState } from 'react';
import { Button } from 'flowbite-react';
import {
  useGetPlanCommitmentsQuery,
  useSetCommitmentStatusMutation,
  useSubmitReconciledMutation,
  type CommitmentDto,
  type ReconciliationStatus,
  type WeeklyPlanDto,
} from '../../store/api';
import { problemDetailMessage } from '../../lib/problemDetail';
import { statusStyle } from '../../lib/statusStyles';
import CommitmentRow from '../../components/CommitmentRow';

interface ModeViewProps {
  plan: WeeklyPlanDto;
}

/**
 * The IC-settable reconciliation statuses (UX-R9). `UNRECONCILED` is deliberately
 * excluded — it's system-set only (the auto-backstop marks unreconciled work) and
 * must never be offered as a choice or it would read as a legitimate "I handled
 * this" outcome. The status selector is built from exactly this list.
 */
const SETTABLE_STATUSES: ReconciliationStatus[] = [
  'DONE',
  'PARTIAL',
  'NOT_DONE',
  'DROPPED',
];

/**
 * Per-commitment status control (the `renderStatusControl` slot CommitmentRow
 * exposes for `reconciling` mode — U5's reuse seam). A native `<select>` offering
 * only the IC-settable values plus an optional note input. Setting a status (or
 * editing the note then re-selecting) issues `setCommitmentStatus`; the note is
 * optional and sent as `null` when blank.
 *
 * The select's current value is derived from the server's `reconciliationStatus`
 * (so it stays correct after the invalidation refetch), with an empty
 * "not yet statused" placeholder option when null. The note field keeps local
 * input state (the only thing not yet persisted), seeded from the server value.
 */
function StatusControl({ commitment }: { commitment: CommitmentDto }) {
  const [note, setNote] = useState(commitment.reconciliationNote ?? '');
  const [setStatus, setState] = useSetCommitmentStatusMutation();

  async function applyStatus(status: ReconciliationStatus) {
    const trimmed = note.trim();
    try {
      await setStatus({
        id: commitment.id,
        body: { status, note: trimmed.length > 0 ? trimmed : null },
      }).unwrap();
    } catch {
      // Surfaced via setState.error below.
    }
  }

  return (
    <div className="flex flex-col items-end gap-1" data-testid="status-control">
      <select
        aria-label={`Status for ${commitment.title}`}
        value={commitment.reconciliationStatus ?? ''}
        onChange={(e) => {
          const value = e.target.value;
          if (value) {
            void applyStatus(value as ReconciliationStatus);
          }
        }}
        disabled={setState.isLoading}
        className="rounded border border-gray-300 px-2 py-1 text-sm"
      >
        <option value="" disabled>
          Set status…
        </option>
        {SETTABLE_STATUSES.map((status) => (
          <option key={status} value={status}>
            {statusStyle(status).label}
          </option>
        ))}
      </select>

      <input
        type="text"
        aria-label={`Note for ${commitment.title}`}
        value={note}
        onChange={(e) => setNote(e.target.value)}
        placeholder="Optional note"
        className="w-44 rounded border border-gray-300 px-2 py-1 text-sm"
      />

      {setState.error && (
        <p role="alert" className="text-xs text-red-600">
          {problemDetailMessage(setState.error)}
        </p>
      )}
    </div>
  );
}

/**
 * RECONCILING mode sub-view (U7). The IC sets each commitment's outcome via a
 * per-row status selector (DONE/PARTIAL/NOT_DONE/DROPPED — never `UNRECONCILED`,
 * UX-R9) with an optional note, issuing `setCommitmentStatus`. The **Submit**
 * action is gated: it stays disabled until every commitment (planned + unplanned)
 * has a non-null `reconciliationStatus`, showing how many remain (UX-R10/AE-D6).
 *
 * The gate is derived from the fetched commitments (count where
 * `reconciliationStatus === null`), not local state — `setCommitmentStatus`
 * invalidates the `Commitment` tag, the list refetches with updated statuses, and
 * the gate re-derives so it stays correct (AE-D7). Submit issues
 * `submitReconciled`; on success the plan leaves RECONCILING (tag invalidation
 * refetch). A defensive 422 (not-all-statused) surfaces its ProblemDetail message.
 */
export default function ReconcileView({ plan }: ModeViewProps) {
  const { data: commitments, isLoading, isError, error } = useGetPlanCommitmentsQuery(plan.id);
  const [submitReconciled, submitState] = useSubmitReconciledMutation();

  const list = commitments ?? [];
  const remaining = list.filter((c) => c.reconciliationStatus === null).length;
  const isEmpty = list.length === 0;
  const canSubmit = !isEmpty && remaining === 0;

  async function handleSubmit() {
    try {
      await submitReconciled(plan.id).unwrap();
    } catch {
      // Surfaced via submitState.error below.
    }
  }

  return (
    <section aria-label="Reconcile view" className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-medium text-gray-700">Reconciling — how did the week go?</h2>
        <p className="text-sm text-gray-500">Week {plan.weekKey}</p>
      </div>

      {isLoading && <p className="text-gray-500">Loading commitments…</p>}

      {isError && (
        <p role="alert" className="text-red-600">
          {problemDetailMessage(error)}
        </p>
      )}

      {!isLoading && !isError && (
        <>
          {isEmpty ? (
            <p className="text-gray-500">No commitments to reconcile this week.</p>
          ) : (
            <ul className="space-y-2">
              {list.map((c) => (
                <CommitmentRow
                  key={c.id}
                  commitment={c}
                  mode="reconciling"
                  renderStatusControl={(commitment) => <StatusControl commitment={commitment} />}
                />
              ))}
            </ul>
          )}

          <div className="border-t border-gray-200 pt-4">
            {submitState.error && (
              <p role="alert" className="mb-2 text-sm text-red-600">
                {problemDetailMessage(submitState.error)}
              </p>
            )}
            <p className="mb-2 text-sm text-gray-600" data-testid="remaining-count">
              {remaining === 0
                ? 'All commitments statused.'
                : `${remaining} remaining to status before you can submit.`}
            </p>
            <Button
              type="button"
              onClick={() => void handleSubmit()}
              disabled={!canSubmit || submitState.isLoading}
            >
              Submit reconciliation
            </Button>
          </div>
        </>
      )}
    </section>
  );
}
