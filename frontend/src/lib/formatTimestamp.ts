/**
 * Formats an ISO-8601 timestamp string as an absolute, readable local time.
 *
 * Shared across the deadline displays (My Week shell, Locked view) and the
 * manager-review note so the parse-with-fallback rule lives in one place. A
 * ticking/relative countdown is deferred — this is the fixed-timestamp form.
 * Falls back to the raw string if the value doesn't parse (defensive against a
 * malformed Instant from the server).
 */
export function formatTimestamp(iso: string): string {
  const parsed = new Date(iso);
  return Number.isNaN(parsed.getTime()) ? iso : parsed.toLocaleString();
}
