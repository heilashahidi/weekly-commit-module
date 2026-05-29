import { configureStore } from '@reduxjs/toolkit';
import { render, screen, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { setTokenProvider } from '../auth/tokenProvider';
import HealthCheck from '../routes/HealthCheck';
import { api } from '../store/api';

function renderWithStore() {
  const store = configureStore({
    reducer: { [api.reducerPath]: api.reducer },
    middleware: (getDefault) => getDefault().concat(api.middleware),
  });
  return render(
    <Provider store={store}>
      <HealthCheck />
    </Provider>,
  );
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('HealthCheck', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    setTokenProvider(async () => null);
  });

  it('renders the backend status when the query resolves', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => jsonResponse({ status: 'UP' })),
    );
    renderWithStore();
    await waitFor(
      () => expect(screen.getByText(/Backend status: UP/)).toBeInTheDocument(),
      { timeout: 3000 },
    );
  });

  it('renders an error state when the query fails', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) =>
        jsonResponse({ error: 'boom' }, 500),
      ),
    );
    renderWithStore();
    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument(), { timeout: 3000 });
  });

  it('attaches the in-memory bearer token to the request', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) =>
      jsonResponse({ status: 'UP' }),
    );
    vi.stubGlobal('fetch', fetchMock);
    setTokenProvider(async () => 'test-token');

    renderWithStore();

    await waitFor(() => expect(fetchMock).toHaveBeenCalled(), { timeout: 3000 });
    const request = fetchMock.mock.calls[0][0] as Request;
    expect(request.headers.get('Authorization')).toBe('Bearer test-token');
  });
});
