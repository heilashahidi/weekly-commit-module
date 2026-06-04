import { fireEvent, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { setTokenProvider } from '../auth/tokenProvider';
import { type PageDto, type TeamRowDto } from '../store/api';
import TeamBoard from '../routes/manager/TeamBoard';
import { jsonResponse, renderWithStore } from '../test/renderWithStore';

function board(rows: TeamRowDto[]): PageDto<TeamRowDto> {
  return { content: rows, page: 0, size: 50, totalElements: rows.length, totalPages: 1 };
}

const AVA: TeamRowDto = {
  reportSub: 'auth0|report-ava',
  displayName: 'Ava Stone',
  planId: 'plan-ava',
  status: 'LOCKED',
  outcomeSpread: [
    { outcome: 'Increase ARR by 20%', count: 2 },
    { outcome: 'Lift NPS to 50', count: 1 },
  ],
  reviewExists: true,
};

const DAN: TeamRowDto = {
  reportSub: 'auth0|report-dan',
  displayName: 'Dan Ruiz',
  planId: null,
  status: null,
  outcomeSpread: [],
  reviewExists: false,
};

function stub(page: PageDto<TeamRowDto>) {
  const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) =>
    jsonResponse(page),
  );
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

describe('TeamBoard', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    setTokenProvider(async () => null);
  });

  it('renders status, no-plan badge, outcome chips, and review indicators', async () => {
    stub(board([AVA, DAN]));
    renderWithStore(<TeamBoard onSelectPlan={vi.fn()} />);

    await waitFor(() => expect(screen.getByText('Ava Stone')).toBeInTheDocument(), {
      timeout: 3000,
    });
    // Status badge for the planned report; dedicated no-plan badge for the other.
    expect(screen.getByText('Locked')).toBeInTheDocument();
    expect(screen.getByText('no plan yet')).toBeInTheDocument();
    // AE4: outcome chips with labels + counts.
    expect(screen.getByText('Increase ARR by 20% ×2')).toBeInTheDocument();
    expect(screen.getByText('Lift NPS to 50 ×1')).toBeInTheDocument();
    // Review indicators (accessible labels).
    expect(screen.getByLabelText('Review complete')).toBeInTheDocument();
    expect(screen.getByLabelText('No review')).toBeInTheDocument();
  });

  it('caps the outcome spread at 3 chips with a +N more label', async () => {
    const many: TeamRowDto = {
      ...AVA,
      outcomeSpread: [
        { outcome: 'O1', count: 1 },
        { outcome: 'O2', count: 1 },
        { outcome: 'O3', count: 1 },
        { outcome: 'O4', count: 1 },
        { outcome: 'O5', count: 1 },
      ],
    };
    stub(board([many]));
    renderWithStore(<TeamBoard onSelectPlan={vi.fn()} />);

    await waitFor(() => expect(screen.getByText('O1 ×1')).toBeInTheDocument(), { timeout: 3000 });
    expect(screen.getByText('O3 ×1')).toBeInTheDocument();
    expect(screen.queryByText('O4 ×1')).not.toBeInTheDocument();
    expect(screen.getByText('+2 more')).toBeInTheDocument();
  });

  it('opens a planned row but not a no-plan row', async () => {
    stub(board([AVA, DAN]));
    const onSelect = vi.fn();
    renderWithStore(<TeamBoard onSelectPlan={onSelect} />);

    await waitFor(() => expect(screen.getByText('Ava Stone')).toBeInTheDocument(), {
      timeout: 3000,
    });

    // Ava's row is a button; Dan's (no plan) is not.
    fireEvent.click(screen.getByText('Ava Stone'));
    expect(onSelect).toHaveBeenCalledWith('plan-ava', 'Ava Stone');

    fireEvent.click(screen.getByText('Dan Ruiz'));
    expect(onSelect).toHaveBeenCalledTimes(1);
  });

  it('shows an empty state when there are no reports', async () => {
    stub(board([]));
    renderWithStore(<TeamBoard onSelectPlan={vi.fn()} />);

    await waitFor(
      () => expect(screen.getByText('No direct reports found for this week.')).toBeInTheDocument(),
      { timeout: 3000 },
    );
  });

  it('surfaces an error (e.g. non-manager 403) instead of a blank board', async () => {
    const fetchMock = vi.fn(
      async (_input: RequestInfo | URL, _init?: RequestInit) =>
        new Response(JSON.stringify({ detail: 'Not a manager' }), {
          status: 403,
          headers: { 'Content-Type': 'application/problem+json' },
        }),
    );
    vi.stubGlobal('fetch', fetchMock);
    renderWithStore(<TeamBoard onSelectPlan={vi.fn()} />);

    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument(), { timeout: 3000 });
    expect(screen.getByText('Not a manager')).toBeInTheDocument();
  });
});
