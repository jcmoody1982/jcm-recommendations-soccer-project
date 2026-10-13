import { describe, expect, it } from 'vitest';
import type { ElitePick, Recommendation } from '../types';
import {
  eliteDayLabel,
  eliteDaysFromPool,
  elitePickKey,
  flattenGroupedRecommendations,
  toEliteKeySet,
} from './elitePicks';

function rec(overrides: Partial<Recommendation> = {}): Recommendation {
  return {
    fixtureId: 1,
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

function pick(date: string, rank: number, fixtureId: number, type: ElitePick['type'] = 'MATCH_RESULT'): ElitePick {
  return { date, rank, fixtureId, type };
}

describe('eliteDaysFromPool', () => {
  it('groups picks by day in date then rank order', () => {
    const pool = [rec({ fixtureId: 1 }), rec({ fixtureId: 2 }), rec({ fixtureId: 3, type: 'BTTS' })];
    const days = eliteDaysFromPool(
      [pick('2026-10-14', 1, 3, 'BTTS'), pick('2026-10-13', 2, 2), pick('2026-10-13', 1, 1)],
      pool,
    );

    expect(days.map((day) => day.date)).toEqual(['2026-10-13', '2026-10-14']);
    expect(days[0].picks.map((r) => r.fixtureId)).toEqual([1, 2]);
    expect(days[1].picks.map((r) => r.fixtureId)).toEqual([3]);
  });

  it('drops picks the page has filtered out instead of back-filling', () => {
    const days = eliteDaysFromPool([pick('2026-10-13', 1, 1), pick('2026-10-13', 2, 2)], [rec({ fixtureId: 2 })]);

    expect(days).toHaveLength(1);
    expect(days[0].picks.map((r) => r.fixtureId)).toEqual([2]);
  });

  it('matches on fixture and type together', () => {
    expect(eliteDaysFromPool([pick('2026-10-13', 1, 1, 'BTTS')], [rec({ fixtureId: 1 })])).toEqual([]);
  });

  it('handles missing picks', () => {
    expect(eliteDaysFromPool(undefined, [rec()])).toEqual([]);
  });
});

describe('eliteDayLabel', () => {
  // 23:30 UTC on 12 Oct is already 13 Oct in London (BST).
  const now = new Date('2026-10-12T23:30:00Z');

  it('names today and tomorrow on the London calendar', () => {
    expect(eliteDayLabel('2026-10-13', now)).toBe('Today');
    expect(eliteDayLabel('2026-10-14', now)).toBe('Tomorrow');
  });

  it('spells out later days', () => {
    expect(eliteDayLabel('2026-10-15', now)).toBe('Thu 15 Oct');
  });
});

describe('helpers', () => {
  it('flattens grouped recommendations', () => {
    const a = rec({ fixtureId: 1 });
    const b = rec({ fixtureId: 2 });
    expect(flattenGroupedRecommendations({ BTTS: [a], DRAW: [b] })).toEqual([a, b]);
    expect(flattenGroupedRecommendations(null)).toEqual([]);
  });

  it('builds fixture:type keys', () => {
    expect(elitePickKey(7, 'BTTS')).toBe('7:BTTS');
    expect(toEliteKeySet([pick('2026-10-13', 1, 7, 'BTTS')]).has('7:BTTS')).toBe(true);
  });
});
