import type { WeeklyPlanDto } from '../../store/api';

interface ModeViewProps {
  plan: WeeklyPlanDto;
}

/**
 * RECONCILED mode sub-view (stub). U8 fleshes this out into the
 * planned-vs-actual summary + two-axis metrics surface; for now it names the
 * mode so the adaptive shell + lazy boundary are wired and testable end-to-end.
 */
export default function SummaryView({ plan }: ModeViewProps) {
  return (
    <section aria-label="Summary view">
      <h2 className="text-lg font-medium text-gray-700">Reconciled — week summary</h2>
      <p className="text-sm text-gray-500">Week {plan.weekKey}</p>
    </section>
  );
}
