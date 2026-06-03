import { useState } from 'react';
import { Button } from 'flowbite-react';
import {
  useCreateCommitmentMutation,
  useUpdateCommitmentMutation,
  type CommitmentDto,
  type RcdoNode,
} from '../store/api';
import { problemDetailMessage } from '../lib/problemDetail';
import RcdoPicker from './RcdoPicker';

interface CommitmentFormProps {
  planId: string;
  /** The row under edit, or `null`/omitted to create a new commitment. */
  editing?: CommitmentDto | null;
  onDone: () => void;
  /** Field label (also the accessible name the title input is queried by). */
  titleLabel?: string;
  /** Unique id tying the label to the input. */
  inputId?: string;
  placeholder?: string;
  /** Submit-button text. Defaults to Save changes (editing) / Add commitment. */
  submitLabel?: string;
}

/**
 * The shared add/edit commitment form (UX-R4 / AE-D1). A title field plus a
 * **Pick Supporting Outcome** button that opens the {@link RcdoPicker}. Save is
 * disabled until an Outcome is selected — and no create/update request fires
 * without one, because `handleSave` guards on the effective node id. Used by the
 * DRAFT view (create + edit) and the LOCKED view (create-only, for appending
 * unplanned commitments) with different copy; the link-gating logic lives here
 * once rather than being duplicated per view.
 */
export default function CommitmentForm({
  planId,
  editing = null,
  onDone,
  titleLabel = 'Commitment title',
  inputId = 'commitment-title',
  placeholder = 'What will you commit to?',
  submitLabel,
}: CommitmentFormProps) {
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
  const resolvedSubmitLabel = submitLabel ?? (editing ? 'Save changes' : 'Add commitment');

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
      <label className="block text-sm font-medium text-gray-700" htmlFor={inputId}>
        {titleLabel}
      </label>
      <input
        id={inputId}
        type="text"
        value={title}
        onChange={(e) => setTitle(e.target.value)}
        placeholder={placeholder}
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
          {resolvedSubmitLabel}
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
