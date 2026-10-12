import { describe, expect, it } from 'vitest';
import type { Recommendation, RecommendationType } from '../types';
import {
  ELITE_MIN_OVER_15_PRICE,
  ELITE_MIN_PRICE,
  ELITE_PICKS_LIMIT,
  elitePickKey,
  flattenGroupedRecommendations,
  isEliteEligible,
  isExcludedFromElitePicks,
  isOver15Line,
  marketFamily,
  selectElitePicks,
  toEliteKeySet,
} from './elitePicks';

// Cases mirror core ElitePicksSelectorTest so the board and Results stay in step.

let nextFixture = 1;

function rec(overrides: Partial<Recommendation> = {}): Recommendation {
  return {
    fixtureId: nextFixture++,
    homeTeamId: 1,
    awayTeamId: 2,
    homeTeamName: 'Home',
    awayTeamName: 'Away',
    matchDateUnix: 1_800_000_000,
    leagueId: 1,
    leagueName: 'England Premier League',
    leagueImage: null,
    type: 'MATCH_RESULT',
    confidence: 'STRONG',
    score: 70,
    market: 'Home to win',
    odds: 1.9,
    description: '',
    factors: {},
    generatedAt: '2026-10-10T00:00:00Z',
    ...overrides,
  };
}

function many(count: number, overrides: (i: number) => Partial<Recommendation>): Recommendation[] {
  return Array.from({ length: count }, (_, i) => rec(overrides(i)));
}

describe('selectElitePicks', () => {
  it('selects Strong eligible picks only, one per fixture, capped at ten', () => {
    const shared = rec({ fixtureId: 500, score: 90, type: 'BTTS', market: 'Both teams to score' });
    const sameFixture = rec({ fixtureId: 500, score: 85, type: 'MATCH_RESULT' });
    const moderate = rec({ score: 99, confidence: 'MODERATE' });
    const ineligibleType = rec({ score: 99, type: 'BOOKING_POINTS', market: 'Over 40 booking points' });
    const types: RecommendationType[] = ['MATCH_RESULT', 'BTTS', 'DOUBLE_CHANCE', 'DRAW', 'VALUE_BET', 'TOP_VS_BOTTOM'];
    const filler = many(12, (i) => ({ score: 60 + i, type: types[i % types.length], market: `Market ${i}` }));

    const elite = selectElitePicks([shared, sameFixture, moderate, ineligibleType, ...filler]);

    expect(elite).toHaveLength(ELITE_PICKS_LIMIT);
    expect(elite[0]).toBe(shared);
    expect(elite).not.toContain(sameFixture);
    expect(elite).not.toContain(moderate);
    expect(elite).not.toContain(ineligibleType);
    expect(new Set(elite.map((p) => p.fixtureId)).size).toBe(elite.length);
  });

  it('never carries Draw, which sits outside the confidence tiers', () => {
    const draw = rec({ fixtureId: 900, score: 99, type: 'DRAW', market: 'Draw', odds: 3.2 });
    const btts = rec({ fixtureId: 901, score: 80, type: 'BTTS' });

    expect(selectElitePicks([draw, btts])).toEqual([btts]);
  });

  it('breaks score ties on shorter price, then sooner kickoff', () => {
    const longer = rec({ score: 80, odds: 2.1, matchDateUnix: 100 });
    const shorter = rec({ score: 80, odds: 1.5, matchDateUnix: 300 });
    const shorterLater = rec({ score: 80, odds: 1.5, matchDateUnix: 400 });

    expect(selectElitePicks([longer, shorterLater, shorter])).toEqual([shorter, shorterLater, longer]);
  });

  it('caps any one market family at three and fills with other markets', () => {
    const btts = many(5, (i) => ({ type: 'BTTS', market: 'Both teams to score', score: 95 - i }));
    const others = many(3, (i) => ({ type: 'MATCH_RESULT', score: 60 - i }));

    const elite = selectElitePicks([...btts, ...others]);

    expect(elite.filter((p) => p.type === 'BTTS')).toHaveLength(3);
    expect(elite.filter((p) => p.type === 'MATCH_RESULT')).toHaveLength(3);
  });

  it('caps the whole goals-over family across its type names', () => {
    const goals = [
      ...many(2, () => ({ type: 'OVER_15_GOALS', market: 'Over 1.5 Goals', score: 95 })),
      ...many(2, () => ({ type: 'FIRST_HALF_GOALS', market: 'Over 0.5 HT Goals x', score: 94 })),
      ...many(2, () => ({ type: 'OVER_25_GOALS', market: 'Over 2.5 Goals', score: 93 })),
      ...many(2, () => ({ type: 'SECOND_HALF_GOALS', market: 'Over 1.5 2H Goals', score: 92, odds: 1.6 })),
    ];

    const elite = selectElitePicks(goals);

    expect(elite.every((p) => marketFamily(p.type) === 'GOALS_OVER')).toBe(true);
    expect(elite).toHaveLength(3);
  });

  it('caps Over 1.5 at three even when VALUE_BET sells it outside the goals family', () => {
    const family = many(2, () => ({ type: 'OVER_15_GOALS', market: 'Over 1.5 Goals', score: 95 }));
    const value = many(3, () => ({ type: 'VALUE_BET', market: 'Over 1.5 Goals', score: 90 }));

    const elite = selectElitePicks([...family, ...value]);

    expect(elite.filter((p) => isOver15Line(p.market))).toHaveLength(3);
  });

  it('excludes unpriced and too-short prices but accepts the floor exactly', () => {
    const unpriced = rec({ odds: null, score: 99 });
    const tooShort = rec({ odds: 1.19, score: 98 });
    const atFloor = rec({ odds: ELITE_MIN_PRICE, score: 97 });

    expect(selectElitePicks([unpriced, tooShort, atFloor])).toEqual([atFloor]);
  });

  it('excludes Over 1.5 priced under 1.22 from every type but keeps other markets at the general floor', () => {
    const shortOver15 = rec({ type: 'OVER_15_GOALS', market: 'Over 1.5 Goals', odds: 1.21, score: 95 });
    const shortValue = rec({ type: 'VALUE_BET', market: 'Over 1.5 Goals', odds: 1.2, score: 94 });
    const over15AtFloor = rec({ type: 'OVER_15_GOALS', market: 'Over 1.5 Goals', odds: ELITE_MIN_OVER_15_PRICE, score: 93 });
    const doubleChance = rec({ type: 'DOUBLE_CHANCE', market: 'Home/Draw (1X)', odds: 1.2, score: 92 });

    expect(selectElitePicks([shortOver15, shortValue, over15AtFloor, doubleChance]))
      .toEqual([over15AtFloor, doubleChance]);
  });

  it('respects a custom limit', () => {
    expect(selectElitePicks(many(5, (i) => ({ score: 90 - i })), 2)).toHaveLength(2);
  });
});

describe('market screening', () => {
  it('excludes Over 0.5, Over 3.5 and Under 1.5 lines regardless of type', () => {
    expect(isExcludedFromElitePicks('Over 0.5 Goals')).toBe(true);
    expect(isExcludedFromElitePicks('Arsenal Over 3.5 Goals')).toBe(true);
    expect(isExcludedFromElitePicks('Under 1.5 Goals')).toBe(true);
    expect(isExcludedFromElitePicks(null)).toBe(false);

    expect(isEliteEligible(rec({ type: 'VALUE_BET', market: 'Over 3.5 Goals' }))).toBe(false);
    expect(isEliteEligible(rec({ type: 'OVER_GOALS', market: 'Under 1.5 Goals' }))).toBe(false);
  });

  it('keeps the lines we trust', () => {
    expect(isExcludedFromElitePicks('Over 2.5 Goals')).toBe(false);
    expect(isExcludedFromElitePicks('Under 2.5 Goals')).toBe(false);
    expect(isExcludedFromElitePicks('Over 1.5 Goals')).toBe(false);
  });

  it('treats half and full Over 1.5 as one line and leaves corners alone', () => {
    expect(isOver15Line('Over 1.5 HT Goals')).toBe(true);
    expect(isOver15Line('Over 1.5 2H Goals')).toBe(true);
    expect(isOver15Line('Over 11.5 Corners')).toBe(false);
    expect(isOver15Line(undefined)).toBe(false);
  });

  it('groups goals-over types into one family and leaves others alone', () => {
    expect(marketFamily('over_15_goals')).toBe('GOALS_OVER');
    expect(marketFamily('FIRST_HALF_GOALS')).toBe('GOALS_OVER');
    expect(marketFamily('BTTS')).toBe('BTTS');
    expect(marketFamily(null)).toBe('');
  });

  it('is case-insensitive on confidence', () => {
    expect(isEliteEligible(rec({ confidence: 'strong' as Recommendation['confidence'] }))).toBe(true);
  });
});

describe('helpers', () => {
  it('flattens grouped recommendations', () => {
    const a = rec();
    const b = rec();
    expect(flattenGroupedRecommendations({ BTTS: [a], DRAW: [b] })).toEqual([a, b]);
    expect(flattenGroupedRecommendations(null)).toEqual([]);
  });

  it('builds fixture:type keys', () => {
    const a = rec({ fixtureId: 7, type: 'BTTS' });
    expect(elitePickKey(7, 'BTTS')).toBe('7:BTTS');
    expect(toEliteKeySet([a]).has('7:BTTS')).toBe(true);
  });
});
