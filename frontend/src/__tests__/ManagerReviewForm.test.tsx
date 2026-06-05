import { fireEvent, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { setTokenProvider } from '../auth/tokenProvider';
import { type ManagerReviewDto } from '../store/api';
import ManagerReviewForm from '../components/ManagerReviewForm';
import { jsonResponse, renderWithStore } from '../test/renderWithStore';

const REVIEW: ManagerReviewDto = {
  id: 'r1',
  weeklyPlanId: 'plan-1',
  reviewer: 'auth0|manager-mary',
  comment: 'Great week',
  reviewedAt: '2026-06-03T12:00:00Z',
};

describe('ManagerReviewForm', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    setTokenProvider(async () => null);
  });

  it('disables submit until a non-empty comment is entered', () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (_i: RequestInfo | URL, _n?: RequestInit) => jsonResponse(REVIEW)),
    );
    renderWithStore(<ManagerReviewForm planId="plan-1" />);

    const button = screen.getByRole('button', { name: /save review/i });
    expect(button).toBeDisabled();

    fireEvent.change(screen.getByLabelText('Write a review'), { target: { value: '   ' } });
    expect(button).toBeDisabled(); // whitespace-only stays disabled

    fireEvent.change(screen.getByLabelText('Write a review'), { target: { value: 'Great week' } });
    expect(button).toBeEnabled();
  });

  it('PUTs the comment and shows a success confirmation', async () => {
    const fetchMock = vi.fn(async (_i: RequestInfo | URL, _n?: RequestInit) => jsonResponse(REVIEW));
    vi.stubGlobal('fetch', fetchMock);
    renderWithStore(<ManagerReviewForm planId="plan-1" />);

    fireEvent.change(screen.getByLabelText('Write a review'), { target: { value: 'Great week' } });
    fireEvent.click(screen.getByRole('button', { name: /save review/i }));

    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Review saved'), {
      timeout: 3000,
    });
    const putCall = fetchMock.mock.calls.find((c) => (c[0] as Request).method === 'PUT');
    expect(putCall).toBeDefined();
    expect((putCall![0] as Request).url).toContain('/api/lifecycle/plans/plan-1/review');
  });

  it('disables the button and shows Saving… while the write is in flight', async () => {
    let resolveFetch!: (r: Response) => void;
    const pending = new Promise<Response>((res) => {
      resolveFetch = res;
    });
    vi.stubGlobal(
      'fetch',
      vi.fn(async (_i: RequestInfo | URL, _n?: RequestInit) => pending),
    );
    renderWithStore(<ManagerReviewForm planId="plan-1" />);

    fireEvent.change(screen.getByLabelText('Write a review'), { target: { value: 'note' } });
    fireEvent.click(screen.getByRole('button', { name: /save review/i }));

    await waitFor(() => expect(screen.getByRole('button', { name: /saving/i })).toBeDisabled(), {
      timeout: 3000,
    });

    // Let the write complete so the success state shows and no promise dangles.
    resolveFetch(
      new Response(JSON.stringify(REVIEW), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Review saved'), {
      timeout: 3000,
    });
  });

  it('shows a problem-detail error on 403 and preserves the typed text', async () => {
    const fetchMock = vi.fn(
      async (_i: RequestInfo | URL, _n?: RequestInit) =>
        new Response(JSON.stringify({ detail: 'Only the plan owner’s manager may review' }), {
          status: 403,
          headers: { 'Content-Type': 'application/problem+json' },
        }),
    );
    vi.stubGlobal('fetch', fetchMock);
    renderWithStore(<ManagerReviewForm planId="plan-1" />);

    fireEvent.change(screen.getByLabelText('Write a review'), { target: { value: 'My note' } });
    fireEvent.click(screen.getByRole('button', { name: /save review/i }));

    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument(), { timeout: 3000 });
    expect(screen.getByText('Only the plan owner’s manager may review')).toBeInTheDocument();
    // Text is not wiped on failure.
    expect(screen.getByLabelText('Write a review')).toHaveValue('My note');
  });
});
