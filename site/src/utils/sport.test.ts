import { describe, expect, it } from 'vitest';
import {
  SOCCER_FIXTURES,
  SOCCER_RECOMMENDATIONS,
  SOCCER_RESULTS,
  isNavActive,
  soccerFixturePath,
  sportFromPath,
} from './sport';

describe('sportFromPath', () => {
  it('detects US Football at and below its root', () => {
    expect(sportFromPath('/us-football')).toBe('us-football');
    expect(sportFromPath('/us-football/picks')).toBe('us-football');
  });

  it('defaults everything else to soccer', () => {
    expect(sportFromPath('/soccer/recommendations')).toBe('soccer');
    expect(sportFromPath('/')).toBe('soccer');
    expect(sportFromPath('/us-footballish')).toBe('soccer');
  });
});

describe('isNavActive', () => {
  it('keeps Fixtures active on a fixture detail page', () => {
    expect(isNavActive(SOCCER_FIXTURES, SOCCER_FIXTURES)).toBe(true);
    expect(isNavActive(soccerFixturePath(42), SOCCER_FIXTURES)).toBe(true);
    expect(isNavActive('/soccer/fixtures-archive', SOCCER_FIXTURES)).toBe(false);
  });

  it('matches other items exactly', () => {
    expect(isNavActive(SOCCER_RESULTS, SOCCER_RESULTS)).toBe(true);
    expect(isNavActive(`${SOCCER_RECOMMENDATIONS}/extra`, SOCCER_RECOMMENDATIONS)).toBe(false);
  });
});

describe('soccerFixturePath', () => {
  it('builds the fixture detail route', () => {
    expect(soccerFixturePath(42)).toBe('/soccer/fixtures/42');
    expect(soccerFixturePath('7')).toBe('/soccer/fixtures/7');
  });
});
