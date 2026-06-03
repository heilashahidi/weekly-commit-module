import { useGetManagerReviewQuery } from '../store/api';
import { formatTimestamp } from '../lib/formatTimestamp';

/**
 * Read-only display of a manager's review comment on a plan (UX-R13, AE-D9 display half).
 *
 * The review GET returns 204 No Content when no review exists; api.ts's custom
 * responseHandler resolves that as `data: null` (not an error), so absence is a
 * valid, silent state. We render nothing in the empty case — the safest default
 * for AE-D9, keeping the locked/summary views uncluttered when no review applies.
 *
 * Writing the review is workstream F; this component is deliberately read-only and
 * exposes no edit affordance.
 */
export default function ManagerReviewNote({ planId }: { planId: string }) {
  const { data, isLoading } = useGetManagerReviewQuery(planId);

  if (isLoading) {
    return <p className="text-sm text-gray-500">Loading manager review…</p>;
  }

  // No review yet (204 → null) — render nothing.
  if (!data) {
    return null;
  }

  return (
    <section aria-label="Manager review" className="rounded border border-gray-200 bg-gray-50 p-3">
      <p className="text-sm font-medium text-gray-700">Manager review</p>
      <p className="mt-1 text-sm text-gray-700">{data.comment}</p>
      <p className="mt-2 text-xs text-gray-500">
        — {data.reviewer},{' '}
        <time dateTime={data.reviewedAt}>{formatTimestamp(data.reviewedAt)}</time>
      </p>
    </section>
  );
}
