import { lazy, Suspense, useEffect, useRef, useState } from 'react';
import TeamBoard from './manager/TeamBoard';

// Drill-in detail loads behind a lazy boundary (mirrors MyWeek's no-router pattern):
// navigation is local selection state, not a route.
const ReportPlanDetail = lazy(() => import('./manager/ReportPlanDetail'));

interface Selection {
  planId: string;
  displayName: string;
}

/**
 * The manager dashboard shell (F-U5/U6). Home is the current-week team board; opening
 * a report's row swaps to a read-only plan detail with review writing (U6). There is
 * no router (house style) — navigation is the `selection` state. Because the browser
 * isn't managing focus across the swap, focus is moved to the detail heading on open
 * and back to the board heading on return, so keyboard/SR users aren't stranded.
 */
export default function ManagerDashboard() {
  const [selection, setSelection] = useState<Selection | null>(null);
  const returnedToBoard = useRef(false);
  const boardHeadingRef = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    if (selection === null && returnedToBoard.current) {
      boardHeadingRef.current?.focus();
      returnedToBoard.current = false;
    }
  }, [selection]);

  const handleBack = () => {
    returnedToBoard.current = true;
    setSelection(null);
  };

  return (
    <main className="flex min-h-screen justify-center bg-gray-50 p-8">
      <div className="w-full max-w-3xl space-y-6">
        {selection === null ? (
          <>
            <h1 ref={boardHeadingRef} tabIndex={-1} className="text-2xl font-semibold text-gray-800">
              Team — this week
            </h1>
            <TeamBoard
              onSelectPlan={(planId, displayName) => setSelection({ planId, displayName })}
            />
          </>
        ) : (
          <Suspense fallback={<p className="text-gray-500">Loading plan…</p>}>
            <ReportPlanDetail
              planId={selection.planId}
              displayName={selection.displayName}
              onBack={handleBack}
            />
          </Suspense>
        )}
      </div>
    </main>
  );
}
