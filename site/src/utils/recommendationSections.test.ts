import { describe, expect, it } from 'vitest';
import type { Recommendation } from '../types';
import {
  MIN_BACKABLE_PRICE,
  SECTION_CONFIG,
  SECTION_ORDER,
  includeInMarketSection,
  isStandaloneType,
  sectionDomId,
  sectionTitle,
} from './recommendationSections';

function rec(overrides: Partial<Recommendation>): Recommendation {
  return {
    fixtureId: 1,
    homeTeamId: 1,
    awayTeamId: 2,
    homeTeamName: 'Home',
    awayTeamName: 'Away',
    matchDateUnix: 0,
    leagueId: null,
    leagueName: null,
    leagueImage: null,
    type: 'BTTS',
    confidence: 'STRONG',
    score: 70,
    market: '',
    odds: null,
    description: '',
    factors: {},
    generatedAt: '',
    ...overrides,
  };
}

describe('section configuration', () => {
  it('has config for every ordered section and no duplicates', () => {
    expect(new Set(SECTION_ORDER).size).toBe(SECTION_ORDER.length);
    for (const type of SECTION_ORDER) {
      expect(SECTION_CONFIG[type]).toBeDefined();
    }
  });

  it('keeps paused engines off the board', () => {
    for (const paused of ['RESULT_BTTS', 'PLAYER_TO_SCORE', 'PLAYER_TO_ASSIST', 'WINNING_FORM_MISMATCH', 'LOSING_FORM_MISMATCH', 'UNDER_GOALS']) {
      expect(SECTION_ORDER).not.toContain(paused);
    }
  });

  it('titles known types and humanises unknown ones', () => {
    expect(sectionTitle('BTTS')).toBe('Both Teams To Score');
    expect(sectionTitle('SOME_NEW_TYPE')).toBe('SOME NEW TYPE');
  });

  it('builds stable DOM ids', () => {
    expect(sectionDomId('DRAW')).toBe('rec-section-DRAW');
  });

  it('keeps Draw on the board as a standalone market', () => {
    expect(SECTION_ORDER).toContain('DRAW');
    expect(isStandaloneType('DRAW')).toBe(true);
    expect(isStandaloneType('draw')).toBe(true);
    expect(isStandaloneType('BTTS')).toBe(false);
    expect(isStandaloneType(null)).toBe(false);
  });
});

describe('includeInMarketSection', () => {
  it('keeps only Over 0.5 lines on the Over 0.5 board', () => {
    expect(includeInMarketSection(rec({ type: 'OVER_05_GOALS', market: 'Arsenal Over 0.5 Goals' }))).toBe(true);
    expect(includeInMarketSection(rec({ type: 'OVER_05_GOALS', market: 'Arsenal Over 1.5 Goals' }))).toBe(false);
  });

  it('drops Double Chance below the backable price', () => {
    expect(includeInMarketSection(rec({ type: 'DOUBLE_CHANCE', odds: MIN_BACKABLE_PRICE }))).toBe(true);
    expect(includeInMarketSection(rec({ type: 'DOUBLE_CHANCE', odds: 1.15 }))).toBe(false);
    expect(includeInMarketSection(rec({ type: 'DOUBLE_CHANCE', odds: null }))).toBe(false);
  });

  it('passes other sections through', () => {
    expect(includeInMarketSection(rec({ type: 'BTTS', odds: null }))).toBe(true);
  });
});
