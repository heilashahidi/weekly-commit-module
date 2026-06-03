/**
 * Extracts a human-readable message from an RTK Query error (KTD 5).
 *
 * Workstream C enables `spring.mvc.problemdetails`, so error responses carry an
 * RFC 7807 `application/problem+json` body of shape
 * `{ type, title, status, detail, ... }`. RTK Query surfaces that body on
 * `error.data` for a {@link import('@reduxjs/toolkit/query').FetchBaseQueryError}.
 *
 * Resolution order: ProblemDetail `detail` → `title` → a generic fallback. The
 * helper is pure and defensive: it tolerates non-object errors, errors with no
 * `.data`, a `.data` lacking `detail`/`title`, and network/parsing errors (where
 * `status` is a string like `'FETCH_ERROR'` and there is no ProblemDetail body).
 */
const GENERIC_FALLBACK = 'Something went wrong';

interface ProblemDetailBody {
  detail?: unknown;
  title?: unknown;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

export function problemDetailMessage(error: unknown): string {
  if (!isRecord(error) || !isRecord(error.data)) {
    return GENERIC_FALLBACK;
  }
  const body = error.data as ProblemDetailBody;
  if (typeof body.detail === 'string' && body.detail.length > 0) {
    return body.detail;
  }
  if (typeof body.title === 'string' && body.title.length > 0) {
    return body.title;
  }
  return GENERIC_FALLBACK;
}
