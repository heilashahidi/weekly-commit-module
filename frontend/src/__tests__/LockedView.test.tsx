import { fireEvent, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi, type Mock } from 'vitest';
import { setTokenProvider } from '../auth/tokenProvider';
import LockedView from '../routes/myweek/LockedView';
import {
  type CommitmentDto,
  type ManagerReviewDto,
  type RcdoNode,
  type WeeklyPlanDto,
} from '../store/api';
import { jsonResponse, renderWithStore } from '../test/renderWithStore';

const PLAN: WeeklyPlanDto = {
  id: 'plan-1',
  owner: 'auth0|ic',
  weekKey: '2026-W23',
  status: 'LOCKED',
  lockType: 'USER_LOCKED',
  noPlan: false,
  statusDeadline: '2026-06-08T17:00:00Z',
  commitmentCount: 1,
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

const UNPLANNED: CommitmentDto = {
  id: 'c-2',
  weeklyPlanId: 'plan-1',
  rcdoNodeId: 'so-1',
  title: 'Hotfix billing rounding bug',
  planned: false,
  reconciliationStatus: null,
  reconciliationNote: null,
  carriedFromId: null,
  carryWeekCount: 0,
};

const REVIEW: ManagerReviewDto = {
  id: 'rev-1',
  weeklyPlanId: 'plan-1',
  reviewer: 'auth0|manager',
  comment: 'Strong focus this week — keep it up.',
  reviewedAt: '2026-06-07T12:00:00Z',
};

interface RouteMap {
  /** GET /commitments list (may change across refetches). */
  commitments?: () => Response;
  /** GET /review — defaults to 204 (no review) when omitted. */
  review?: () => Response;
  /** Response for POST create / POST start-reconciling. */
  mutate?: (req: Request) => Response;
}

/**
 * Branches the fetch stub on URL + method so a single render serves the
 * commitments list, the manager-review GET (204 by default — absence is valid),
 * the RCDO tree (picker), per-row node resolves, and the mutation under test.
 */
function stubRoutes(routes: RouteMap) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const req = input instanceof Request ? input : new Request(input, init);
    const url = req.url;
    const method = req.method;

    if (url.includes('/api/rcdo/tree')) return jsonResponse(TREE);
    if (url.includes('/api/rcdo/nodes/')) return jsonResponse(OUTCOME);
    if (url.includes('/review') && method === 'GET') {
      return routes.review ? routes.review() : new Response(null, { status: 204 });
    }
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

function startReconcilingCalls(mock: Mock) {
  return mock.mock.calls.filter((c) => {
    const req = c[0] as Request;
    return req.url.includes('/transitions/start-reconciling') && req.method === 'POST';
  });
}

describe('LockedView', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    setTokenProvider(async () => null);
  });

  it('renders a planned commitment frozen — no edit/delete controls (AE-D4)', async () => {
    stubRoutes({ commitments: () => jsonResponse([PLANNED]) });
    renderWithStore(<LockedView plan={PLAN} />);

    await waitFor(
      () => expect(screen.getByText('Draft pricing tiers')).toBeInTheDocument(),
      { timeout: 3000 },
    );

    // Frozen rows are legibly immutable: no edit/delete affordance at all.
    expect(screen.queryByRole('button', { name: 'Edit' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Delete' })).toBeNull();
  });

  it('adds an unplanned commitment — POSTs createCommitment and the new row shows the unplanned badge while planned rows stay frozen (AE-D5)', async () => {
    let listCalls = 0;
    const fetchMock = stubRoutes({
      commitments: () => {
        listCalls += 1;
        // First load: only the frozen planned row. After create+invalidation:
        // the server has flagged the new row planned=false.
        return jsonResponse(listCalls <= 1 ? [PLANNED] : [PLANNED, UNPLANNED]);
      },
      mutate: () => jsonResponse(UNPLANNED),
    });
    renderWithStore(<LockedView plan={PLAN} />);

    await waitFor(
      () => expect(screen.getByText('Draft pricing tiers')).toBeInTheDocument(),
      { timeout: 3000 },
    );

    fireEvent.click(screen.getByText('Add unplanned commitment'));
    fireEvent.change(screen.getByLabelText('Unplanned commitment title'), {
      target: { value: 'Hotfix billing rounding bug' },
    });

    fireEvent.click(screen.getByText('Pick Supporting Outcome'));
    // The picker renders the leaf as a selectable button; the frozen row renders
    // the same outcome title as plain text. Target the button to disambiguate.
    const pickerLeaf = await screen.findByRole(
      'button',
      { name: /Ship usage-based billing/ },
      { timeout: 3000 },
    );
    fireEvent.click(pickerLeaf);

    const saveBtn = screen.getByRole('button', { name: 'Add unplanned commitment' });
    await waitFor(() => expect(saveBtn).not.toBeDisabled(), { timeout: 3000 });
    fireEvent.click(saveBtn);

    await waitFor(() => expect(createCalls(fetchMock).length).toBeGreaterThan(0), {
      timeout: 3000,
    });
    const postReq = createCalls(fetchMock)[0][0] as Request;
    expect(postReq.url).toContain('/api/plans/plan-1/commitments');
    const body = JSON.parse(await postReq.text());
    expect(body).toEqual({ rcdoNodeId: 'so-1', title: 'Hotfix billing rounding bug' });

    // After invalidation the refetched list shows the new unplanned row.
    await waitFor(
      () => expect(screen.getByText('Hotfix billing rounding bug')).toBeInTheDocument(),
      { timeout: 3000 },
    );
    // The unplanned badge appears (rendered by CommitmentRow for planned=false).
    expect(screen.getByText('unplanned')).toBeInTheDocument();
    // Planned rows remain frozen throughout — still no controls.
    expect(screen.queryByRole('button', { name: 'Edit' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Delete' })).toBeNull();
  });

  it('displays the statusDeadline and Start reconciling issues startReconciling (POST)', async () => {
    const reconcilingPlan: WeeklyPlanDto = { ...PLAN, status: 'RECONCILING' };
    const fetchMock = stubRoutes({
      commitments: () => jsonResponse([PLANNED]),
      mutate: () => jsonResponse(reconcilingPlan),
    });
    renderWithStore(<LockedView plan={PLAN} />);

    await waitFor(
      () => expect(screen.getByText(/Auto-advance deadline:/)).toBeInTheDocument(),
      { timeout: 3000 },
    );

    fireEvent.click(screen.getByRole('button', { name: 'Start reconciling' }));

    await waitFor(() => expect(startReconcilingCalls(fetchMock).length).toBeGreaterThan(0), {
      timeout: 3000,
    });
    const req = startReconcilingCalls(fetchMock)[0][0] as Request;
    expect(req.url).toContain('/api/lifecycle/plans/plan-1/transitions/start-reconciling');
  });

  it('displays the manager review comment read-only when one exists (AE-D9)', async () => {
    stubRoutes({
      commitments: () => jsonResponse([PLANNED]),
      review: () => jsonResponse(REVIEW),
    });
    renderWithStore(<LockedView plan={PLAN} />);

    await waitFor(
      () =>
        expect(
          screen.getByText('Strong focus this week — keep it up.'),
        ).toBeInTheDocument(),
      { timeout: 3000 },
    );
    expect(screen.getByLabelText('Manager review')).toBeInTheDocument();
  });

  it('does not display a manager review when none exists (204 → nothing)', async () => {
    stubRoutes({ commitments: () => jsonResponse([PLANNED]) });
    renderWithStore(<LockedView plan={PLAN} />);

    await waitFor(
      () => expect(screen.getByText('Draft pricing tiers')).toBeInTheDocument(),
      { timeout: 3000 },
    );
    expect(screen.queryByLabelText('Manager review')).toBeNull();
  });

  it('renders nothing for the deadline when statusDeadline is null', async () => {
    stubRoutes({ commitments: () => jsonResponse([PLANNED]) });
    renderWithStore(<LockedView plan={{ ...PLAN, statusDeadline: null }} />);

    await waitFor(
      () => expect(screen.getByText('Draft pricing tiers')).toBeInTheDocument(),
      { timeout: 3000 },
    );
    expect(screen.queryByText(/Auto-advance deadline:/)).toBeNull();
  });

  it('blocks adding an unplanned commitment without an Outcome (link required) — save disabled, no POST', async () => {
    const fetchMock = stubRoutes({ commitments: () => jsonResponse([PLANNED]) });
    renderWithStore(<LockedView plan={PLAN} />);

    await waitFor(
      () => expect(screen.getByText('Add unplanned commitment')).toBeInTheDocument(),
      { timeout: 3000 },
    );

    fireEvent.click(screen.getByText('Add unplanned commitment'));
    fireEvent.change(screen.getByLabelText('Unplanned commitment title'), {
      target: { value: 'Hotfix billing rounding bug' },
    });

    const saveBtn = screen.getByRole('button', { name: 'Add unplanned commitment' });
    expect(saveBtn).toBeDisabled();
    fireEvent.click(saveBtn);
    expect(createCalls(fetchMock)).toHaveLength(0);
  });

  it('surfaces a ProblemDetail message when the create mutation fails (4xx)', async () => {
    const fetchMock = stubRoutes({
      commitments: () => jsonResponse([PLANNED]),
      mutate: () =>
        jsonResponse(
          { title: 'Unprocessable', detail: 'rcdoNode must be a Supporting Outcome' },
          422,
        ),
    });
    renderWithStore(<LockedView plan={PLAN} />);

    await waitFor(
      () => expect(screen.getByText('Add unplanned commitment')).toBeInTheDocument(),
      { timeout: 3000 },
    );

    fireEvent.click(screen.getByText('Add unplanned commitment'));
    fireEvent.change(screen.getByLabelText('Unplanned commitment title'), {
      target: { value: 'Bad link' },
    });
    fireEvent.click(screen.getByText('Pick Supporting Outcome'));
    // The picker renders the leaf as a selectable button; the frozen row renders
    // the same outcome title as plain text. Target the button to disambiguate.
    const pickerLeaf = await screen.findByRole(
      'button',
      { name: /Ship usage-based billing/ },
      { timeout: 3000 },
    );
    fireEvent.click(pickerLeaf);
    fireEvent.click(screen.getByRole('button', { name: 'Add unplanned commitment' }));

    await waitFor(
      () =>
        expect(
          screen.getByText('rcdoNode must be a Supporting Outcome'),
        ).toBeInTheDocument(),
      { timeout: 3000 },
    );
    // The failing POST did fire (the form stayed open with the error).
    expect(createCalls(fetchMock).length).toBeGreaterThan(0);
  });
});
