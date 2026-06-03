import { useGetRcdoNodeQuery } from '../store/api';

/**
 * Resolves and renders the linked RCDO Supporting Outcome title for any entity
 * that carries an `rcdoNodeId` (a commitment or a carry candidate). The DTOs only
 * carry the id, so we fetch the node via the existing `getRcdoNode` query — RTK
 * Query caches and de-dupes per id, so rows sharing an Outcome collapse to one
 * request. Pending → graceful placeholder; error/absent → fall back to the id
 * rather than blocking the row. This keeps the RCDO spine always visible (UX-R5)
 * without threading the node down from every caller. (Richer ancestry context
 * beyond the title is deferred — the parent id alone is a UUID, not human
 * context, so we show only the resolved title.)
 */
export default function LinkedOutcome({ rcdoNodeId }: { rcdoNodeId: string }) {
  const { data, isLoading } = useGetRcdoNodeQuery(rcdoNodeId);
  let title: string;
  if (data) {
    title = data.title;
  } else if (isLoading) {
    title = 'Loading…';
  } else {
    title = rcdoNodeId;
  }
  return (
    <p className="text-sm font-medium text-blue-700">
      <span className="text-xs uppercase tracking-wide text-gray-400">
        Supporting Outcome:{' '}
      </span>
      {title}
    </p>
  );
}
