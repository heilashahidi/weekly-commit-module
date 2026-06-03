import type { WeeklyPlanDto } from '../../store/api';

interface ModeViewProps {
  plan: WeeklyPlanDto;
}

/**
 * DRAFT mode sub-view (stub). U5 fleshes this out into the add/edit/delete +
 * lock surface; for now it names the mode so the adaptive shell + lazy boundary
 * are wired and testable end-to-end.
 */
export default function DraftView({ plan }: ModeViewProps) {
  return (
    <section aria-label="Draft view">
      <h2 className="text-lg font-medium text-gray-700">Draft — plan your week</h2>
      <p className="text-sm text-gray-500">Week {plan.weekKey}</p>
    </section>
  );
}
