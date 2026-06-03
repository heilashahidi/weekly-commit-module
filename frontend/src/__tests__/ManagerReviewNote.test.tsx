import { screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { setTokenProvider } from '../auth/tokenProvider';
import ManagerReviewNote from '../components/ManagerReviewNote';
import { type ManagerReviewDto } from '../store/api';
import { jsonResponse, renderWithStore } from '../test/renderWithStore';

const PLAN_ID = 'plan-1';

const REVIEW: ManagerReviewDto = {
  id: 'rev-1',
  weeklyPlanId: PLAN_ID,
  reviewer: 'Dana Manager',
  comment: 'Solid week — tighten the billing scope next time.',
  reviewedAt: '2026-06-01T15:30:00Z',
};

function stubJson(body: unknown, status = 200) {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) =>
      jsonResponse(body, status),
    ),
  );
}

describe('ManagerReviewNote', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    setTokenProvider(async () => null);
  });

  it('displays the comment + reviewer read-only when a review exists (AE-D9)', async () => {
    stubJson(REVIEW);
    const { container } = renderWithStore(<ManagerReviewNote planId={PLAN_ID} />);

    await waitFor(() => expect(screen.getByText(REVIEW.comment)).toBeInTheDocument(), {
      timeout: 3000,
    });
    expect(screen.getByText(/Dana Manager/)).toBeInTheDocument();

    // Read-only: no edit/write affordance in the rendered output.
    expect(container.querySelector('button')).toBeNull();
    expect(container.querySelector('textarea')).toBeNull();
    expect(container.querySelector('input')).toBeNull();
    expect(screen.queryByRole('button')).toBeNull();
    expect(screen.queryByRole('textbox')).toBeNull();
  });

  it('renders an absolute local timestamp for a valid reviewedAt', async () => {
    stubJson(REVIEW);
    renderWithStore(<ManagerReviewNote planId={PLAN_ID} />);

    await waitFor(() => expect(screen.getByText(REVIEW.comment)).toBeInTheDocument(), {
      timeout: 3000,
    });
    const expected = new Date(REVIEW.reviewedAt).toLocaleString();
    expect(screen.getByText(new RegExp(expected.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')))).toBeInTheDocument();
  });

  it('renders nothing when the GET returns 204 / no review (no error)', async () => {
    // 204 has no JSON body; api.ts responseHandler resolves data: null.
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response(null, { status: 204 })),
    );
    const { container } = renderWithStore(<ManagerReviewNote planId={PLAN_ID} />);

    // Loading clears, then the component renders nothing without throwing.
    await waitFor(
      () => expect(screen.queryByText(/Loading manager review/)).toBeNull(),
      { timeout: 3000 },
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('shows a loading state while the query is pending', () => {
    stubJson(REVIEW);
    renderWithStore(<ManagerReviewNote planId={PLAN_ID} />);
    expect(screen.getByText(/Loading manager review/)).toBeInTheDocument();
  });
});
