import { fireEvent, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi, type Mock } from 'vitest';
import { setTokenProvider } from '../auth/tokenProvider';
import CarryForwardPanel from '../routes/myweek/CarryForwardPanel';
import {
  type CarryCandidateDto,
  type RcdoNode,
  type WeeklyPlanDto,
} from '../store/api';
import { jsonResponse, renderWithStore } from '../test/renderWithStore';

const PLAN_ID = 'plan-1';

const OUTCOME: RcdoNode = {
  id: 'so-1',
  nodeType: 'SUPPORTING_OUTCOME',
  title: 'Ship usage-based billing',
  description: null,
  parentId: 'oc-1',
  children: [],
};

const PARTIAL: CarryCandidateDto = {
  id: 'c-1',
  title: 'Draft pricing tiers',
  rcdoNodeId: 'so-1',
  reconciliationStatus: 'PARTIAL',
  carryWeekCount: 2,
};

const NOT_DONE: CarryCandidateDto = {
  id: 'c-2',
  title: 'Migrate invoice ledger',
  rcdoNodeId: 'so-1',
  reconciliationStatus: 'NOT_DONE',
  carryWeekCount: 1,
};

const CARRIED_PLAN: WeeklyPlanDto = {
  id: 'plan-2',
  owner: 'auth0|ic',
  weekKey: '2026-W24',
  status: 'DRAFT',
  lockType: null,
  noPlan: false,
  statusDeadline: null,
  commitmentCount: 1,
};

interface RouteMap {
  /** GET carry-candidates list. */
  candidates?: () => Response;
  /** POST carry. */
  carry?: (req: Request) => Response;
}

/**
 * Branches the fetch stub on URL + method. Both endpoints contain "carry", so we
 * disambiguate the candidates GET (`/carry-candidates`) from the carry POST
 * (`/carry`) on the URL suffix + method. The RCDO node resolve serves the
 * OUTCOME title for each candidate's spine (queried by role/testid to avoid
 * colliding with that plain text).
 */
function stubRoutes(routes: RouteMap) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const req = input instanceof Request ? input : new Request(input, init);
    const url = req.url;
    const method = req.method;

    if (url.includes('/api/rcdo/nodes/')) return jsonResponse(OUTCOME);
    if (url.includes('/carry-candidates') && method === 'GET') {
      return routes.candidates ? routes.candidates() : jsonResponse([]);
    }
    if (url.endsWith('/carry') && method === 'POST') {
      return routes.carry ? routes.carry(req) : jsonResponse(CARRIED_PLAN);
    }
    return jsonResponse({});
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function carryCalls(mock: Mock) {
  return mock.mock.calls.filter((c) => {
    const req = c[0] as Request;
    return req.url.endsWith('/carry') && req.method === 'POST';
  });
}

describe('CarryForwardPanel', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    setTokenProvider(async () => null);
  });

  it('lists PARTIAL/NOT_DONE candidates with their week-count and carries exactly the selected ids (AE-D8)', async () => {
    const fetchMock = stubRoutes({
      candidates: () => jsonResponse([PARTIAL, NOT_DONE]),
      carry: () => jsonResponse(CARRIED_PLAN),
    });
    renderWithStore(<CarryForwardPanel planId={PLAN_ID} />);

    // Both returned candidates render with their week-count.
    await waitFor(
      () => expect(screen.getByText('Draft pricing tiers')).toBeInTheDocument(),
      { timeout: 3000 },
    );
    expect(screen.getByText('Migrate invoice ledger')).toBeInTheDocument();
    const counts = screen.getAllByTestId('carry-week-count');
    expect(counts.map((c) => c.textContent)).toEqual([
      'Carried 2 weeks',
      'Carried 1 week',
    ]);

    // Select only the first candidate.
    fireEvent.click(screen.getByRole('checkbox', { name: 'Carry Draft pricing tiers' }));

    const carryBtn = screen.getByRole('button', { name: 'Carry selected' });
    expect(carryBtn).not.toBeDisabled();
    fireEvent.click(carryBtn);

    await waitFor(() => expect(carryCalls(fetchMock).length).toBeGreaterThan(0), {
      timeout: 3000,
    });
    const postReq = carryCalls(fetchMock)[0][0] as Request;
    expect(postReq.url).toContain('/api/lifecycle/plans/plan-1/carry');
    const body = JSON.parse(await postReq.text());
    expect(body).toEqual({ commitmentIds: ['c-1'] });

    // Confirmation that the selected items seed next week's draft.
    await waitFor(
      () => expect(screen.getByTestId('carry-confirmation')).toBeInTheDocument(),
      { timeout: 3000 },
    );
  });

  it('carries exactly the full set when all candidates are selected', async () => {
    const fetchMock = stubRoutes({
      candidates: () => jsonResponse([PARTIAL, NOT_DONE]),
      carry: () => jsonResponse(CARRIED_PLAN),
    });
    renderWithStore(<CarryForwardPanel planId={PLAN_ID} />);

    await waitFor(
      () => expect(screen.getByText('Draft pricing tiers')).toBeInTheDocument(),
      { timeout: 3000 },
    );

    fireEvent.click(screen.getByRole('checkbox', { name: 'Carry Draft pricing tiers' }));
    fireEvent.click(screen.getByRole('checkbox', { name: 'Carry Migrate invoice ledger' }));
    fireEvent.click(screen.getByRole('button', { name: 'Carry selected' }));

    await waitFor(() => expect(carryCalls(fetchMock).length).toBeGreaterThan(0), {
      timeout: 3000,
    });
    const body = JSON.parse(await (carryCalls(fetchMock)[0][0] as Request).text());
    expect(body).toEqual({ commitmentIds: ['c-1', 'c-2'] });
  });

  it('disables the carry action when nothing is selected', async () => {
    const fetchMock = stubRoutes({ candidates: () => jsonResponse([PARTIAL, NOT_DONE]) });
    renderWithStore(<CarryForwardPanel planId={PLAN_ID} />);

    const carryBtn = await screen.findByRole('button', { name: 'Carry selected' }, {
      timeout: 3000,
    });
    expect(carryBtn).toBeDisabled();

    // Clicking the disabled button issues no carry POST.
    fireEvent.click(carryBtn);
    expect(carryCalls(fetchMock)).toHaveLength(0);
  });

  it('shows an empty state when there are no candidates', async () => {
    stubRoutes({ candidates: () => jsonResponse([]) });
    renderWithStore(<CarryForwardPanel planId={PLAN_ID} />);

    await waitFor(() => expect(screen.getByTestId('carry-empty')).toBeInTheDocument(), {
      timeout: 3000,
    });
    // No carry control in the empty state.
    expect(screen.queryByRole('button', { name: 'Carry selected' })).toBeNull();
  });

  it('surfaces the ProblemDetail message when carry fails', async () => {
    stubRoutes({
      candidates: () => jsonResponse([PARTIAL]),
      carry: () =>
        jsonResponse({ title: 'Conflict', detail: 'Next week is already locked' }, 409),
    });
    renderWithStore(<CarryForwardPanel planId={PLAN_ID} />);

    await waitFor(
      () => expect(screen.getByText('Draft pricing tiers')).toBeInTheDocument(),
      { timeout: 3000 },
    );

    fireEvent.click(screen.getByRole('checkbox', { name: 'Carry Draft pricing tiers' }));
    fireEvent.click(screen.getByRole('button', { name: 'Carry selected' }));

    await waitFor(
      () => expect(screen.getByText('Next week is already locked')).toBeInTheDocument(),
      { timeout: 3000 },
    );
    expect(screen.getByRole('alert')).toHaveTextContent('Next week is already locked');
  });

  it('surfaces the ProblemDetail message when loading candidates fails', async () => {
    stubRoutes({
      candidates: () =>
        jsonResponse({ title: 'Server error', detail: 'Could not load candidates' }, 500),
    });
    renderWithStore(<CarryForwardPanel planId={PLAN_ID} />);

    await waitFor(
      () => expect(screen.getByRole('alert')).toHaveTextContent('Could not load candidates'),
      { timeout: 3000 },
    );
  });
});
