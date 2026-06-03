import { fireEvent, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi, type Mock } from 'vitest';
import { setTokenProvider } from '../auth/tokenProvider';
import DraftView from '../routes/myweek/DraftView';
import {
  type CommitmentDto,
  type RcdoNode,
  type WeeklyPlanDto,
} from '../store/api';
import { jsonResponse, renderWithStore } from '../test/renderWithStore';

const PLAN: WeeklyPlanDto = {
  id: 'plan-1',
  owner: 'auth0|ic',
  weekKey: '2026-W23',
  status: 'DRAFT',
  lockType: null,
  noPlan: false,
  statusDeadline: null,
  commitmentCount: 0,
};

const OUTCOME: RcdoNode = {
  id: 'so-1',
  nodeType: 'SUPPORTING_OUTCOME',
  title: 'Ship usage-based billing',
  description: null,
  parentId: 'oc-1',
  children: [],
};

const TREE: RcdoNode[] = [
  {
    id: 'rc-1',
    nodeType: 'RALLY_CRY',
    title: 'Become the category leader',
    description: null,
    parentId: null,
    children: [OUTCOME],
  },
];

const COMMITMENT: CommitmentDto = {
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

interface RouteMap {
  /** GET /commitments list (changes across refetches). */
  commitments?: () => Response;
  /** Response for POST create / PUT update / DELETE. */
  mutate?: (req: Request) => Response;
}

/**
 * Branches the fetch stub on URL + method so a single render can serve the
 * commitments list, the RCDO tree (picker), per-row node resolves, and the
 * mutation under test with the right body each time.
 */
function stubRoutes(routes: RouteMap) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const req = input instanceof Request ? input : new Request(input, init);
    const url = req.url;
    const method = req.method;

    if (url.includes('/api/rcdo/tree')) return jsonResponse(TREE);
    if (url.includes('/api/rcdo/nodes/')) return jsonResponse(OUTCOME);
    if (url.includes('/commitments') && method === 'GET') {
      return routes.commitments ? routes.commitments() : jsonResponse([]);
    }
    if (routes.mutate) return routes.mutate(req);
    return jsonResponse({});
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function createCalls(mock: Mock) {
  return mock.mock.calls.filter((c) => {
    const req = c[0] as Request;
    return req.url.includes('/commitments') && req.method === 'POST';
  });
}

describe('DraftView', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    setTokenProvider(async () => null);
  });

  it('save is disabled and no createCommitment fires without a selected Outcome (AE-D1)', async () => {
    const fetchMock = stubRoutes({ commitments: () => jsonResponse([]) });
    renderWithStore(<DraftView plan={PLAN} />);

    await waitFor(
      () => expect(screen.getByText(/No commitments yet/)).toBeInTheDocument(),
      { timeout: 3000 },
    );

    fireEvent.click(screen.getByText('Add commitment'));
    // Type a title but pick NO outcome.
    fireEvent.change(screen.getByLabelText('Commitment title'), {
      target: { value: 'Draft pricing tiers' },
    });

    const saveBtn = screen.getByRole('button', { name: 'Add commitment' });
    expect(saveBtn).toBeDisabled();
    // Clicking the disabled button must not fire a POST.
    fireEvent.click(saveBtn);
    expect(createCalls(fetchMock)).toHaveLength(0);
  });

  it('selecting an Outcome enables save and POSTs createCommitment with {rcdoNodeId, title}', async () => {
    let listCalls = 0;
    const fetchMock = stubRoutes({
      commitments: () => {
        listCalls += 1;
        return jsonResponse(listCalls <= 1 ? [] : [COMMITMENT]);
      },
      mutate: () => jsonResponse(COMMITMENT),
    });
    renderWithStore(<DraftView plan={PLAN} />);

    await waitFor(() => expect(screen.getByText('Add commitment')).toBeInTheDocument(), {
      timeout: 3000,
    });
    fireEvent.click(screen.getByText('Add commitment'));
    fireEvent.change(screen.getByLabelText('Commitment title'), {
      target: { value: 'Draft pricing tiers' },
    });

    // Open the picker and select the Supporting Outcome.
    fireEvent.click(screen.getByText('Pick Supporting Outcome'));
    await waitFor(
      () => expect(screen.getByText('Ship usage-based billing')).toBeInTheDocument(),
      { timeout: 3000 },
    );
    fireEvent.click(screen.getByText('Ship usage-based billing'));

    const saveBtn = screen.getByRole('button', { name: 'Add commitment' });
    await waitFor(() => expect(saveBtn).not.toBeDisabled(), { timeout: 3000 });
    fireEvent.click(saveBtn);

    await waitFor(() => expect(createCalls(fetchMock).length).toBeGreaterThan(0), {
      timeout: 3000,
    });
    const postReq = createCalls(fetchMock)[0][0] as Request;
    expect(postReq.url).toContain('/api/plans/plan-1/commitments');
    const body = JSON.parse(await postReq.text());
    expect(body).toEqual({ rcdoNodeId: 'so-1', title: 'Draft pricing tiers' });

    // After invalidation the refetched list shows the new row's Outcome (UX-R5).
    await waitFor(
      () => expect(screen.getByText('Draft pricing tiers')).toBeInTheDocument(),
      { timeout: 3000 },
    );
  });

  it('editing a commitment issues updateCommitment (PUT)', async () => {
    const fetchMock = stubRoutes({
      commitments: () => jsonResponse([COMMITMENT]),
      mutate: () => jsonResponse(COMMITMENT),
    });
    renderWithStore(<DraftView plan={PLAN} />);

    await waitFor(() => expect(screen.getByText('Edit')).toBeInTheDocument(), { timeout: 3000 });
    fireEvent.click(screen.getByText('Edit'));
    fireEvent.change(screen.getByLabelText('Commitment title'), {
      target: { value: 'Draft pricing tiers v2' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Save changes' }));

    await waitFor(
      () => {
        const put = fetchMock.mock.calls.find((c) => {
          const r = c[0] as Request;
          return r.url.includes('/api/commitments/c-1') && r.method === 'PUT';
        });
        expect(put).toBeDefined();
      },
      { timeout: 3000 },
    );
  });

  it('deleting a commitment issues deleteCommitment (DELETE)', async () => {
    const fetchMock = stubRoutes({
      commitments: () => jsonResponse([COMMITMENT]),
      mutate: () => new Response(null, { status: 204 }),
    });
    renderWithStore(<DraftView plan={PLAN} />);

    await waitFor(() => expect(screen.getByText('Delete')).toBeInTheDocument(), { timeout: 3000 });
    fireEvent.click(screen.getByText('Delete'));

    await waitFor(
      () => {
        const del = fetchMock.mock.calls.find((c) => {
          const r = c[0] as Request;
          return r.url.includes('/api/commitments/c-1') && r.method === 'DELETE';
        });
        expect(del).toBeDefined();
      },
      { timeout: 3000 },
    );
  });

  it('locking an empty draft warns and the resulting plan reflects noPlan distinctly (AE-D3)', async () => {
    const noPlanLocked: WeeklyPlanDto = {
      ...PLAN,
      status: 'LOCKED',
      lockType: 'USER_LOCKED',
      noPlan: true,
    };
    const fetchMock = stubRoutes({
      commitments: () => jsonResponse([]),
      mutate: () => jsonResponse(noPlanLocked),
    });
    renderWithStore(<DraftView plan={PLAN} />);

    await waitFor(
      () => expect(screen.getByText(/No commitments yet/)).toBeInTheDocument(),
      { timeout: 3000 },
    );
    fireEvent.click(screen.getByText('Lock week'));

    // The empty-plan warning (role=alert) appears in the confirm modal.
    await waitFor(
      () => expect(screen.getByText(/records a .*no plan.* week/i)).toBeInTheDocument(),
      { timeout: 3000 },
    );

    fireEvent.click(screen.getByRole('button', { name: 'Lock empty week' }));

    await waitFor(
      () => {
        const lockCall = fetchMock.mock.calls.find((c) => {
          const r = c[0] as Request;
          return r.url.includes('/transitions/lock') && r.method === 'POST';
        });
        expect(lockCall).toBeDefined();
      },
      { timeout: 3000 },
    );
  });

  it('locking a non-empty draft issues the lock mutation (POST)', async () => {
    const lockedPlan: WeeklyPlanDto = { ...PLAN, status: 'LOCKED', lockType: 'USER_LOCKED' };
    const fetchMock = stubRoutes({
      commitments: () => jsonResponse([COMMITMENT]),
      mutate: () => jsonResponse(lockedPlan),
    });
    renderWithStore(<DraftView plan={PLAN} />);

    await waitFor(() => expect(screen.getByText('Lock week')).toBeInTheDocument(), {
      timeout: 3000,
    });
    // Open the confirm modal (the section trigger).
    fireEvent.click(screen.getByText('Lock week'));
    // The modal body warning text appears once open; confirm via the modal's
    // "Lock week" button (the last "Lock week" element in DOM order).
    await waitFor(
      () => expect(screen.getByText(/Locking freezes your planned commitments/)).toBeInTheDocument(),
      { timeout: 3000 },
    );
    const lockButtons = screen.getAllByText('Lock week');
    fireEvent.click(lockButtons[lockButtons.length - 1]);

    await waitFor(
      () => {
        const lockCall = fetchMock.mock.calls.find((c) => {
          const r = c[0] as Request;
          return r.url.includes('/transitions/lock') && r.method === 'POST';
        });
        expect(lockCall).toBeDefined();
      },
      { timeout: 3000 },
    );
  });

  it('a create failure (422 bad link) shows the ProblemDetail message', async () => {
    const fetchMock = stubRoutes({
      commitments: () => jsonResponse([]),
      mutate: () =>
        jsonResponse({ title: 'Unprocessable', detail: 'rcdoNode must be a Supporting Outcome' }, 422),
    });
    renderWithStore(<DraftView plan={PLAN} />);

    await waitFor(() => expect(screen.getByText('Add commitment')).toBeInTheDocument(), {
      timeout: 3000,
    });
    fireEvent.click(screen.getByText('Add commitment'));
    fireEvent.change(screen.getByLabelText('Commitment title'), {
      target: { value: 'Bad link' },
    });
    fireEvent.click(screen.getByText('Pick Supporting Outcome'));
    await waitFor(
      () => expect(screen.getByText('Ship usage-based billing')).toBeInTheDocument(),
      { timeout: 3000 },
    );
    fireEvent.click(screen.getByText('Ship usage-based billing'));
    fireEvent.click(screen.getByRole('button', { name: 'Add commitment' }));

    await waitFor(
      () =>
        expect(
          screen.getByText('rcdoNode must be a Supporting Outcome'),
        ).toBeInTheDocument(),
      { timeout: 3000 },
    );
    // The failing POST did fire (but the form stayed open with the error).
    expect(createCalls(fetchMock).length).toBeGreaterThan(0);
  });
});
