import type { WeeklyPlanDto } from '../../store/api';

interface ModeViewProps {
  plan: WeeklyPlanDto;
}

/**
 * LOCKED mode sub-view (stub). U6 fleshes this out into the frozen-planned +
 * add-unplanned + review surface; for now it names the mode so the adaptive
 * shell + lazy boundary are wired and testable end-to-end.
 */
export default function LockedView({ plan }: ModeViewProps) {
  return (
    <section aria-label="Locked view">
      <h2 className="text-lg font-medium text-gray-700">Locked — week in progress</h2>
      <p className="text-sm text-gray-500">Week {plan.weekKey}</p>
    </section>
  );
}
