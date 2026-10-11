import { describe, expect, it } from 'vitest';
import { isFeaturedCompetition, normalizeLeagueName } from './featuredCompetitions';

describe('normalizeLeagueName', () => {
  it('strips accents, case and punctuation but keeps dots', () => {
    expect(normalizeLeagueName('  Argentina Primera División ')).toBe('argentina primera division');
    expect(normalizeLeagueName('Germany 2. Bundesliga')).toBe('germany 2. bundesliga');
    expect(normalizeLeagueName('USA - MLS')).toBe('usa mls');
  });
});

describe('isFeaturedCompetition', () => {
  it.each([
    'England Premier League',
    'Germany Bundesliga',
    'Spain La Liga',
    'Mexico Liga MX',
    'USA MLS',
    'Norway Eliteserien',
    'Argentina Primera División',
  ])('features %s', (name) => {
    expect(isFeaturedCompetition(name)).toBe(true);
  });

  it.each([
    'Germany 2. Bundesliga',
    'England League One',
    'Scotland Premiership',
    'Wales Premier League',
  ])('does not feature %s', (name) => {
    expect(isFeaturedCompetition(name)).toBe(false);
  });

  it('is false for empty names', () => {
    expect(isFeaturedCompetition(null)).toBe(false);
    expect(isFeaturedCompetition('   ')).toBe(false);
  });
});
