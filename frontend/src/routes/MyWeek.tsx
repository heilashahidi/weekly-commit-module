import { lazy, Suspense } from 'react';
import { useGetCurrentPlanQuery, type WeeklyPlanDto } from '../store/api';
import { problemDetailMessage } from '../lib/problemDetail';
import StatusBadge from '../components/StatusBadge';

// Heavier mode sub-views load behind a lazy boundary (KTD 1 / UX-R16, UX-R17),
// mirroring WeeklyCommitApp.tsx. The lifecycle state is the navigation — no
// router; the shell switches on plan.status.
const DraftView = lazy(() => import('./myweek/DraftView'));
const LockedView = lazy(() => import('./myweek/LockedView'));
const ReconcileView = lazy(() => import('./myweek/ReconcileView'));
const SummaryView = lazy(() => import('./myweek/SummaryView'));

/** Selects the mode sub-view for the current plan status (KTD 1). */
function ModeView({ plan }: { plan: WeeklyPlanDto }) {
  switch (plan.status) {
    case 'DRAFT':
      return <DraftView plan={plan} />;
    case 'LOCKED':
      return <LockedView plan={plan} />;
    case 'RECONCILING':
      return <ReconcileView plan={plan} />;
    case 'RECONCILED':
      return <SummaryView plan={plan} />;
  }
}

/**
 * Absolute deadline display (UX-R2). No ticking countdown — that's deferred;
 * here we show the deadline as a fixed, readable timestamp. Renders nothing when
 * `statusDeadline` is null.
 */
function StatusDeadline({ deadline }: { deadline: string | null }) {
  if (!deadline) {
    return null;
  }
  const parsed = new Date(deadline);
  const text = Number.isNaN(parsed.getTime()) ? deadline : parsed.toLocaleString();
  return (
    <p className="text-sm text-gray-500">
      Deadline: <time dateTime={deadline}>{text}</time>
    </p>
  );
}

/**
 * The IC's primary surface — the adaptive "My Week" screen. Fetches the current
 * plan and renders the correct mode sub-view by `plan.status` (KTD 1), with the
 * current status and (when set) the deadline always visible (UX-R1, UX-R2,
 * UX-R3). Loading/error/data branches mirror HealthCheck/RcdoTree; the error
 * branch surfaces a ProblemDetail message (KTD 5 / UX-R15, AE-D11).
 */
export default function MyWeek() {
  const { data: plan, isLoading, isError, error } = useGetCurrentPlanQuery();

  if (isLoading) {
    return <p className="text-gray-500">Loading your week…</p>;
  }
  if (isError || !plan) {
    return (
      <p role="alert" className="text-red-600">
        {problemDetailMessage(error)}
      </p>
    );
  }

  return (
    <section className="space-y-4 text-left">
      <header className="flex items-center gap-2">
        <h2 className="text-lg font-medium text-gray-700">My Week</h2>
        <StatusBadge status={plan.status} />
      </header>
      <StatusDeadline deadline={plan.statusDeadline} />
      <Suspense fallback={<p className="text-gray-500">Loading view…</p>}>
        <ModeView plan={plan} />
      </Suspense>
    </section>
  );
}
