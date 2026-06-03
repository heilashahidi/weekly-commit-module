import { screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { setTokenProvider } from '../auth/tokenProvider';
import SummaryView from '../routes/myweek/SummaryView';
import {
  type CommitmentDto,
  type PlanMetricsDto,
  type RcdoNode,
  type WeeklyPlanDto,
} from '../store/api';
import { jsonResponse, renderWithStore } from '../test/renderWithStore';

const PLAN: WeeklyPlanDto = {
  id: 'plan-1',
  owner: 'auth0|ic',
  weekKey: '2026-W23',
  status: 'RECONCILED',
  lockType: 'USER_LOCKED',
  noPlan: false,
  statusDeadline: null,
  commitmentCount: 3,
};

const OUTCOME: RcdoNode = {
  id: 'so-1',
  nodeType: 'SUPPORTING_OUTCOME',
  title: 'Ship usage-based billing',
  description: null,
  parentId: 'oc-1',
  children: [],
};

const DONE_PLANNED: CommitmentDto = {
  id: 'c-1',
  weeklyPlanId: 'plan-1',
  rcdoNodeId: 'so-1',
  title: 'Draft pricing tiers',
  planned: true,
  reconciliationStatus: 'DONE',
  reconciliationNote: null,
  carriedFromId: null,
  carryWeekCount: 0,
};

const UNRECONCILED_PLANNED: CommitmentDto = {
  id: 'c-2',
  weeklyPlanId: 'plan-1',
  rcdoNodeId: 'so-1',
  title: 'Migrate invoice ledger',
  planned: true,
  reconciliationStatus: 'UNRECONCILED',
  reconciliationNote: null,
  carriedFromId: null,
  carryWeekCount: 0,
};

const PARTIAL_UNPLANNED: CommitmentDto = {
  id: 'c-3',
  weeklyPlanId: 'plan-1',
  rcdoNodeId: 'so-1',
  title: 'Hotfix billing rounding bug',
  planned: false,
  reconciliationStatus: 'PARTIAL',
  reconciliationNote: null,
  carriedFromId: null,
  carryWeekCount: 0,
};

const METRICS: PlanMetricsDto = {
  planId: 'plan-1',
  plannedCount: 2,
  unplannedCount: 1,
  doneCount: 1,
  reconciliationAccuracy: 0.75,
  plannedVsUnplannedRatio: 2,
};

interface RouteMap {
  commitments?: () => Response;
  metrics?: () => Response;
}

/**
 * Branches the fetch stub on URL + method so a single render serves the
 * commitments list GET, the metrics GET, and the per-row RCDO node resolve that
 * CommitmentRow's outcome spine needs. Returns the OUTCOME body for node
 * resolves so the spine renders its title (we query by role/testid to avoid
 * colliding with that plain text — disambiguation lesson).
 */
function stubRoutes(routes: RouteMap) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const req = input instanceof Request ? input : new Request(input, init);
    const url = req.url;
    const method = req.method;

    if (url.includes('/api/rcdo/nodes/')) return jsonResponse(OUTCOME);
    if (url.includes('/metrics') && method === 'GET') {
      return routes.metrics ? routes.metrics() : jsonResponse(METRICS);
    }
    if (url.includes('/commitments') && method === 'GET') {
      return routes.commitments ? routes.commitments() : jsonResponse([]);
    }
    return jsonResponse({});
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

describe('SummaryView', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    setTokenProvider(async () => null);
  });

  it('shows accuracy + ratio and displays an UNRECONCILED commitment honestly, not as done (AE-D10)', async () => {
    stubRoutes({
      commitments: () =>
        jsonResponse([DONE_PLANNED, UNRECONCILED_PLANNED, PARTIAL_UNPLANNED]),
      metrics: () => jsonResponse(METRICS),
    });
    renderWithStore(<SummaryView plan={PLAN} />);

    // Metrics surface.
    await waitFor(
      () =>
        expect(screen.getByTestId('reconciliation-accuracy')).toHaveTextContent('75%'),
      { timeout: 3000 },
    );
    expect(screen.getByTestId('planned-vs-unplanned-ratio')).toHaveTextContent('2.00');

    // Every commitment renders with its final status badge.
    await waitFor(
      () => expect(screen.getByText('Migrate invoice ledger')).toBeInTheDocument(),
      { timeout: 3000 },
    );

    // The UNRECONCILED commitment reads as unreconciled, distinct from DONE.
    expect(screen.getByText('Unreconciled')).toBeInTheDocument();
    expect(screen.getByText('Done')).toBeInTheDocument();
    // It is NOT shown as done: there is exactly one "Done" badge (the done one).
    expect(screen.getAllByText('Done')).toHaveLength(1);
    expect(screen.getByText('Partial')).toBeInTheDocument();
  });

  it('renders null reconciliationAccuracy (zero planned) as "not applicable", not 0%', async () => {
    const nullMetrics: PlanMetricsDto = {
      planId: 'plan-1',
      plannedCount: 0,
      unplannedCount: 1,
      doneCount: 0,
      reconciliationAccuracy: null,
      plannedVsUnplannedRatio: 0,
    };
    stubRoutes({
      commitments: () => jsonResponse([PARTIAL_UNPLANNED]),
      metrics: () => jsonResponse(nullMetrics),
    });
    renderWithStore(<SummaryView plan={PLAN} />);

    const accuracy = await screen.findByTestId('reconciliation-accuracy', undefined, {
      timeout: 3000,
    });
    expect(accuracy).toHaveTextContent(/not applicable/i);
    expect(accuracy).not.toHaveTextContent('0%');
  });

  it('renders a non-null accuracy as a percentage (0.75 → 75%)', async () => {
    stubRoutes({
      commitments: () => jsonResponse([DONE_PLANNED]),
      metrics: () => jsonResponse(METRICS),
    });
    renderWithStore(<SummaryView plan={PLAN} />);

    const accuracy = await screen.findByTestId('reconciliation-accuracy', undefined, {
      timeout: 3000,
    });
    expect(accuracy).toHaveTextContent('75%');
  });

  it('makes planned vs unplanned distinguishable (unplanned badge on unplanned rows only)', async () => {
    stubRoutes({
      commitments: () => jsonResponse([DONE_PLANNED, PARTIAL_UNPLANNED]),
      metrics: () => jsonResponse(METRICS),
    });
    renderWithStore(<SummaryView plan={PLAN} />);

    await waitFor(
      () => expect(screen.getByText('Draft pricing tiers')).toBeInTheDocument(),
      { timeout: 3000 },
    );

    // Exactly one unplanned badge — the unplanned commitment.
    expect(screen.getAllByText('unplanned')).toHaveLength(1);

    // The unplanned badge sits within the unplanned commitment's summary row.
    const rows = screen.getAllByTestId('summary-commitment');
    expect(rows).toHaveLength(2);
    const unplannedRow = rows.find((r) => within(r).queryByText('Hotfix billing rounding bug'));
    expect(unplannedRow).toBeDefined();
    expect(within(unplannedRow!).getByText('unplanned')).toBeInTheDocument();
    const plannedRow = rows.find((r) => within(r).queryByText('Draft pricing tiers'));
    expect(within(plannedRow!).queryByText('unplanned')).toBeNull();
  });

  it('shows the raw counts as context', async () => {
    stubRoutes({
      commitments: () => jsonResponse([DONE_PLANNED, UNRECONCILED_PLANNED, PARTIAL_UNPLANNED]),
      metrics: () => jsonResponse(METRICS),
    });
    renderWithStore(<SummaryView plan={PLAN} />);

    await waitFor(
      () => expect(screen.getByTestId('planned-count')).toHaveTextContent('2 planned'),
      { timeout: 3000 },
    );
    expect(screen.getByTestId('unplanned-count')).toHaveTextContent('1 unplanned');
    expect(screen.getByTestId('done-count')).toHaveTextContent('1 done');
  });

  it('exposes the carry-forward entry point (seam for U9)', async () => {
    stubRoutes({
      commitments: () => jsonResponse([DONE_PLANNED]),
      metrics: () => jsonResponse(METRICS),
    });
    renderWithStore(<SummaryView plan={PLAN} />);

    await waitFor(
      () => expect(screen.getByLabelText('Carry forward')).toBeInTheDocument(),
      { timeout: 3000 },
    );
  });

  it('shows a loading state before data resolves', () => {
    // Never-resolving fetch keeps both queries pending.
    const fetchMock = vi.fn(() => new Promise<Response>(() => {}));
    vi.stubGlobal('fetch', fetchMock);
    renderWithStore(<SummaryView plan={PLAN} />);

    expect(screen.getByText('Loading summary…')).toBeInTheDocument();
  });

  it('surfaces a ProblemDetail message when the metrics fetch fails', async () => {
    stubRoutes({
      commitments: () => jsonResponse([DONE_PLANNED]),
      metrics: () => jsonResponse({ title: 'Server error', detail: 'metrics unavailable' }, 500),
    });
    renderWithStore(<SummaryView plan={PLAN} />);

    await waitFor(
      () => expect(screen.getByText('metrics unavailable')).toBeInTheDocument(),
      { timeout: 3000 },
    );
    expect(screen.getByRole('alert')).toBeInTheDocument();
  });

  it('surfaces a ProblemDetail message when the commitments fetch fails', async () => {
    stubRoutes({
      commitments: () =>
        jsonResponse({ title: 'Server error', detail: 'commitments unavailable' }, 500),
      metrics: () => jsonResponse(METRICS),
    });
    renderWithStore(<SummaryView plan={PLAN} />);

    await waitFor(
      () => expect(screen.getByText('commitments unavailable')).toBeInTheDocument(),
      { timeout: 3000 },
    );
  });

  it('shows an empty state when there are no commitments', async () => {
    stubRoutes({
      commitments: () => jsonResponse([]),
      metrics: () =>
        jsonResponse({
          planId: 'plan-1',
          plannedCount: 0,
          unplannedCount: 0,
          doneCount: 0,
          reconciliationAccuracy: null,
          plannedVsUnplannedRatio: 0,
        }),
    });
    renderWithStore(<SummaryView plan={PLAN} />);

    await waitFor(
      () => expect(screen.getByText('No commitments this week.')).toBeInTheDocument(),
      { timeout: 3000 },
    );
  });
});
