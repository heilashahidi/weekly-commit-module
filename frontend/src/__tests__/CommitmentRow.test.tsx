import { fireEvent, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { setTokenProvider } from '../auth/tokenProvider';
import CommitmentRow from '../components/CommitmentRow';
import { type CommitmentDto, type RcdoNode } from '../store/api';
import { jsonResponse, renderWithStore } from '../test/renderWithStore';

const OUTCOME: RcdoNode = {
  id: 'so-1',
  nodeType: 'SUPPORTING_OUTCOME',
  title: 'Ship usage-based billing',
  description: null,
  parentId: 'oc-1',
  children: [],
};

const PLANNED: CommitmentDto = {
  id: 'c-1',
  weeklyPlanId: 'plan-1',
  rcdoNodeId: 'so-1',
  title: 'Draft pricing tiers',
  planned: true,
  reconciliationStatus: null,
  reconciliationNote: null,
  carriedFromId: null,
  carryWeekCount: 0,
};

const UNPLANNED: CommitmentDto = { ...PLANNED, id: 'c-2', title: 'Hotfix prod', planned: false };

/** Resolve the linked RCDO node title (the spine) for any row. */
function stubNode() {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => jsonResponse(OUTCOME)),
  );
}

describe('CommitmentRow', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    setTokenProvider(async () => null);
  });

  it('always shows the linked Supporting Outcome (UX-R5)', async () => {
    stubNode();
    renderWithStore(<CommitmentRow commitment={PLANNED} mode="draft" />);
    await waitFor(
      () => expect(screen.getByText('Ship usage-based billing')).toBeInTheDocument(),
      { timeout: 3000 },
    );
    expect(screen.getByText(/Supporting Outcome:/)).toBeInTheDocument();
    expect(screen.getByText('Draft pricing tiers')).toBeInTheDocument();
  });

  it('draft mode renders edit/delete and fires the callbacks', async () => {
    stubNode();
    const onEdit = vi.fn();
    const onDelete = vi.fn();
    renderWithStore(
      <CommitmentRow commitment={PLANNED} mode="draft" onEdit={onEdit} onDelete={onDelete} />,
    );
    await waitFor(() => expect(screen.getByText('Edit')).toBeInTheDocument(), { timeout: 3000 });
    fireEvent.click(screen.getByText('Edit'));
    fireEvent.click(screen.getByText('Delete'));
    expect(onEdit).toHaveBeenCalledWith(PLANNED);
    expect(onDelete).toHaveBeenCalledWith(PLANNED);
  });

  it('frozen mode renders no edit/delete controls (UX-R8 reuse seam)', async () => {
    stubNode();
    renderWithStore(<CommitmentRow commitment={PLANNED} mode="frozen" />);
    await waitFor(
      () => expect(screen.getByText('Draft pricing tiers')).toBeInTheDocument(),
      { timeout: 3000 },
    );
    expect(screen.queryByText('Edit')).not.toBeInTheDocument();
    expect(screen.queryByText('Delete')).not.toBeInTheDocument();
  });

  it('shows the unplanned badge when planned is false', async () => {
    stubNode();
    renderWithStore(<CommitmentRow commitment={UNPLANNED} mode="frozen" />);
    await waitFor(() => expect(screen.getByText('unplanned')).toBeInTheDocument(), {
      timeout: 3000,
    });
  });

  it('reconciling mode renders the injected status-control slot (U7 reuse seam)', async () => {
    stubNode();
    renderWithStore(
      <CommitmentRow
        commitment={PLANNED}
        mode="reconciling"
        renderStatusControl={() => <button>Status</button>}
      />,
    );
    await waitFor(() => expect(screen.getByText('Status')).toBeInTheDocument(), { timeout: 3000 });
    expect(screen.queryByText('Edit')).not.toBeInTheDocument();
  });
});
