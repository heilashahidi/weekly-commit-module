import type { ReactNode } from 'react';
import { useGetRcdoNodeQuery, type CommitmentDto } from '../store/api';

/**
 * The three lifecycle modes a commitment row renders in (KTD reuse seam):
 *
 * - `draft`       — DRAFT (U5): edit/delete controls.
 * - `frozen`      — LOCKED (U6): planned commitments are visibly immutable
 *                   (no controls); unplanned ones still show the `unplanned` badge.
 * - `reconciling` — RECONCILING (U7): a per-commitment status selector.
 *
 * The always-visible RCDO Supporting Outcome link (UX-R5) renders identically in
 * every mode — that consistency is the whole point of one component over thin
 * wrappers. Modes that need extra controls inject them through the optional
 * render slots below rather than forking the component, so U6/U7 extend without
 * rewriting U5.
 */
export type CommitmentRowMode = 'draft' | 'frozen' | 'reconciling';

export interface CommitmentRowProps {
  commitment: CommitmentDto;
  mode: CommitmentRowMode;
  /**
   * DRAFT controls. Rendered in the row's action area only in `draft` mode.
   * U5 passes edit/delete buttons here.
   */
  onEdit?: (commitment: CommitmentDto) => void;
  onDelete?: (commitment: CommitmentDto) => void;
  /**
   * Reuse seam for U7: a render slot for the RECONCILING status selector (and
   * any per-commitment note field). Rendered in the row's action area in
   * `reconciling` mode. Kept as a slot so U7 owns the selector without this
   * component importing the reconcile mutation.
   */
  renderStatusControl?: (commitment: CommitmentDto) => ReactNode;
}

/**
 * Resolves and renders the linked RCDO Supporting Outcome title for a committed
 * row. `CommitmentDto` only carries `rcdoNodeId`, so we fetch the node per row
 * via the existing `getRcdoNode` query (cached + de-duped by RTK Query across
 * rows sharing an Outcome). Pending → show a graceful placeholder; error/absent →
 * fall back to the id rather than blocking the row. This keeps the spine always
 * visible (UX-R5) without threading the node down from every caller. (Richer
 * ancestry context beyond the title is deferred — the parent id alone is a UUID,
 * not human context, so we show only the resolved title.)
 */
function LinkedOutcome({ rcdoNodeId }: { rcdoNodeId: string }) {
  const { data, isLoading } = useGetRcdoNodeQuery(rcdoNodeId);
  let title: string;
  if (data) {
    title = data.title;
  } else if (isLoading) {
    title = 'Loading…';
  } else {
    title = rcdoNodeId;
  }
  return (
    <p className="text-sm font-medium text-blue-700">
      <span className="text-xs uppercase tracking-wide text-gray-400">
        Supporting Outcome:{' '}
      </span>
      {title}
    </p>
  );
}

/**
 * The shared commitment row used across DRAFT (U5), LOCKED (U6) and RECONCILING
 * (U7). It always shows the linked RCDO Supporting Outcome (UX-R5, the spine)
 * plus the commitment title and an `unplanned` badge when `planned === false`.
 * What changes per `mode` is only the action area on the right; the spine stays
 * identical. One component with a `mode` discriminator + optional render slots
 * (per the Deferred note) rather than per-mode wrappers.
 */
export default function CommitmentRow({
  commitment,
  mode,
  onEdit,
  onDelete,
  renderStatusControl,
}: CommitmentRowProps) {
  return (
    <li
      className="flex items-start justify-between gap-4 rounded border border-gray-200 p-3"
      data-testid="commitment-row"
    >
      <div className="min-w-0">
        <LinkedOutcome rcdoNodeId={commitment.rcdoNodeId} />
        <p className="text-gray-800">
          {commitment.title}
          {!commitment.planned && (
            <span className="ml-2 inline-block rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium uppercase tracking-wide text-gray-500">
              unplanned
            </span>
          )}
        </p>
      </div>

      <div className="flex shrink-0 items-center gap-2">
        {mode === 'draft' && (
          <>
            <button
              type="button"
              onClick={() => onEdit?.(commitment)}
              className="rounded border border-gray-300 px-2 py-1 text-sm text-gray-700 hover:bg-gray-50"
            >
              Edit
            </button>
            <button
              type="button"
              onClick={() => onDelete?.(commitment)}
              className="rounded border border-red-300 px-2 py-1 text-sm text-red-700 hover:bg-red-50"
            >
              Delete
            </button>
          </>
        )}
        {mode === 'reconciling' && renderStatusControl?.(commitment)}
        {/* frozen mode renders no controls — immutability is legible (UX-R8). */}
      </div>
    </li>
  );
}
