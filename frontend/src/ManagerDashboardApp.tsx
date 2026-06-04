import { lazy, Suspense } from 'react';
import { Provider } from 'react-redux';
import { store } from './store';

// Lazy boundary mirroring WeeklyCommitApp: the dashboard code-splits behind the
// exposed module so the host loads it on demand.
const ManagerDashboard = lazy(() => import('./routes/ManagerDashboard'));

/**
 * The manager-dashboard module exposed to the PA host via Module Federation
 * (workstream F). Self-contained: brings its own Redux store Provider so the remote
 * works standalone or inside a host, exactly like {@link WeeklyCommitApp}.
 */
export default function ManagerDashboardApp() {
  return (
    <Provider store={store}>
      <Suspense fallback={<div className="p-4 text-gray-500">Loading Manager Dashboard…</div>}>
        <ManagerDashboard />
      </Suspense>
    </Provider>
  );
}
