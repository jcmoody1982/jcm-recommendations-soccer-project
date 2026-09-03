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

/**
 * Most Over 1.5 slots Elite will carry, across every type that can emit that line. The family cap
 * alone is not enough: VALUE_BET is Elite-eligible, sells Over 1.5, and is not in the goals-over
 * family, so it could take another three slots that all read the same bet.
 */
export const ELITE_MAX_OVER_15 = 3;

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

/**
 * Goal lines Elite will not carry, screened on the market string because more than one Elite-eligible
 * type can emit them (OVER_GOALS, UNDER_GOALS, TOP_VS_BOTTOM, VALUE_BET).
 *
 * Over 0.5: half-goals score too high and crowd out everything else. Over 1.5 stays eligible but is
 * capped separately.
 *
 * Over 3.5: lands in about 30% of matches, but the engines only attach the label above their STRONG
 * line, so the pick is published claiming 80+ on a 30% event and is Elite-eligible by construction.
 * Over 2.5 is the highest over-line we trust.
 *
 * Under 1.5: the same construction — the engine only labels Under 1.5 above STRONG, so every pick is
 * Elite-eligible — on a line that lands in about 20% of matches. Under 2.5 stays eligible.
 */
const EXCLUDED_MARKET_LINES = ['over 0.5', 'over 3.5', 'under 1.5'];

export function isExcludedFromElitePicks(market: string | null | undefined): boolean {
  if (market == null) return false;
  const lower = market.toLowerCase();
  return EXCLUDED_MARKET_LINES.some((line) => lower.includes(line));
}

/**
 * Full-match Over 1.5, Over 1.5 HT and Over 1.5 2H are the same high-base-rate read. Does not match
 * Over 10.5 / 11.5 corners — those strings do not contain `over 1.5`.
 */
export function isOver15Line(market: string | null | undefined): boolean {
  return market != null && market.toLowerCase().includes('over 1.5');
}

/**
 * Shortest price Elite will carry. Ranking by probability pushes the shortest prices to the top, and
 * live data had the board opening with Over 1.5 at 1.01 and Double Chance at 1.03 — returns too thin
 * to be worth staking, whatever the hit rate.
 */
export const ELITE_MIN_PRICE = 1.2;

/**
 * Elite is a shortlist to bet, so a pick needs a price worth backing. An unpriced pick cannot be
 * judged good or bad at all — the half-goals engines send a null price because the data model carries
 * no half-time or second-half lines — and a priced-but-tiny return is not a bet.
 */
export function hasBackablePrice(rec: Recommendation): boolean {
  return rec.odds != null && rec.odds >= ELITE_MIN_PRICE;
}

export function isEliteEligible(rec: Recommendation): boolean {
  return (
    rec.confidence?.toUpperCase() === 'STRONG'
    && isEliteEligibleType(rec.type)
    && !isExcludedFromElitePicks(rec.market)
    && hasBackablePrice(rec)
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
 * At most one selection per fixture; default cap 10; at most 3 of any one market family;
 * at most 3 Over 1.5; priced only.
 */
export function selectElitePicks(
  recommendations: Recommendation[],
  limit: number = ELITE_PICKS_LIMIT
): Recommendation[] {
  const pool = recommendations.filter(isEliteEligible);

  pool.sort(compareEliteRank);

  const seenFixtures = new Set<number>();
  const familyCounts = new Map<string, number>();
  let over15Count = 0;
  const elite: Recommendation[] = [];

  for (const rec of pool) {
    if (seenFixtures.has(rec.fixtureId)) continue;

    const family = marketFamily(rec.type);
    const familyCount = familyCounts.get(family) ?? 0;
    if (familyCount >= ELITE_MAX_PER_FAMILY) continue;

    const over15 = isOver15Line(rec.market);
    if (over15 && over15Count >= ELITE_MAX_OVER_15) continue;

    seenFixtures.add(rec.fixtureId);
    familyCounts.set(family, familyCount + 1);
    if (over15) over15Count += 1;
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
