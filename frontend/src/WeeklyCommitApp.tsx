import { lazy, Suspense } from 'react';

// Lazy route boundary from day one: the exposed module code-splits the app so
// the host's lazy-loading requirement (workstream D) is additive, not a refactor.
const App = lazy(() => import('./App'));

/**
 * The module exposed to the PA host via Module Federation. Also used by the
 * standalone bootstrap, so both entry points render the same component.
 */
export default function WeeklyCommitApp() {
  return (
    <Suspense fallback={<div className="p-4 text-gray-500">Loading Weekly Commit…</div>}>
      <App />
    </Suspense>
  );
}
