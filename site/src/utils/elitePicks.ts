import type { Recommendation, RecommendationType } from '../types';

/** UC-036: probability / quality %-style types only. */
export const ELITE_ELIGIBLE_TYPES: readonly RecommendationType[] = [
  'MATCH_RESULT',
  'BTTS',
  'DOUBLE_CHANCE',
  'DRAW',
  'OVER_GOALS',
  'OVER_15_GOALS',
  'OVER_25_GOALS',
  'UNDER_GOALS',
  'RESULT_BTTS',
  'TOP_VS_BOTTOM',
  'FIRST_HALF_GOALS',
  'SECOND_HALF_GOALS',
  'VALUE_BET',
] as const;

export const ELITE_PICKS_LIMIT = 10;

/**
 * Most slots any single market family may take. Elite ranks by probability, which structurally
 * favours short-priced markets: Over 1.5 clears in roughly three quarters of fixtures, so on
 * probability alone it outranks almost everything and fills the board on its own.
 *
 * The cap counts families rather than types because capping types did not work. Three separate
 * types emit an "Over 1.5" market — full match, first half and second half — so a cap of three per
 * type still let nine of the ten slots read "Over 1.5 something". Those bets all fire on the same
 * underlying read, that the game will have goals in it, so they share one budget.
 */
export const ELITE_MAX_PER_FAMILY = 3;

const ELITE_TYPE_SET = new Set<string>(ELITE_ELIGIBLE_TYPES);

/**
 * Types that are really one bet in different clothing. Grouping is by type rather than by parsing
 * the market string, which varies per engine and would silently regroup on any wording change.
 */
const TYPE_FAMILIES: Record<string, string> = {
  OVER_GOALS: 'GOALS_OVER',
  OVER_15_GOALS: 'GOALS_OVER',
  OVER_25_GOALS: 'GOALS_OVER',
  FIRST_HALF_GOALS: 'GOALS_OVER',
  SECOND_HALF_GOALS: 'GOALS_OVER',
};

export function isEliteEligibleType(type: string): boolean {
  return ELITE_TYPE_SET.has(type);
}

/** The shared budget key for a type, or the type itself when it stands alone. */
export function marketFamily(type: string | null | undefined): string {
  if (type == null) return '';
  const upper = type.toUpperCase();
  return TYPE_FAMILIES[upper] ?? upper;
}

/** Temporary: Over 0.5 half-goals crowd Elite until scoring is tuned. Over 1.5 remains eligible. */
export function isExcludedFromElitePicks(market: string | null | undefined): boolean {
  return market != null && market.toLowerCase().includes('over 0.5');
}

/**
 * Elite is a shortlist to bet, and an unpriced pick cannot be judged good or bad. The half-goals
 * engines send a null price because the data model carries no half-time or second-half lines, so
 * they were filling the board with selections nobody could evaluate — and a market that lands often
 * but pays 1.4 can lose money at a high hit rate.
 */
export function hasUsablePrice(rec: Recommendation): boolean {
  return rec.odds != null && rec.odds > 1.0;
}

export function isEliteEligible(rec: Recommendation): boolean {
  return (
    rec.confidence?.toUpperCase() === 'STRONG'
    && isEliteEligibleType(rec.type)
    && !isExcludedFromElitePicks(rec.market)
    && hasUsablePrice(rec)
  );
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
 * At most one selection per fixture; default cap 10; at most 3 of any one market family; priced only.
 */
export function selectElitePicks(
  recommendations: Recommendation[],
  limit: number = ELITE_PICKS_LIMIT
): Recommendation[] {
  const pool = recommendations.filter(isEliteEligible);

  pool.sort(compareEliteRank);

  const seenFixtures = new Set<number>();
  const familyCounts = new Map<string, number>();
  const elite: Recommendation[] = [];

  for (const rec of pool) {
    if (seenFixtures.has(rec.fixtureId)) continue;

    const family = marketFamily(rec.type);
    const familyCount = familyCounts.get(family) ?? 0;
    if (familyCount >= ELITE_MAX_PER_FAMILY) continue;

    seenFixtures.add(rec.fixtureId);
    familyCounts.set(family, familyCount + 1);
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

export function elitePickKey(fixtureId: number, type: string): string {
  return `${fixtureId}:${type}`;
}

export function toEliteKeySet(picks: Recommendation[]): Set<string> {
  return new Set(picks.map((pick) => elitePickKey(pick.fixtureId, pick.type)));
}
