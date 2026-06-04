import { fireEvent, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { setTokenProvider } from '../auth/tokenProvider';
import {
  type CommitmentDto,
  type PlanMetricsDto,
  type RcdoNode,
  type WeeklyPlanDto,
} from '../store/api';
import ReportPlanDetail from '../routes/manager/ReportPlanDetail';
import { jsonResponse, renderWithStore } from '../test/renderWithStore';

const PLAN_ID = 'plan-1';

const NODE: RcdoNode = {
  id: 'node-1',
  nodeType: 'SUPPORTING_OUTCOME',
  title: 'Ship usage-based billing',
  description: null,
  parentId: null,
  children: [],
};

const COMMITMENT: CommitmentDto = {
  id: 'c1',
  weeklyPlanId: PLAN_ID,
  rcdoNodeId: 'node-1',
  title: 'Draft the pricing page',
  planned: true,
  reconciliationStatus: null,
  reconciliationNote: null,
  carriedFromId: null,
  carryWeekCount: 0,
};

const METRICS: PlanMetricsDto = {
  planId: PLAN_ID,
  plannedCount: 1,
  unplannedCount: 0,
  doneCount: 1,
  reconciliationAccuracy: 1,
  plannedVsUnplannedRatio: 0,
};

function plan(status: WeeklyPlanDto['status']): WeeklyPlanDto {
  return {
    id: PLAN_ID,
    owner: 'auth0|report-ava',
    weekKey: '2026-W23',
    status,
    lockType: status === 'DRAFT' ? null : 'USER_LOCKED',
    noPlan: false,
    statusDeadline: null,
    commitmentCount: 1,
  };
}

function stubRoutes(opts: {
  plan: WeeklyPlanDto;
  commitments?: CommitmentDto[];
  metrics?: PlanMetricsDto;
  review?: unknown;
}) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, _init?: RequestInit) => {
    const url = (input as Request).url;
    if (url.includes('/review')) {
      return opts.review ? jsonResponse(opts.review) : new Response(null, { status: 204 });
    }
    if (url.includes('/metrics')) {
      return jsonResponse(opts.metrics ?? METRICS);
    }
    if (url.includes('/commitments')) {
      return jsonResponse(opts.commitments ?? []);
    }
    if (url.includes('/api/rcdo/nodes/')) {
      return jsonResponse(NODE);
    }
    return jsonResponse(opts.plan);
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

describe('ReportPlanDetail', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    setTokenProvider(async () => null);
  });

  it('renders commitments read-only (no edit/delete) and a working Back control', async () => {
    stubRoutes({ plan: plan('LOCKED'), commitments: [COMMITMENT] });
    const onBack = vi.fn();
    renderWithStore(
      <ReportPlanDetail planId={PLAN_ID} displayName="Ava Stone" onBack={onBack} />,
    );

    await waitFor(() => expect(screen.getByText('Draft the pricing page')).toBeInTheDocument(), {
      timeout: 3000,
    });
    // Linked Outcome spine visible (title shares its <p> with the "Supporting Outcome:"
    // label, and resolves via a separate getRcdoNode fetch — wait for it).
    await waitFor(
      () => expect(screen.getByText(/Ship usage-based billing/)).toBeInTheDocument(),
      { timeout: 3000 },
    );
    // Read-only: no IC edit/delete controls.
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument();
    // Review form present (empty review = 204, no note rendered, no error).
    expect(screen.getByLabelText('Write a review')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();

    fireEvent.click(screen.getByText('← Back to team'));
    expect(onBack).toHaveBeenCalledTimes(1);
  });

  it('shows planned-vs-actual only once the plan is RECONCILED', async () => {
    stubRoutes({ plan: plan('RECONCILED'), commitments: [COMMITMENT], metrics: METRICS });
    renderWithStore(<ReportPlanDetail planId={PLAN_ID} displayName="Ava Stone" onBack={vi.fn()} />);

    await waitFor(() => expect(screen.getByLabelText('Planned vs actual')).toBeInTheDocument(), {
      timeout: 3000,
    });
  });

  it('omits planned-vs-actual for a DRAFT plan', async () => {
    stubRoutes({ plan: plan('DRAFT'), commitments: [COMMITMENT] });
    renderWithStore(<ReportPlanDetail planId={PLAN_ID} displayName="Ava Stone" onBack={vi.fn()} />);

    await waitFor(() => expect(screen.getByText('Draft the pricing page')).toBeInTheDocument(), {
      timeout: 3000,
    });
    expect(screen.queryByLabelText('Planned vs actual')).not.toBeInTheDocument();
  });
});
