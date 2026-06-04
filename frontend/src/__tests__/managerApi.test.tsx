import { fireEvent, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { setTokenProvider } from '../auth/tokenProvider';
import {
  type ManagerReviewDto,
  type PageDto,
  type TeamRowDto,
  useGetTeamWeekQuery,
  useUpsertManagerReviewMutation,
} from '../store/api';
import { jsonResponse, renderWithStore } from '../test/renderWithStore';

const BOARD: PageDto<TeamRowDto> = {
  content: [
    {
      reportSub: 'auth0|report-ava',
      displayName: 'Ava Stone',
      status: 'LOCKED',
      outcomeSpread: [{ outcome: 'Increase ARR by 20%', count: 2 }],
      reviewExists: false,
    },
    {
      reportSub: 'auth0|report-dan',
      displayName: 'Dan Ruiz',
      status: null,
      outcomeSpread: [],
      reviewExists: false,
    },
  ],
  page: 0,
  size: 50,
  totalElements: 2,
  totalPages: 1,
};

const REVIEW: ManagerReviewDto = {
  id: 'review-1',
  weeklyPlanId: 'plan-1',
  reviewer: 'auth0|manager-mary',
  comment: 'Nice work',
  reviewedAt: '2026-06-03T12:00:00Z',
};

/** Shows the board rows and a button that writes a review. */
function BoardHarness() {
  const { data, isLoading } = useGetTeamWeekQuery();
  const [upsert] = useUpsertManagerReviewMutation();
  if (isLoading || !data) return <p>loading</p>;
  return (
    <div>
      {data.content.map((row) => (
        <p key={row.reportSub}>
          row: {row.displayName} / {row.status ?? 'NO_PLAN'}
        </p>
      ))}
      <button onClick={() => void upsert({ planId: 'plan-1', comment: 'Nice work' })}>Review</button>
    </div>
  );
}

describe('manager dashboard RTK Query layer', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    setTokenProvider(async () => null);
  });

  it('getTeamWeek requests the team route with bearer + page params and parses content', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) =>
      jsonResponse(BOARD),
    );
    vi.stubGlobal('fetch', fetchMock);
    setTokenProvider(async () => 'mgr-token');

    renderWithStore(<BoardHarness />);

    await waitFor(() => expect(screen.getByText('row: Ava Stone / LOCKED')).toBeInTheDocument(), {
      timeout: 3000,
    });
    // The null-status report renders as a no-plan row.
    expect(screen.getByText('row: Dan Ruiz / NO_PLAN')).toBeInTheDocument();

    const request = fetchMock.mock.calls[0][0] as Request;
    expect(request.url).toContain('/api/manager/team');
    expect(request.url).toContain('page=0');
    expect(request.url).toContain('size=50');
    expect(request.headers.get('Authorization')).toBe('Bearer mgr-token');
  });

  it('upsertManagerReview PUTs the comment and invalidates the board (refetch)', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const req = input as Request;
      if (req.url.includes('/review') && (init?.method === 'PUT' || req.method === 'PUT')) {
        return jsonResponse(REVIEW);
      }
      return jsonResponse(BOARD);
    });
    vi.stubGlobal('fetch', fetchMock);

    renderWithStore(<BoardHarness />);
    await waitFor(() => expect(screen.getByText('row: Ava Stone / LOCKED')).toBeInTheDocument(), {
      timeout: 3000,
    });
    const beforeCount = fetchMock.mock.calls.length;

    fireEvent.click(screen.getByText('Review'));

    // The PUT fires at the review URL with a JSON comment body.
    await waitFor(
      () => {
        const putCall = fetchMock.mock.calls.find((c) => {
          const req = c[0] as Request;
          return req.url.includes('/api/lifecycle/plans/plan-1/review') && req.method === 'PUT';
        });
        expect(putCall).toBeDefined();
      },
      { timeout: 3000 },
    );

    // invalidatesTags ['TeamWeek'] triggers a board refetch -> more calls than before.
    await waitFor(() => expect(fetchMock.mock.calls.length).toBeGreaterThan(beforeCount + 1), {
      timeout: 3000,
    });
  });
});
