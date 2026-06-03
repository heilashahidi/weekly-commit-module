import { configureStore } from '@reduxjs/toolkit';
import { render } from '@testing-library/react';
import type { ReactElement } from 'react';
import { Provider } from 'react-redux';
import { api } from '../store/api';

/**
 * Renders a component wrapped in a fresh store carrying the RTK Query api slice.
 * Each call builds an isolated store so tests don't share cache state.
 */
export function renderWithStore(ui: ReactElement) {
  const store = configureStore({
    reducer: { [api.reducerPath]: api.reducer },
    middleware: (getDefault) => getDefault().concat(api.middleware),
  });
  return render(<Provider store={store}>{ui}</Provider>);
}

/** Builds a JSON `Response` for stubbing `fetch` in component tests. */
export function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
