import { lazy, Suspense } from 'react';
import { Provider } from 'react-redux';
import { store } from './store';

// Lazy route boundary from day one: the exposed module code-splits the app so
// the host's lazy-loading requirement (workstream D) is additive, not a refactor.
const App = lazy(() => import('./App'));

/**
 * The module exposed to the PA host via Module Federation. Self-contained: brings
 * its own Redux store Provider so the remote works standalone or inside a host.
 */
export default function WeeklyCommitApp() {
  return (
    <Provider store={store}>
      <Suspense fallback={<div className="p-4 text-gray-500">Loading Weekly Commit…</div>}>
        <App />
      </Suspense>
    </Provider>
  );
}
