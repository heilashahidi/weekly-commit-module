import { fireEvent, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { setTokenProvider } from '../auth/tokenProvider';
import RcdoPicker from '../components/RcdoPicker';
import { type RcdoNode } from '../store/api';
import { jsonResponse, renderWithStore } from '../test/renderWithStore';

const LEAF: RcdoNode = {
  id: 'so-1',
  nodeType: 'SUPPORTING_OUTCOME',
  title: 'Ship usage-based billing',
  description: null,
  parentId: 'oc-1',
  children: [],
};

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
            children: [LEAF],
          },
        ],
      },
    ],
  },
];

function stubFetch(body: unknown, status = 200) {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) =>
      jsonResponse(body, status),
    ),
  );
}

describe('RcdoPicker', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    setTokenProvider(async () => null);
  });

  it('selecting a Supporting Outcome leaf calls onSelect with the node and closes (AE-D2)', async () => {
    stubFetch(SAMPLE_TREE);
    const onSelect = vi.fn();
    const onClose = vi.fn();
    renderWithStore(<RcdoPicker open onClose={onClose} onSelect={onSelect} />);

    await waitFor(
      () => expect(screen.getByText('Ship usage-based billing')).toBeInTheDocument(),
      { timeout: 3000 },
    );

    fireEvent.click(screen.getByText('Ship usage-based billing'));

    expect(onSelect).toHaveBeenCalledTimes(1);
    expect(onSelect).toHaveBeenCalledWith(LEAF);
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('does not make non-Supporting-Outcome nodes selectable', async () => {
    stubFetch(SAMPLE_TREE);
    const onSelect = vi.fn();
    const onClose = vi.fn();
    renderWithStore(<RcdoPicker open onClose={onClose} onSelect={onSelect} />);

    await waitFor(
      () => expect(screen.getByText('Become the category leader')).toBeInTheDocument(),
      { timeout: 3000 },
    );

    // Clicking a Rally Cry (non-leaf) must not select anything.
    fireEvent.click(screen.getByText('Become the category leader'));
    // And clicking an Outcome (non-leaf) must not select anything either.
    fireEvent.click(screen.getByText('Increase ARR by 20%'));

    expect(onSelect).not.toHaveBeenCalled();
    expect(onClose).not.toHaveBeenCalled();
  });

  it('renders all hierarchy levels for navigation context', async () => {
    stubFetch(SAMPLE_TREE);
    renderWithStore(<RcdoPicker open onClose={vi.fn()} onSelect={vi.fn()} />);

    await waitFor(
      () => expect(screen.getByText('Become the category leader')).toBeInTheDocument(),
      { timeout: 3000 },
    );
    // Top-level Rally Cry and the nested leaf both appear (full ancestry context).
    expect(screen.getByText('Grow recurring revenue')).toBeInTheDocument();
    expect(screen.getByText('Increase ARR by 20%')).toBeInTheDocument();
    expect(screen.getByText('Ship usage-based billing')).toBeInTheDocument();
  });

  it('shows a loading state while the tree query is pending', () => {
    stubFetch(SAMPLE_TREE);
    renderWithStore(<RcdoPicker open onClose={vi.fn()} onSelect={vi.fn()} />);
    expect(screen.getByText(/Loading strategy hierarchy/)).toBeInTheDocument();
  });

  it('shows an error state (role=alert) when the tree query fails', async () => {
    stubFetch({ error: 'boom' }, 500);
    renderWithStore(<RcdoPicker open onClose={vi.fn()} onSelect={vi.fn()} />);
    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument(), {
      timeout: 3000,
    });
  });

  it('shows an empty state when the tree has no nodes', async () => {
    stubFetch([]);
    renderWithStore(<RcdoPicker open onClose={vi.fn()} onSelect={vi.fn()} />);
    await waitFor(() => expect(screen.getByText(/No strategy nodes yet/)).toBeInTheDocument(), {
      timeout: 3000,
    });
  });
});
