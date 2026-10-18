import { describe, expect, it } from 'vitest';
import type { Recommendation, RecommendationType } from '../types';
import {
  buildBoards,
  legProbability,
  summarizeAccumulator,
  toWinAccumulator,
  topPicksAccumulator,
  type BetGenFilters,
  type Boards,
} from './betGen';

const FILTERS: BetGenFilters = { confidence: 'strong', kickoffWindow: 'all', scope: 'all' };
const NOW_MS = 1_800_000_000_000;

function rec(overrides: Partial<Recommendation> = {}): Recommendation {
  return {
    fixtureId: 1,
    homeTeamId: 1,
    awayTeamId: 2,
    homeTeamName: 'Home',
    awayTeamName: 'Away',
    matchDateUnix: NOW_MS / 1000 + 3600,
    leagueId: 1,
    leagueName: 'England Premier League',
    leagueImage: null,
    type: 'BTTS',
    confidence: 'STRONG',
    score: 70,
    market: 'BTTS Yes',
    odds: 1.8,
    description: '',
    factors: {},
    generatedAt: '2026-10-10T00:00:00Z',
    ...overrides,
  };
}

describe('buildBoards', () => {
  it('never offers information-only Draw Watch matches as legs', () => {
    const grouped = {
      DRAW_WATCH: [rec({ fixtureId: 7, type: 'DRAW_WATCH', confidence: 'WEAK', market: 'Draw', odds: 3.1 })],
    };
    const boards = buildBoards(grouped, FILTERS, new Set(), NOW_MS);
    expect(boards.DRAW_WATCH).toBeUndefined();
    expect(topPicksAccumulator(boards).legs).toHaveLength(0);
  });

  it('filters like the Recommendations page and ranks by score', () => {
    const grouped = {
      BTTS: [
        rec({ fixtureId: 1, score: 70 }),
        rec({ fixtureId: 2, score: 80 }),
        rec({ fixtureId: 3, score: 90, confidence: 'MODERATE' }),
      ],
    };
    const boards = buildBoards(grouped, FILTERS, new Set(), NOW_MS);
    expect(boards.BTTS?.map((r) => r.fixtureId)).toEqual([2, 1]);
  });

  it('drops picks the user removed', () => {
    const grouped = { BTTS: [rec({ fixtureId: 1 }), rec({ fixtureId: 2 })] };
    const boards = buildBoards(grouped, FILTERS, new Set(['1:BTTS']), NOW_MS);
    expect(boards.BTTS?.map((r) => r.fixtureId)).toEqual([2]);
  });
});

describe('topPicksAccumulator', () => {
  it('takes the top pick per board and skips a match already used', () => {
    const boards: Boards = {
      MATCH_RESULT: [rec({ fixtureId: 1, type: 'MATCH_RESULT', odds: 1.5 })],
      BTTS: [rec({ fixtureId: 1 }), rec({ fixtureId: 2, odds: 2 })],
      OVER_CORNERS: [rec({ fixtureId: 3, type: 'OVER_CORNERS', odds: null, score: 11 })],
    };
    const acca = topPicksAccumulator(boards);

    expect(acca.legs.map((r) => [r.type, r.fixtureId])).toEqual([
      ['MATCH_RESULT', 1],
      ['BTTS', 2],
      ['OVER_CORNERS', 3],
    ]);
    expect(acca.combinedOdds).toBeCloseTo(3);
    expect(acca.pricedLegs).toBe(2);
    // Corners scores are predicted counts, not probabilities.
    expect(acca.probabilityLegs).toBe(2);
    expect(acca.probability).toBeCloseTo(0.49);
  });
});

describe('legProbability', () => {
  it('only reads probability boards', () => {
    expect(legProbability(rec({ score: 62 }))).toBeCloseTo(0.62);
    expect(legProbability(rec({ type: 'VALUE_BET', score: 62 }))).toBeNull();
  });
});

function bruteForce(pool: Recommendation[], target: number, n: number): number {
  let best = -1;
  const walk = (start: number, chosen: Recommendation[]) => {
    if (chosen.length === n) {
      const acca = summarizeAccumulator(chosen);
      if ((acca.combinedOdds ?? 0) >= target) best = Math.max(best, acca.probability ?? 0);
      return;
    }
    for (let i = start; i < pool.length; i++) {
      if (chosen.some((c) => c.fixtureId === pool[i].fixtureId)) continue;
      walk(i + 1, [...chosen, pool[i]]);
    }
  };
  walk(0, []);
  return best;
}

describe('toWinAccumulator', () => {
  it.each(Array.from({ length: 15 }, (_, i) => i + 1))('finds the most likely combination that reaches the target (pool %i)', (poolSeed) => {
    const types: RecommendationType[] = ['BTTS', 'MATCH_RESULT', 'OVER_25_GOALS'];
    let seed = poolSeed * 7919;
    const random = () => {
      seed = (seed * 16807) % 2147483647;
      return seed / 2147483647;
    };
    const pool = Array.from({ length: 24 }, (_, i) => {
      const probability = 0.35 + random() * 0.5;
      return rec({
        fixtureId: 1 + (i % 9),
        type: types[i % types.length],
        score: Math.round(probability * 100),
        odds: Math.round((1 / probability) * (0.9 + random() * 0.25) * 100) / 100,
      });
    });
    const boards: Boards = {};
    for (const r of pool) (boards[r.type] ??= []).push(r);

    for (const [target, n] of [[4, 2], [10, 4], [6, 3], [25, 5], [3, 1]] as const) {
      const result = toWinAccumulator(boards, target, n);
      const expected = bruteForce(pool, target, n);
      if (expected < 0) {
        expect(result.status).toBe('unreachable');
        continue;
      }
      expect(result.status).toBe('built');
      if (result.status !== 'built') continue;
      expect(result.acca.legs).toHaveLength(n);
      expect(new Set(result.acca.legs.map((r) => r.fixtureId)).size).toBe(n);
      expect(result.acca.combinedOdds).toBeGreaterThanOrEqual(target);
      // Grouping partial bets into price buckets can pass over the very best combination; the
      // result must still be essentially as likely.
      expect(result.acca.probability).toBeGreaterThan(expected * 0.97);
    }
  });

  it('prefers two safe legs over one long shot and one banker when both reach the target', () => {
    const boards: Boards = {
      BTTS: [
        rec({ fixtureId: 1, odds: 2.0, score: 55 }),
        rec({ fixtureId: 2, odds: 2.0, score: 55 }),
      ],
      MATCH_RESULT: [
        rec({ fixtureId: 3, type: 'MATCH_RESULT', odds: 3.6, score: 25 }),
        rec({ fixtureId: 4, type: 'MATCH_RESULT', odds: 1.15, score: 85 }),
      ],
    };
    const result = toWinAccumulator(boards, 4, 2);
    expect(result.status).toBe('built');
    if (result.status === 'built') {
      expect(result.acca.legs.map((r) => r.fixtureId).sort()).toEqual([1, 2]);
    }
  });

  it('reports the best reachable price when the target is out of reach', () => {
    const boards: Boards = {
      BTTS: [rec({ fixtureId: 1, odds: 2 }), rec({ fixtureId: 2, odds: 1.5 })],
    };
    const result = toWinAccumulator(boards, 10, 2);
    expect(result).toMatchObject({ status: 'unreachable', maxOdds: 3, candidates: 2 });
  });

  it('cannot build more legs than there are matches', () => {
    const boards: Boards = { BTTS: [rec({ fixtureId: 1 }), rec({ fixtureId: 1, type: 'MATCH_RESULT' })] };
    expect(toWinAccumulator(boards, 1.5, 2)).toMatchObject({ status: 'unreachable', maxOdds: null });
  });

  it('skips unpriced and non-probability picks', () => {
    const boards: Boards = {
      BTTS: [rec({ fixtureId: 1, odds: null })],
      VALUE_BET: [rec({ fixtureId: 2, type: 'VALUE_BET', odds: 3 })],
    };
    expect(toWinAccumulator(boards, 2, 1)).toMatchObject({ status: 'unreachable', candidates: 0 });
  });
});
