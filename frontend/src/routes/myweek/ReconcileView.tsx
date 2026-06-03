import type { WeeklyPlanDto } from '../../store/api';

interface ModeViewProps {
  plan: WeeklyPlanDto;
}

/**
 * RECONCILING mode sub-view (stub). U7 fleshes this out into the per-commitment
 * status + gated-submit surface; for now it names the mode so the adaptive
 * shell + lazy boundary are wired and testable end-to-end.
 */
export default function ReconcileView({ plan }: ModeViewProps) {
  return (
    <section aria-label="Reconcile view">
      <h2 className="text-lg font-medium text-gray-700">Reconciling — how did the week go?</h2>
      <p className="text-sm text-gray-500">Week {plan.weekKey}</p>
    </section>
  );
}
