import { useState } from 'react';
import { Button, Modal } from 'flowbite-react';
import {
  useCreateCommitmentMutation,
  useDeleteCommitmentMutation,
  useGetPlanCommitmentsQuery,
  useLockMutation,
  useUpdateCommitmentMutation,
  type CommitmentDto,
  type RcdoNode,
  type WeeklyPlanDto,
} from '../../store/api';
import { problemDetailMessage } from '../../lib/problemDetail';
import CommitmentRow from '../../components/CommitmentRow';
import RcdoPicker from '../../components/RcdoPicker';

interface ModeViewProps {
  plan: WeeklyPlanDto;
}

/**
 * The add/edit form. Reused for both creating a new commitment and editing an
 * existing one (`editing` carries the row under edit). A title field plus a
 * **Pick Supporting Outcome** button that opens the {@link RcdoPicker}. Save is
 * disabled until an Outcome is selected (UX-R4 / AE-D1) — and no create/update
 * request fires without one, because `handleSave` guards on `selected`.
 */
function CommitmentForm({
  planId,
  editing,
  onDone,
}: {
  planId: string;
  editing: CommitmentDto | null;
  onDone: () => void;
}) {
  const [title, setTitle] = useState(editing ? editing.title : '');
  const [selected, setSelected] = useState<RcdoNode | null>(null);
  const [pickerOpen, setPickerOpen] = useState(false);

  const [createCommitment, createState] = useCreateCommitmentMutation();
  const [updateCommitment, updateState] = useUpdateCommitmentMutation();

  // For an existing row we already have a linked Outcome (rcdoNodeId); the IC may
  // keep it or re-pick. The save gate requires a chosen node for a *new* row;
  // when editing, the existing link counts unless the IC re-picks.
  const effectiveNodeId = selected ? selected.id : editing ? editing.rcdoNodeId : null;
  const canSave = title.trim().length > 0 && effectiveNodeId !== null;

  const error = createState.error ?? updateState.error;

  async function handleSave() {
    if (!canSave || effectiveNodeId === null) {
      return;
    }
    const body = { rcdoNodeId: effectiveNodeId, title: title.trim() };
    try {
      if (editing) {
        await updateCommitment({ id: editing.id, body }).unwrap();
      } else {
        await createCommitment({ planId, body }).unwrap();
      }
      onDone();
    } catch {
      // Error surfaced below via the mutation state; keep the form open.
    }
  }

  const selectedLabel = selected
    ? selected.title
    : editing
      ? `Linked outcome ${editing.rcdoNodeId}`
      : null;

  return (
    <div className="space-y-2 rounded border border-gray-200 p-3">
      <label className="block text-sm font-medium text-gray-700" htmlFor="commitment-title">
        Commitment title
      </label>
      <input
        id="commitment-title"
        type="text"
        value={title}
        onChange={(e) => setTitle(e.target.value)}
        placeholder="What will you commit to?"
        className="w-full rounded border border-gray-300 px-2 py-1"
      />

      <div className="flex items-center gap-2">
        <Button type="button" color="light" onClick={() => setPickerOpen(true)}>
          Pick Supporting Outcome
        </Button>
        {selectedLabel ? (
          <span className="text-sm text-blue-700" data-testid="selected-outcome">
            {selectedLabel}
          </span>
        ) : (
          <span className="text-sm text-gray-400">No Supporting Outcome selected</span>
        )}
      </div>

      {error && (
        <p role="alert" className="text-sm text-red-600">
          {problemDetailMessage(error)}
        </p>
      )}

      <div className="flex gap-2">
        <Button type="button" onClick={() => void handleSave()} disabled={!canSave}>
          {editing ? 'Save changes' : 'Add commitment'}
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

/** Confirm modal for the consequential Lock action (UX-R6). Warns distinctly
 * when locking an empty plan (no commitments → `noPlan`, UX-R7 / AE-D3). */
function LockConfirm({
  open,
  empty,
  pending,
  onConfirm,
  onCancel,
}: {
  open: boolean;
  empty: boolean;
  pending: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  return (
    <Modal show={open} onClose={onCancel} dismissible>
      <Modal.Header>Lock this week?</Modal.Header>
      <Modal.Body>
        {empty ? (
          <p role="alert" className="text-amber-700">
            This plan has no commitments. Locking now records a “no plan” week — you
            won’t be able to add planned commitments afterward. Continue?
          </p>
        ) : (
          <p className="text-gray-700">
            Locking freezes your planned commitments for the week. You can still add
            unplanned commitments after locking, but planned ones become immutable.
          </p>
        )}
      </Modal.Body>
      <Modal.Footer>
        <Button onClick={onConfirm} disabled={pending}>
          {empty ? 'Lock empty week' : 'Lock week'}
        </Button>
        <Button color="light" onClick={onCancel} disabled={pending}>
          Cancel
        </Button>
      </Modal.Footer>
    </Modal>
  );
}

/**
 * DRAFT mode (U5). The IC builds the plan: lists the plan's commitments, adds
 * new ones (each requiring a Supporting Outcome via the picker — UX-R4/AE-D1),
 * edits/deletes them, and locks the week as a deliberate action (UX-R6). Locking
 * an empty plan warns and yields the `noPlan` state (UX-R7/AE-D3). Every mutation
 * invalidates tags → the shell refetches → the view flips to LOCKED.
 */
export default function DraftView({ plan }: ModeViewProps) {
  const { data: commitments, isLoading, isError, error } = useGetPlanCommitmentsQuery(plan.id);

  const [deleteCommitment, deleteState] = useDeleteCommitmentMutation();
  const [lock, lockState] = useLockMutation();

  const [adding, setAdding] = useState(false);
  const [editing, setEditing] = useState<CommitmentDto | null>(null);
  const [lockOpen, setLockOpen] = useState(false);

  const isEmpty = !commitments || commitments.length === 0;

  function handleDone() {
    setAdding(false);
    setEditing(null);
  }

  async function handleLock() {
    try {
      await lock(plan.id).unwrap();
      setLockOpen(false);
    } catch {
      // Surfaced via lockState.error below; keep the modal logic simple.
    }
  }

  return (
    <section aria-label="Draft view" className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-medium text-gray-700">Draft — plan your week</h2>
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
            <p className="text-gray-500">No commitments yet — add one to plan your week.</p>
          ) : (
            <ul className="space-y-2">
              {commitments!.map((c) => (
                <CommitmentRow
                  key={c.id}
                  commitment={c}
                  mode="draft"
                  onEdit={(row) => {
                    setAdding(false);
                    setEditing(row);
                  }}
                  onDelete={(row) => void deleteCommitment(row.id)}
                />
              ))}
            </ul>
          )}

          {deleteState.error && (
            <p role="alert" className="text-sm text-red-600">
              {problemDetailMessage(deleteState.error)}
            </p>
          )}

          {editing ? (
            <CommitmentForm planId={plan.id} editing={editing} onDone={handleDone} />
          ) : adding ? (
            <CommitmentForm planId={plan.id} editing={null} onDone={handleDone} />
          ) : (
            <Button type="button" onClick={() => setAdding(true)}>
              Add commitment
            </Button>
          )}

          <div className="border-t border-gray-200 pt-4">
            {lockState.error && (
              <p role="alert" className="mb-2 text-sm text-red-600">
                {problemDetailMessage(lockState.error)}
              </p>
            )}
            <Button type="button" color="warning" onClick={() => setLockOpen(true)}>
              Lock week
            </Button>
          </div>
        </>
      )}

      <LockConfirm
        open={lockOpen}
        empty={isEmpty}
        pending={lockState.isLoading}
        onConfirm={() => void handleLock()}
        onCancel={() => setLockOpen(false)}
      />
    </section>
  );
}
