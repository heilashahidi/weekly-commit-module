import { fireEvent, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { setTokenProvider } from '../auth/tokenProvider';
import {
  type WeeklyPlanDto,
  useGetCurrentPlanQuery,
  useGetManagerReviewQuery,
  useLockMutation,
} from '../store/api';
import { jsonResponse, renderWithStore } from '../test/renderWithStore';

const DRAFT_PLAN: WeeklyPlanDto = {
  id: 'plan-1',
  owner: 'auth0|ic',
  weekKey: '2026-W23',
  status: 'DRAFT',
  lockType: null,
  noPlan: false,
  statusDeadline: null,
  commitmentCount: 0,
};

const LOCKED_PLAN: WeeklyPlanDto = { ...DRAFT_PLAN, status: 'LOCKED', lockType: 'USER_LOCKED' };

/** Tiny harness: shows the current plan status and a Lock button. */
function PlanHarness() {
  const { data, isLoading } = useGetCurrentPlanQuery();
  const [lock] = useLockMutation();
  if (isLoading || !data) return <p>loading</p>;
  return (
    <div>
      <p>status: {data.status}</p>
      <button onClick={() => void lock(data.id)}>Lock</button>
    </div>
  );
}

/** Harness for the 204 manager-review query. */
function ReviewHarness() {
  const { data, isLoading, isError, isSuccess } = useGetManagerReviewQuery('plan-1');
  if (isLoading) return <p>loading</p>;
  if (isError) return <p role="alert">error</p>;
  return (
    <div>
      <p>success: {String(isSuccess)}</p>
      <p>review: {data ? data.comment : 'none'}</p>
    </div>
  );
}

describe('lifecycle RTK Query layer', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    setTokenProvider(async () => null);
  });

  it('issues getCurrentPlan with the bearer token and returns typed data', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) =>
      jsonResponse(DRAFT_PLAN),
    );
    vi.stubGlobal('fetch', fetchMock);
    setTokenProvider(async () => 'test-token');

    renderWithStore(<PlanHarness />);

    await waitFor(() => expect(screen.getByText('status: DRAFT')).toBeInTheDocument(), {
      timeout: 3000,
    });
    const request = fetchMock.mock.calls[0][0] as Request;
    expect(request.url).toContain('/api/lifecycle/plans/current');
    expect(request.headers.get('Authorization')).toBe('Bearer test-token');
  });

  it('lock POSTs to the right URL and its invalidatesTags refetch the plan', async () => {
    // First the current plan (DRAFT), then the lock POST, then the refetch (LOCKED).
    const responses = [jsonResponse(DRAFT_PLAN), jsonResponse(LOCKED_PLAN), jsonResponse(LOCKED_PLAN)];
    let call = 0;
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => {
      const r = responses[Math.min(call, responses.length - 1)];
      call += 1;
      return r;
    });
    vi.stubGlobal('fetch', fetchMock);

    renderWithStore(<PlanHarness />);
    await waitFor(() => expect(screen.getByText('status: DRAFT')).toBeInTheDocument(), {
      timeout: 3000,
    });
    const planFetchCount = fetchMock.mock.calls.length;

    fireEvent.click(screen.getByText('Lock'));

    // The lock POST fires at the right URL with method POST.
    await waitFor(
      () => {
        const lockCall = fetchMock.mock.calls.find((c) => {
          const req = c[0] as Request;
          return req.url.includes('/transitions/lock');
        });
        expect(lockCall).toBeDefined();
        expect((lockCall![0] as Request).method).toBe('POST');
      },
      { timeout: 3000 },
    );

    // invalidates+['WeeklyPlan'] refetches the plan query -> view flips to LOCKED.
    await waitFor(() => expect(screen.getByText('status: LOCKED')).toBeInTheDocument(), {
      timeout: 3000,
    });
    // More fetches than before the lock (POST + refetch).
    expect(fetchMock.mock.calls.length).toBeGreaterThan(planFetchCount);
  });

  it('getManagerReview tolerates a 204 (resolves to null data, no error)', async () => {
    const fetchMock = vi.fn(
      async (_input: RequestInfo | URL, _init?: RequestInit) => new Response(null, { status: 204 }),
    );
    vi.stubGlobal('fetch', fetchMock);

    renderWithStore(<ReviewHarness />);

    await waitFor(() => expect(screen.getByText('success: true')).toBeInTheDocument(), {
      timeout: 3000,
    });
    expect(screen.getByText('review: none')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
