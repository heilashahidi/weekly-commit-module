import type { PlanStatus, ReconciliationStatus } from '../store/api';
import { statusStyle } from '../lib/statusStyles';

interface StatusBadgeProps {
  status: PlanStatus | ReconciliationStatus;
}

/**
 * Renders a small pill for any lifecycle status using the shared KTD 6 token map
 * ({@link statusStyle}). Reused across the shell, commitment rows, and the
 * reconcile/summary views so state reads consistently (UX-R3).
 */
export default function StatusBadge({ status }: StatusBadgeProps) {
  const { label, badgeClass } = statusStyle(status);
  return (
    <span
      className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium uppercase tracking-wide ${badgeClass}`}
    >
      {label}
    </span>
  );
}
