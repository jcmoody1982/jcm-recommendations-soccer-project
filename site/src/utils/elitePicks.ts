import type { Recommendation, RecommendationType } from '../types';

/** UC-036: probability / quality %-style types only. */
export const ELITE_ELIGIBLE_TYPES: readonly RecommendationType[] = [
  'MATCH_RESULT',
  'BTTS',
  'DOUBLE_CHANCE',
  'DRAW',
  'OVER_GOALS',
  'UNDER_GOALS',
  'CLEAN_SHEET',
  'RESULT_BTTS',
  'TOP_VS_BOTTOM',
  'FIRST_HALF_GOALS',
  'SECOND_HALF_GOALS',
  'VALUE_BET',
] as const;

export const ELITE_PICKS_LIMIT = 10;

/** Soft caps so one market cannot dominate the Elite board. */
export const ELITE_TYPE_CAPS: Partial<Record<RecommendationType, number>> = {
  BTTS: 3,
};

const ELITE_TYPE_SET = new Set<string>(ELITE_ELIGIBLE_TYPES);

export function isEliteEligibleType(type: string): boolean {
  return ELITE_TYPE_SET.has(type);
}

function compareEliteRank(a: Recommendation, b: Recommendation): number {
  const scoreDiff = (b.score || 0) - (a.score || 0);
  if (scoreDiff !== 0) return scoreDiff;

  const aOdds = a.odds != null && a.odds > 0 ? a.odds : Number.POSITIVE_INFINITY;
  const bOdds = b.odds != null && b.odds > 0 ? b.odds : Number.POSITIVE_INFINITY;
  if (aOdds !== bOdds) return aOdds - bOdds;

  return (a.matchDateUnix || 0) - (b.matchDateUnix || 0);
}

/**
 * Rank Strong %-style picks into Elite Picks (UC-036).
 * At most one selection per fixture; default cap 10; BTTS capped at 3.
 */
export function selectElitePicks(
  recommendations: Recommendation[],
  limit: number = ELITE_PICKS_LIMIT
): Recommendation[] {
  const pool = recommendations.filter(
    (rec) =>
      rec.confidence?.toUpperCase() === 'STRONG'
      && isEliteEligibleType(rec.type)
  );

  pool.sort(compareEliteRank);

  const seenFixtures = new Set<number>();
  const typeCounts = new Map<string, number>();
  const elite: Recommendation[] = [];

  for (const rec of pool) {
    if (seenFixtures.has(rec.fixtureId)) continue;

    const typeCap = ELITE_TYPE_CAPS[rec.type];
    const typeCount = typeCounts.get(rec.type) ?? 0;
    if (typeCap != null && typeCount >= typeCap) continue;

    seenFixtures.add(rec.fixtureId);
    typeCounts.set(rec.type, typeCount + 1);
    elite.push(rec);
    if (elite.length >= limit) break;
  }

  return elite;
}

/** Flatten a grouped recommendations map into a single list. */
export function flattenGroupedRecommendations(
  grouped: Record<string, Recommendation[]> | null | undefined
): Recommendation[] {
  if (!grouped) return [];
  return Object.values(grouped).flat();
}
