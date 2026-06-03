import { screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { setTokenProvider } from '../auth/tokenProvider';
import RcdoTree from '../routes/RcdoTree';
import { type RcdoNode } from '../store/api';
import { jsonResponse, renderWithStore } from '../test/renderWithStore';

const SAMPLE_TREE: RcdoNode[] = [
  {
    id: 'rc-1',
    nodeType: 'RALLY_CRY',
    title: 'Become the category leader',
    description: null,
    parentId: null,
    children: [
      {
        id: 'do-1',
        nodeType: 'DEFINING_OBJECTIVE',
        title: 'Grow recurring revenue',
        description: null,
        parentId: 'rc-1',
        children: [
          {
            id: 'oc-1',
            nodeType: 'OUTCOME',
            title: 'Increase ARR by 20%',
            description: null,
            parentId: 'do-1',
            children: [
              {
                id: 'so-1',
                nodeType: 'SUPPORTING_OUTCOME',
                title: 'Ship usage-based billing',
                description: null,
                parentId: 'oc-1',
                children: [],
              },
            ],
          },
        ],
      },
    ],
  },
];

describe('RcdoTree', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    setTokenProvider(async () => null);
  });

  it('renders the root and a nested leaf when the query resolves', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => jsonResponse(SAMPLE_TREE)),
    );
    renderWithStore(<RcdoTree />);
    await waitFor(
      () => expect(screen.getByText('Become the category leader')).toBeInTheDocument(),
      { timeout: 3000 },
    );
    // Nested leaf four levels deep is rendered.
    expect(screen.getByText('Ship usage-based billing')).toBeInTheDocument();
  });

  it('shows a loading state before the query resolves', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => jsonResponse(SAMPLE_TREE)),
    );
    renderWithStore(<RcdoTree />);
    // Loading branch is rendered synchronously before the resolved data arrives.
    expect(screen.getByText(/Loading strategy hierarchy/)).toBeInTheDocument();
    await waitFor(
      () => expect(screen.getByText('Become the category leader')).toBeInTheDocument(),
      { timeout: 3000 },
    );
  });

  it('renders the empty state when the tree has no nodes', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => jsonResponse([])),
    );
    renderWithStore(<RcdoTree />);
    await waitFor(() => expect(screen.getByText(/No strategy nodes yet/)).toBeInTheDocument(), {
      timeout: 3000,
    });
  });

  it('renders an error state when the query fails', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) =>
        jsonResponse({ error: 'boom' }, 500),
      ),
    );
    renderWithStore(<RcdoTree />);
    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument(), { timeout: 3000 });
  });

  it('attaches the in-memory bearer token to the request', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) =>
      jsonResponse(SAMPLE_TREE),
    );
    vi.stubGlobal('fetch', fetchMock);
    setTokenProvider(async () => 'test-token');

    renderWithStore(<RcdoTree />);

    await waitFor(() => expect(fetchMock).toHaveBeenCalled(), { timeout: 3000 });
    const request = fetchMock.mock.calls[0][0] as Request;
    expect(request.headers.get('Authorization')).toBe('Bearer test-token');
  });
});
