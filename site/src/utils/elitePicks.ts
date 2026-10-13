import type { ElitePick, Recommendation } from '../types';

/**
 * Elite Picks are selected on the server (core ElitePicksSelector) per matchday, so the boards,
 * fixture pages, shortlist and Results all show the same picks. These helpers only match those
 * picks back to the recommendations a page has loaded.
 */

/** Flatten a grouped recommendations map into a single list. */
export function flattenGroupedRecommendations(
  grouped: Record<string, Recommendation[]> | null | undefined
): Recommendation[] {
  if (!grouped) return [];
  return Object.values(grouped).flat();
}

export function elitePickKey(fixtureId: number, type: string): string {
  return `${fixtureId}:${type}`;
}

export function toEliteKeySet(picks: ReadonlyArray<Pick<ElitePick, 'fixtureId' | 'type'>>): Set<string> {
  return new Set(picks.map((pick) => elitePickKey(pick.fixtureId, pick.type)));
}

export interface EliteDay {
  date: string;
  picks: Recommendation[];
}

function londonDate(at: Date): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/London' }).format(at);
}

/** "Today", "Tomorrow", else e.g. "Thu 15 Oct", for a London matchday (yyyy-mm-dd). */
export function eliteDayLabel(date: string, now: Date = new Date()): string {
  if (date === londonDate(now)) return 'Today';
  if (date === londonDate(new Date(now.getTime() + 24 * 60 * 60 * 1000))) return 'Tomorrow';
  const [year, month, day] = date.split('-').map(Number);
  return new Date(Date.UTC(year, month - 1, day, 12)).toLocaleDateString('en-GB', {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    timeZone: 'UTC',
  });
}

/**
 * Elite picks that are present in `pool`, grouped by matchday in date then rank order. A pick
 * missing from the pool (filtered out on this page, or no longer published) is dropped rather
 * than back-filled, so the ranks shown always match the record Results grades.
 */
export function eliteDaysFromPool(picks: ElitePick[] | null | undefined, pool: Recommendation[]): EliteDay[] {
  if (!picks?.length) return [];
  const byKey = new Map(pool.map((rec) => [elitePickKey(rec.fixtureId, rec.type), rec]));
  const sorted = [...picks].sort((a, b) => a.date.localeCompare(b.date) || a.rank - b.rank);

  const days: EliteDay[] = [];
  for (const pick of sorted) {
    const rec = byKey.get(elitePickKey(pick.fixtureId, pick.type));
    if (!rec) continue;
    const last = days[days.length - 1];
    if (last && last.date === pick.date) {
      last.picks.push(rec);
    } else {
      days.push({ date: pick.date, picks: [rec] });
    }
  }
  return days;
}
