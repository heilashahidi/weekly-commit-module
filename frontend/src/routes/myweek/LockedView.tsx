import { useState } from 'react';
import { Button } from 'flowbite-react';
import {
  useCreateCommitmentMutation,
  useGetPlanCommitmentsQuery,
  useStartReconcilingMutation,
  type RcdoNode,
  type WeeklyPlanDto,
} from '../../store/api';
import { problemDetailMessage } from '../../lib/problemDetail';
import CommitmentRow from '../../components/CommitmentRow';
import ManagerReviewNote from '../../components/ManagerReviewNote';
import RcdoPicker from '../../components/RcdoPicker';

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
  const parsed = new Date(deadline);
  const text = Number.isNaN(parsed.getTime()) ? deadline : parsed.toLocaleString();
  return (
    <p className="text-sm text-gray-500">
      Auto-advance deadline: <time dateTime={deadline}>{text}</time>
    </p>
  );
}

/**
 * Add-unplanned form. A small local form mirroring DraftView's CommitmentForm
 * gate behavior (title + Supporting Outcome both required, UX-R4/AE-D1) — the
 * link stays unskippable in LOCKED too. The server flags the created commitment
 * `planned=false` (AE-D5); after tag invalidation the refetched list shows it
 * with the `unplanned` badge while planned rows stay frozen. Kept local rather
 * than extracting CommitmentForm (no DraftView refactor in this unit); the
 * LOCKED form never edits, so it's deliberately the create-only subset.
 */
function AddUnplannedForm({ planId, onDone }: { planId: string; onDone: () => void }) {
  const [title, setTitle] = useState('');
  const [selected, setSelected] = useState<RcdoNode | null>(null);
  const [pickerOpen, setPickerOpen] = useState(false);

  const [createCommitment, createState] = useCreateCommitmentMutation();

  const canSave = title.trim().length > 0 && selected !== null;

  async function handleSave() {
    if (!canSave || selected === null) {
      return;
    }
    try {
      await createCommitment({
        planId,
        body: { rcdoNodeId: selected.id, title: title.trim() },
      }).unwrap();
      onDone();
    } catch {
      // Error surfaced below via the mutation state; keep the form open.
    }
  }

  return (
    <div className="space-y-2 rounded border border-gray-200 p-3">
      <label
        className="block text-sm font-medium text-gray-700"
        htmlFor="unplanned-commitment-title"
      >
        Unplanned commitment title
      </label>
      <input
        id="unplanned-commitment-title"
        type="text"
        value={title}
        onChange={(e) => setTitle(e.target.value)}
        placeholder="What came up this week?"
        className="w-full rounded border border-gray-300 px-2 py-1"
      />

      <div className="flex items-center gap-2">
        <Button type="button" color="light" onClick={() => setPickerOpen(true)}>
          Pick Supporting Outcome
        </Button>
        {selected ? (
          <span className="text-sm text-blue-700" data-testid="selected-outcome">
            {selected.title}
          </span>
        ) : (
          <span className="text-sm text-gray-400">No Supporting Outcome selected</span>
        )}
      </div>

      {createState.error && (
        <p role="alert" className="text-sm text-red-600">
          {problemDetailMessage(createState.error)}
        </p>
      )}

      <div className="flex gap-2">
        <Button type="button" onClick={() => void handleSave()} disabled={!canSave}>
          Add unplanned commitment
        </Button>
        <Button type="button" color="light" onClick={onDone}>
          Cancel
        </Button>
      </div>

      <RcdoPicker
        open={pickerOpen}
        onClose={() => setPickerOpen(false)}
        onSelect={(node) => setSelected(node)}
      />
    </div>
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
            <AddUnplannedForm planId={plan.id} onDone={() => setAdding(false)} />
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
