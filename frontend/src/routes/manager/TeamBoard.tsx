import { type OutcomeCountDto, type TeamRowDto, useGetTeamWeekQuery } from '../../store/api';
import { problemDetailMessage } from '../../lib/problemDetail';
import StatusBadge from '../../components/StatusBadge';

const MAX_VISIBLE_CHIPS = 3;

interface TeamBoardProps {
  /** Called when a report row with a plan is opened. */
  onSelectPlan: (planId: string, displayName: string) => void;
}

/** A dimmed badge for the "no current-week plan" state — NOT a StatusBadge (status is null). */
function NoPlanBadge() {
  return (
    <span className="inline-block rounded-full bg-gray-50 px-2 py-0.5 text-xs font-medium uppercase tracking-wide text-gray-400">
      no plan yet
    </span>
  );
}

/** Outcome-spread chips, capped at {@link MAX_VISIBLE_CHIPS} with a "+N more" overflow label. */
function OutcomeSpread({ spread }: { spread: OutcomeCountDto[] }) {
  if (spread.length === 0) {
    return <span className="text-sm text-gray-400">—</span>;
  }
  const visible = spread.slice(0, MAX_VISIBLE_CHIPS);
  const overflow = spread.length - visible.length;
  return (
    <div className="flex flex-wrap gap-1">
      {visible.map((s) => (
        <span
          key={s.outcome}
          className="inline-block rounded bg-indigo-50 px-2 py-0.5 text-xs text-indigo-700"
        >
          {s.outcome} ×{s.count}
        </span>
      ))}
      {overflow > 0 && <span className="text-xs text-gray-400">+{overflow} more</span>}
    </div>
  );
}

/** ✓ / — review-done indicator with an accessible label. */
function ReviewIndicator({ reviewExists }: { reviewExists: boolean }) {
  return reviewExists ? (
    <span className="text-green-600" aria-label="Review complete">
      ✓
    </span>
  ) : (
    <span className="text-gray-400" aria-label="No review">
      —
    </span>
  );
}

function Row({
  row,
  onSelectPlan,
}: {
  row: TeamRowDto;
  onSelectPlan: (planId: string, displayName: string) => void;
}) {
  const content = (
    <div className="flex items-center gap-4">
      <span className="w-40 shrink-0 truncate font-medium text-gray-800">{row.displayName}</span>
      <span className="w-28 shrink-0">
        {row.status ? <StatusBadge status={row.status} /> : <NoPlanBadge />}
      </span>
      <span className="min-w-0 flex-1">
        <OutcomeSpread spread={row.outcomeSpread} />
      </span>
      <span className="w-6 shrink-0 text-center">
        <ReviewIndicator reviewExists={row.reviewExists} />
      </span>
    </div>
  );

  // Only rows with a plan are openable; a no-plan row is informational (chase the IC).
  if (row.planId === null) {
    return (
      <li className="rounded border border-gray-200 p-3" data-testid="team-row">
        {content}
      </li>
    );
  }
  const planId = row.planId;
  return (
    <li data-testid="team-row">
      <button
        type="button"
        onClick={() => onSelectPlan(planId, row.displayName)}
        className="w-full rounded border border-gray-200 p-3 text-left hover:bg-gray-50"
      >
        {content}
      </button>
    </li>
  );
}

/**
 * The manager's current-week team status board (F-U5, F1). One row per direct report:
 * status (or a "no plan yet" badge), the RCDO outcome spread, and a review-done
 * indicator. Rows with a plan open the read-only detail (U6). Distinct branches for
 * loading, error (a non-manager 403 surfaces here), and the empty-team case.
 */
export default function TeamBoard({ onSelectPlan }: TeamBoardProps) {
  const { data, isLoading, isError, error } = useGetTeamWeekQuery();

  if (isLoading) {
    return <p className="text-gray-500">Loading your team…</p>;
  }
  if (isError || !data) {
    return (
      <p role="alert" className="text-red-600">
        {problemDetailMessage(error)}
      </p>
    );
  }
  if (data.content.length === 0) {
    return <p className="text-gray-500">No direct reports found for this week.</p>;
  }

  return (
    <ul className="space-y-2" aria-label="Team week board">
      {data.content.map((row) => (
        <Row key={row.reportSub} row={row} onSelectPlan={onSelectPlan} />
      ))}
    </ul>
  );
}
