import { screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { setTokenProvider } from '../auth/tokenProvider';
import MyWeek from '../routes/MyWeek';
import { type PlanStatus, type WeeklyPlanDto } from '../store/api';
import { jsonResponse, renderWithStore } from '../test/renderWithStore';

function makePlan(overrides: Partial<WeeklyPlanDto> = {}): WeeklyPlanDto {
  return {
    id: 'plan-1',
    owner: 'ic@example.com',
    weekKey: '2026-W23',
    status: 'DRAFT',
    lockType: null,
    noPlan: false,
    statusDeadline: null,
    commitmentCount: 0,
    ...overrides,
  };
}

/**
 * Stubs the current-plan GET with `body`/`status`. The shell lazy-loads the real
 * mode sub-views, which fire their own sub-resource queries — so those must be
 * branched to valid shapes (empty commitments/candidates, a metrics object, a
 * 204 no-review) or the sub-view throws and the shell assertion races the throw.
 */
function stubFetch(body: unknown, status = 200) {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = (input instanceof Request ? input : new Request(input, init)).url;
      if (url.includes('/commitments')) return jsonResponse([]);
      if (url.includes('/carry-candidates')) return jsonResponse([]);
      if (url.includes('/metrics')) {
        return jsonResponse({
          planId: 'plan-1',
          plannedCount: 0,
          unplannedCount: 0,
          doneCount: 0,
          reconciliationAccuracy: null,
          plannedVsUnplannedRatio: 0,
        });
      }
      if (url.includes('/review')) return new Response(null, { status: 204 });
      // getCurrentPlan (and any plan-by-id) — the configured response.
      return jsonResponse(body, status);
    }),
  );
}

describe('MyWeek', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    setTokenProvider(async () => null);
  });

  it('shows a loading state before the plan resolves', () => {
    stubFetch(makePlan());
    renderWithStore(<MyWeek />);
    expect(screen.getByText(/Loading your week/)).toBeInTheDocument();
  });

  it('surfaces a ProblemDetail detail message in a role=alert (AE-D11)', async () => {
    stubFetch(
      {
        type: 'about:blank',
        title: 'Conflict',
        detail: 'Plan is already locked for this week',
      },
      409,
    );
    renderWithStore(<MyWeek />);

    await waitFor(
      () => expect(screen.getByRole('alert')).toBeInTheDocument(),
      { timeout: 3000 },
    );
    expect(screen.getByRole('alert')).toHaveTextContent(
      'Plan is already locked for this week',
    );
  });

  const modeCases: Array<{ status: PlanStatus; heading: RegExp }> = [
    { status: 'DRAFT', heading: /Draft — plan your week/ },
    { status: 'LOCKED', heading: /Locked — week in progress/ },
    { status: 'RECONCILING', heading: /Reconciling — how did the week go/ },
    { status: 'RECONCILED', heading: /Reconciled — week summary/ },
  ];

  for (const { status, heading } of modeCases) {
    it(`resolves the ${status} sub-view behind the lazy boundary`, async () => {
      stubFetch(makePlan({ status }));
      renderWithStore(<MyWeek />);

      await waitFor(() => expect(screen.getByText(heading)).toBeInTheDocument(), {
        timeout: 3000,
      });
    });
  }

  it('renders the current status badge', async () => {
    stubFetch(makePlan({ status: 'LOCKED' }));
    renderWithStore(<MyWeek />);

    await waitFor(() => expect(screen.getByText('Locked')).toBeInTheDocument(), {
      timeout: 3000,
    });
  });

  it('displays the deadline when statusDeadline is set', async () => {
    const deadline = '2026-06-10T17:00:00Z';
    stubFetch(makePlan({ status: 'LOCKED', statusDeadline: deadline }));
    renderWithStore(<MyWeek />);

    await waitFor(() => expect(screen.getByText(/Deadline:/)).toBeInTheDocument(), {
      timeout: 3000,
    });
  });

  it('shows no deadline when statusDeadline is null', async () => {
    stubFetch(makePlan({ status: 'DRAFT', statusDeadline: null }));
    renderWithStore(<MyWeek />);

    // Wait for the resolved data branch (status badge present), then assert
    // the deadline line is absent.
    await waitFor(() => expect(screen.getByText('Draft')).toBeInTheDocument(), {
      timeout: 3000,
    });
    expect(screen.queryByText(/Deadline:/)).not.toBeInTheDocument();
  });
});
