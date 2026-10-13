import type { Recommendation, RecommendationType } from '../types';
import { compareByKickoff, compareByScoreDesc, matchesKickoffWindow, type KickoffWindow } from './kickoff';
import { isFeaturedCompetition } from './featuredCompetitions';
import { matchesConfidence, type CompetitionScope, type ConfidenceFilter } from './recommendationFilters';
import { SECTION_CONFIG, SECTION_ORDER, includeInMarketSection, isStandaloneType } from './recommendationSections';
import { elitePickKey } from './elitePicks';

export interface BetGenFilters {
  confidence: ConfidenceFilter;
  kickoffWindow: KickoffWindow;
  scope: CompetitionScope;
}

export type Boards = Partial<Record<RecommendationType, Recommendation[]>>;

export interface Accumulator {
  legs: Recommendation[];
  /** Product of the priced legs' odds; null when no leg is priced. */
  combinedOdds: number | null;
  pricedLegs: number;
  /** Product of the legs' win probabilities (0–1), treating legs as independent. */
  probability: number | null;
  probabilityLegs: number;
}

export type ToWinResult =
  | { status: 'built'; targetOdds: number; acca: Accumulator }
  | { status: 'unreachable'; targetOdds: number; maxOdds: number | null; candidates: number };

/**
 * The boards as the Recommendations page shows them by default: same market rules and filters,
 * ranked by score then kickoff. Excluded picks (removed by the user) are dropped.
 */
export function buildBoards(
  grouped: Record<string, Recommendation[]> | null | undefined,
  filters: BetGenFilters,
  excluded: ReadonlySet<string> = new Set(),
  nowMs: number = Date.now()
): Boards {
  const boards: Boards = {};
  if (!grouped) return boards;
  for (const type of SECTION_ORDER) {
    const picks = (grouped[type] ?? []).filter((rec) =>
      includeInMarketSection(rec)
      && (isStandaloneType(type) || matchesConfidence(rec.confidence, filters.confidence))
      && (filters.scope !== 'featured' || isFeaturedCompetition(rec.leagueName))
      && matchesKickoffWindow(rec.matchDateUnix, filters.kickoffWindow, nowMs)
      && !excluded.has(elitePickKey(rec.fixtureId, rec.type))
    );
    picks.sort((a, b) => compareByScoreDesc(a.score, b.score) || compareByKickoff(a.matchDateUnix, b.matchDateUnix));
    boards[type] = picks;
  }
  return boards;
}

/** Decimal price usable in an accumulator, or null. */
export function legOdds(rec: Recommendation): number | null {
  return rec.odds != null && rec.odds > 1 ? rec.odds : null;
}

/** Win probability (0–1) for boards whose score is a probability; null for quality-style scores. */
export function legProbability(rec: Recommendation): number | null {
  if (SECTION_CONFIG[rec.type]?.scoreLabel !== 'Probability') return null;
  return rec.score > 0 && rec.score < 100 ? rec.score / 100 : null;
}

export function summarizeAccumulator(legs: Recommendation[]): Accumulator {
  let combinedOdds: number | null = null;
  let probability: number | null = null;
  let pricedLegs = 0;
  let probabilityLegs = 0;
  for (const leg of legs) {
    const odds = legOdds(leg);
    if (odds != null) {
      combinedOdds = (combinedOdds ?? 1) * odds;
      pricedLegs += 1;
    }
    const p = legProbability(leg);
    if (p != null) {
      probability = (probability ?? 1) * p;
      probabilityLegs += 1;
    }
  }
  return { legs, combinedOdds, pricedLegs, probability, probabilityLegs };
}

/**
 * Build My Bet From Top Picks: the top pick on each board, in board order. Bookmakers won't take
 * two legs from one match in a normal accumulator, so a board whose top pick is on a match already
 * used falls through to its next pick.
 */
export function topPicksAccumulator(boards: Boards): Accumulator {
  const usedFixtures = new Set<number>();
  const legs: Recommendation[] = [];
  for (const type of SECTION_ORDER) {
    const pick = boards[type]?.find((rec) => !usedFixtures.has(rec.fixtureId));
    if (!pick) continue;
    usedFixtures.add(pick.fixtureId);
    legs.push(pick);
  }
  return summarizeAccumulator(legs);
}

/** Log-odds width of a search bucket (about 0.5% in price). */
const LOG_ODDS_STEP = 0.005;

/**
 * Build My Bet To Win: exactly `selections` priced legs, one per match, whose combined odds reach
 * `targetOdds`, choosing the combination with the highest chance of every leg landing.
 *
 * Maximising Π p subject to Π odds ≥ target is a knapsack in log space (Σ log p, Σ log odds), with
 * one item allowed per match, solved by dynamic programming over matches. Partial bets are grouped
 * into log-odds buckets so the table stays small, but each cell carries its exact combined price
 * and only bets that truly reach the target land in the final bucket, so a built bet always pays
 * at least the target. Grouping can only cost a sliver of probability, never the target.
 */
export function toWinAccumulator(boards: Boards, targetOdds: number, selections: number): ToWinResult {
  const byFixture = new Map<number, Recommendation[]>();
  for (const type of SECTION_ORDER) {
    for (const rec of boards[type] ?? []) {
      if (legOdds(rec) == null || legProbability(rec) == null) continue;
      const list = byFixture.get(rec.fixtureId);
      if (list) list.push(rec);
      else byFixture.set(rec.fixtureId, [rec]);
    }
  }
  const fixtures = [...byFixture.values()];
  const candidates = fixtures.reduce((sum, list) => sum + list.length, 0);

  const bestPerFixture = fixtures
    .map((list) => Math.max(...list.map((rec) => legOdds(rec) as number)))
    .sort((a, b) => b - a);
  const maxOdds = bestPerFixture.length >= selections
    ? bestPerFixture.slice(0, selections).reduce((product, odds) => product * odds, 1)
    : null;
  if (selections < 1 || maxOdds == null || maxOdds < targetOdds) {
    return { status: 'unreachable', targetOdds, maxOdds, candidates };
  }

  const logTarget = Math.log(targetOdds);
  // Bucket `reached` holds bets at or past the target; the rest sit below it by log-odds.
  const reached = Math.max(1, Math.floor(logTarget / LOG_ODDS_STEP) + 1);
  const bucketOf = (logOdds: number) =>
    logOdds >= logTarget - 1e-9 ? reached : Math.min(reached - 1, Math.max(0, Math.floor(logOdds / LOG_ODDS_STEP)));
  const width = reached + 1;
  const cells = (selections + 1) * width;
  const at = (k: number, b: number) => k * width + b;

  let prevValue = new Float64Array(cells).fill(Number.NEGATIVE_INFINITY);
  let prevLogOdds = new Float64Array(cells);
  prevValue[at(0, bucketOf(0))] = 0;
  // Per match: which pick (1-based, 0 = none) produced each cell, and the bucket it came from.
  const choices: Int8Array[] = [];
  const sources: Int32Array[] = [];

  for (const picks of fixtures) {
    const nextValue = Float64Array.from(prevValue);
    const nextLogOdds = Float64Array.from(prevLogOdds);
    const choice = new Int8Array(cells);
    const source = new Int32Array(cells);
    picks.forEach((rec, index) => {
      const logOdds = Math.log(legOdds(rec) as number);
      const value = Math.log(legProbability(rec) as number);
      for (let k = 1; k <= selections; k++) {
        for (let b = 0; b <= reached; b++) {
          const from = prevValue[at(k - 1, b)];
          if (from === Number.NEGATIVE_INFINITY) continue;
          const combined = prevLogOdds[at(k - 1, b)] + logOdds;
          const cell = at(k, bucketOf(combined));
          const candidate = from + value;
          if (candidate > nextValue[cell]) {
            nextValue[cell] = candidate;
            nextLogOdds[cell] = combined;
            choice[cell] = index + 1;
            source[cell] = b;
          }
        }
      }
    });
    choices.push(choice);
    sources.push(source);
    prevValue = nextValue;
    prevLogOdds = nextLogOdds;
  }

  if (prevValue[at(selections, reached)] === Number.NEGATIVE_INFINITY) {
    return { status: 'unreachable', targetOdds, maxOdds, candidates };
  }

  const legs: Recommendation[] = [];
  let k = selections;
  let b = reached;
  for (let f = fixtures.length - 1; f >= 0 && k > 0; f--) {
    const picked = choices[f][at(k, b)];
    if (picked === 0) continue;
    legs.push(fixtures[f][picked - 1]);
    b = sources[f][at(k, b)];
    k -= 1;
  }
  legs.sort((a, c) => compareByKickoff(a.matchDateUnix, c.matchDateUnix));
  return { status: 'built', targetOdds, acca: summarizeAccumulator(legs) };
}
