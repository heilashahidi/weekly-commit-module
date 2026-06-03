import { useState } from 'react';
import { Button } from 'flowbite-react';
import {
  useCarryMutation,
  useGetCarryCandidatesQuery,
  useGetRcdoNodeQuery,
  type CarryCandidateDto,
} from '../../store/api';
import { problemDetailMessage } from '../../lib/problemDetail';

interface CarryForwardPanelProps {
  planId: string;
}

/**
 * Resolves the linked RCDO Supporting Outcome title for a candidate, mirroring
 * `LinkedOutcome` in CommitmentRow: `CarryCandidateDto` only carries the
 * `rcdoNodeId`, so we fetch the node via the existing `getRcdoNode` query
 * (cached + de-duped by RTK Query across rows). Pending → graceful placeholder;
 * error/absent → fall back to the id. This is optional polish over the title +
 * week-count; the spine stays visible so the IC sees what each candidate links
 * to before carrying it forward.
 */
function CandidateOutcome({ rcdoNodeId }: { rcdoNodeId: string }) {
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
 * Carry-forward panel (U9, UX-R12, AE-D8). After a week is RECONCILED it lists
 * the carry candidates from `getCarryCandidates` — the server returns only
 * `PARTIAL`/`NOT_DONE` planned commitments (never `DONE`/`DROPPED`), so AE-D8 is
 * satisfied server-side and this panel simply lists what it gets. Each candidate
 * shows its title, the number of weeks it has been carried (`carryWeekCount`),
 * and the linked Outcome. The IC checks a subset and clicks "Carry selected",
 * which POSTs the chosen `commitmentIds` via the `carry` mutation; that
 * invalidates tags so next week's draft is seeded with the carried items.
 *
 * Selection state is the set of selected candidate ids (a candidate's `id` IS
 * the commitment id). With nothing selected the carry action is disabled so we
 * never issue an empty carry.
 */
export default function CarryForwardPanel({ planId }: CarryForwardPanelProps) {
  const {
    data: candidates,
    isLoading,
    isError,
    error,
  } = useGetCarryCandidatesQuery(planId);

  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [carry, carryState] = useCarryMutation();
  const [carried, setCarried] = useState(false);

  function toggle(id: string) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
    // Selecting again after a successful carry starts a fresh confirmation.
    setCarried(false);
  }

  async function handleCarry() {
    if (selected.size === 0) {
      return;
    }
    try {
      await carry({
        planId,
        body: { commitmentIds: [...selected] },
      }).unwrap();
      setCarried(true);
      setSelected(new Set());
    } catch {
      // Surfaced below via the mutation state; keep the selection.
    }
  }

  return (
    <div className="space-y-3" data-testid="carry-forward-panel">
      <h3 className="text-base font-medium text-gray-700">Carry forward</h3>

      {isLoading && <p className="text-gray-500">Loading carry candidates…</p>}

      {isError && (
        <p role="alert" className="text-sm text-red-600">
          {problemDetailMessage(error)}
        </p>
      )}

      {!isLoading && !isError && (
        <>
          {!Array.isArray(candidates) || candidates.length === 0 ? (
            <p className="text-sm text-gray-500" data-testid="carry-empty">
              No unfinished commitments to carry into next week.
            </p>
          ) : (
            <>
              <p className="text-sm text-gray-500">
                Select unfinished commitments to carry into next week&apos;s draft.
              </p>
              <ul className="space-y-2">
                {candidates.map((candidate: CarryCandidateDto) => (
                  <li
                    key={candidate.id}
                    className="flex items-start gap-3 rounded border border-gray-200 p-3"
                    data-testid="carry-candidate"
                  >
                    <input
                      type="checkbox"
                      className="mt-1"
                      aria-label={`Carry ${candidate.title}`}
                      checked={selected.has(candidate.id)}
                      onChange={() => toggle(candidate.id)}
                    />
                    <div className="min-w-0">
                      <CandidateOutcome rcdoNodeId={candidate.rcdoNodeId} />
                      <p className="text-gray-800">{candidate.title}</p>
                      <p className="text-xs text-gray-500" data-testid="carry-week-count">
                        Carried {candidate.carryWeekCount}{' '}
                        {candidate.carryWeekCount === 1 ? 'week' : 'weeks'}
                      </p>
                    </div>
                  </li>
                ))}
              </ul>

              {carryState.error && (
                <p role="alert" className="text-sm text-red-600">
                  {problemDetailMessage(carryState.error)}
                </p>
              )}

              {carried && (
                <p className="text-sm text-green-700" data-testid="carry-confirmation">
                  Carried into next week&apos;s draft.
                </p>
              )}

              <Button
                type="button"
                onClick={() => void handleCarry()}
                disabled={selected.size === 0 || carryState.isLoading}
              >
                Carry selected
              </Button>
            </>
          )}
        </>
      )}
    </div>
  );
}
