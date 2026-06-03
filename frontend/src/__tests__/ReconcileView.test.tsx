import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi, type Mock } from 'vitest';
import { setTokenProvider } from '../auth/tokenProvider';
import ReconcileView from '../routes/myweek/ReconcileView';
import { type CommitmentDto, type RcdoNode, type WeeklyPlanDto } from '../store/api';
import { jsonResponse, renderWithStore } from '../test/renderWithStore';

const PLAN: WeeklyPlanDto = {
  id: 'plan-1',
  owner: 'auth0|ic',
  weekKey: '2026-W23',
  status: 'RECONCILING',
  lockType: 'USER_LOCKED',
  noPlan: false,
  statusDeadline: '2026-06-08T17:00:00Z',
  commitmentCount: 2,
};

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

interface RouteMap {
  /** GET /commitments list (may change across refetches). */
  commitments?: () => Response;
  /** Response for PUT set-status / POST submit-reconciled. */
  mutate?: (req: Request) => Response;
}

/**
 * Branches the fetch stub on URL + method so a single render serves the
 * commitments list, the per-row RCDO node resolve (the row shows the outcome
 * title as plain text), and the mutation under test.
 */
function stubRoutes(routes: RouteMap) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const req = input instanceof Request ? input : new Request(input, init);
    const url = req.url;
    const method = req.method;

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

function setStatusCalls(mock: Mock) {
  return mock.mock.calls.filter((c) => {
    const req = c[0] as Request;
    return req.url.includes('/status') && req.method === 'PUT';
  });
}

function submitCalls(mock: Mock) {
  return mock.mock.calls.filter((c) => {
    const req = c[0] as Request;
    return req.url.includes('/transitions/submit-reconciled') && req.method === 'POST';
  });
}

describe('ReconcileView', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    setTokenProvider(async () => null);
  });

  it('offers exactly DONE/PARTIAL/NOT_DONE/DROPPED and never UNRECONCILED (UX-R9)', async () => {
    stubRoutes({ commitments: () => jsonResponse([PLANNED]) });
    renderWithStore(<ReconcileView plan={PLAN} />);

    const select = await screen.findByRole(
      'combobox',
      { name: 'Status for Draft pricing tiers' },
      { timeout: 3000 },
    );

    const optionLabels = within(select)
      .getAllByRole('option')
      .map((o) => o.textContent);
    // The placeholder plus exactly the four IC-settable values.
    expect(optionLabels).toEqual(['Set status…', 'Done', 'Partial', 'Not done', 'Dropped']);
    expect(optionLabels).not.toContain('Unreconciled');
    // The enabled (selectable) option values never include UNRECONCILED.
    const values = within(select)
      .getAllByRole('option')
      .map((o) => (o as HTMLOptionElement).value);
    expect(values).not.toContain('UNRECONCILED');
  });

  it('setting a status issues setCommitmentStatus (PUT) with {status, note} including a note', async () => {
    const fetchMock = stubRoutes({
      commitments: () => jsonResponse([PLANNED]),
      mutate: () => jsonResponse({ ...PLANNED, reconciliationStatus: 'DONE' }),
    });
    renderWithStore(<ReconcileView plan={PLAN} />);

    const noteInput = await screen.findByRole(
      'textbox',
      { name: 'Note for Draft pricing tiers' },
      { timeout: 3000 },
    );
    fireEvent.change(noteInput, { target: { value: 'Shipped behind a flag' } });

    const select = screen.getByRole('combobox', { name: 'Status for Draft pricing tiers' });
    fireEvent.change(select, { target: { value: 'DONE' } });

    await waitFor(() => expect(setStatusCalls(fetchMock).length).toBeGreaterThan(0), {
      timeout: 3000,
    });
    const req = setStatusCalls(fetchMock)[0][0] as Request;
    expect(req.url).toContain('/api/lifecycle/commitments/c-1/status');
    const body = JSON.parse(await req.text());
    expect(body).toEqual({ status: 'DONE', note: 'Shipped behind a flag' });
  });

  it('sends note as null when the note field is empty (note optional)', async () => {
    const fetchMock = stubRoutes({
      commitments: () => jsonResponse([PLANNED]),
      mutate: () => jsonResponse({ ...PLANNED, reconciliationStatus: 'PARTIAL' }),
    });
    renderWithStore(<ReconcileView plan={PLAN} />);

    const select = await screen.findByRole(
      'combobox',
      { name: 'Status for Draft pricing tiers' },
      { timeout: 3000 },
    );
    fireEvent.change(select, { target: { value: 'PARTIAL' } });

    await waitFor(() => expect(setStatusCalls(fetchMock).length).toBeGreaterThan(0), {
      timeout: 3000,
    });
    const req = setStatusCalls(fetchMock)[0][0] as Request;
    const body = JSON.parse(await req.text());
    expect(body).toEqual({ status: 'PARTIAL', note: null });
  });

  it('with one commitment unstatused, Submit is disabled and shows the remaining count (AE-D6)', async () => {
    // One statused (planned, DONE) + one unstatused (unplanned).
    const statusedPlanned: CommitmentDto = { ...PLANNED, reconciliationStatus: 'DONE' };
    stubRoutes({ commitments: () => jsonResponse([statusedPlanned, UNPLANNED]) });
    renderWithStore(<ReconcileView plan={PLAN} />);

    await waitFor(
      () => expect(screen.getByText('Hotfix billing rounding bug')).toBeInTheDocument(),
      { timeout: 3000 },
    );

    const submitBtn = screen.getByRole('button', { name: 'Submit reconciliation' });
    expect(submitBtn).toBeDisabled();
    expect(screen.getByText(/1 remaining/)).toBeInTheDocument();
  });

  it('with all commitments statused, Submit is enabled and issues submitReconciled (POST) (AE-D7)', async () => {
    const statusedPlanned: CommitmentDto = { ...PLANNED, reconciliationStatus: 'DONE' };
    const statusedUnplanned: CommitmentDto = { ...UNPLANNED, reconciliationStatus: 'NOT_DONE' };
    const fetchMock = stubRoutes({
      commitments: () => jsonResponse([statusedPlanned, statusedUnplanned]),
      mutate: () => jsonResponse({ ...PLAN, status: 'RECONCILED' }),
    });
    renderWithStore(<ReconcileView plan={PLAN} />);

    const submitBtn = await screen.findByRole(
      'button',
      { name: 'Submit reconciliation' },
      { timeout: 3000 },
    );
    await waitFor(() => expect(submitBtn).not.toBeDisabled(), { timeout: 3000 });
    expect(screen.getByText(/All commitments statused/)).toBeInTheDocument();

    fireEvent.click(submitBtn);

    await waitFor(() => expect(submitCalls(fetchMock).length).toBeGreaterThan(0), {
      timeout: 3000,
    });
    const req = submitCalls(fetchMock)[0][0] as Request;
    expect(req.url).toContain('/api/lifecycle/plans/plan-1/transitions/submit-reconciled');
  });

  it('opens the submit gate after setting the last status → refetch (integration)', async () => {
    let listCalls = 0;
    const fetchMock = stubRoutes({
      commitments: () => {
        listCalls += 1;
        // First load: unplanned still unstatused → gate closed. After the PUT
        // invalidates the Commitment tag, the refetched list shows both statused.
        const unplanned =
          listCalls <= 1 ? UNPLANNED : { ...UNPLANNED, reconciliationStatus: 'DONE' as const };
        return jsonResponse([{ ...PLANNED, reconciliationStatus: 'DONE' as const }, unplanned]);
      },
      mutate: () => jsonResponse({ ...UNPLANNED, reconciliationStatus: 'DONE' }),
    });
    renderWithStore(<ReconcileView plan={PLAN} />);

    // Initially gated: one remaining, submit disabled.
    await waitFor(() => expect(screen.getByText(/1 remaining/)).toBeInTheDocument(), {
      timeout: 3000,
    });
    expect(screen.getByRole('button', { name: 'Submit reconciliation' })).toBeDisabled();

    // Status the last commitment.
    const select = screen.getByRole('combobox', {
      name: 'Status for Hotfix billing rounding bug',
    });
    fireEvent.change(select, { target: { value: 'DONE' } });

    await waitFor(() => expect(setStatusCalls(fetchMock).length).toBeGreaterThan(0), {
      timeout: 3000,
    });

    // After refetch the gate derives open from the fresh query data.
    await waitFor(
      () =>
        expect(
          screen.getByRole('button', { name: 'Submit reconciliation' }),
        ).not.toBeDisabled(),
      { timeout: 3000 },
    );
    expect(screen.getByText(/All commitments statused/)).toBeInTheDocument();
  });

  it('surfaces the ProblemDetail reason on a 422 submit', async () => {
    const statusedPlanned: CommitmentDto = { ...PLANNED, reconciliationStatus: 'DONE' };
    const fetchMock = stubRoutes({
      commitments: () => jsonResponse([statusedPlanned]),
      mutate: () =>
        jsonResponse(
          { title: 'Unprocessable', detail: 'All commitments must be statused' },
          422,
        ),
    });
    renderWithStore(<ReconcileView plan={PLAN} />);

    const submitBtn = await screen.findByRole(
      'button',
      { name: 'Submit reconciliation' },
      { timeout: 3000 },
    );
    await waitFor(() => expect(submitBtn).not.toBeDisabled(), { timeout: 3000 });
    fireEvent.click(submitBtn);

    await waitFor(
      () =>
        expect(screen.getByText('All commitments must be statused')).toBeInTheDocument(),
      { timeout: 3000 },
    );
    expect(submitCalls(fetchMock).length).toBeGreaterThan(0);
  });

  it('shows an empty-state message when there are no commitments', async () => {
    stubRoutes({ commitments: () => jsonResponse([]) });
    renderWithStore(<ReconcileView plan={PLAN} />);

    await waitFor(
      () =>
        expect(
          screen.getByText('No commitments to reconcile this week.'),
        ).toBeInTheDocument(),
      { timeout: 3000 },
    );
    // Empty plan can't be submitted (nothing to reconcile).
    expect(screen.getByRole('button', { name: 'Submit reconciliation' })).toBeDisabled();
  });

  it('surfaces a ProblemDetail message when loading commitments fails', async () => {
    stubRoutes({
      commitments: () =>
        jsonResponse({ title: 'Server error', detail: 'Could not load commitments' }, 500),
    });
    renderWithStore(<ReconcileView plan={PLAN} />);

    await waitFor(
      () => expect(screen.getByText('Could not load commitments')).toBeInTheDocument(),
      { timeout: 3000 },
    );
  });
});
