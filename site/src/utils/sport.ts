export const SOCCER_RECOMMENDATIONS = '/soccer/recommendations';
export const SOCCER_FIXTURES = '/soccer/fixtures';
export const SOCCER_RESULTS = '/soccer/results';
export const SOCCER_SHORTLIST = '/soccer/shortlist';
export const US_FOOTBALL_PATH = '/us-football';

export type SportId = 'soccer' | 'us-football';

export const SPORTS: Array<{ id: SportId; label: string; path: string }> = [
  { id: 'soccer', label: 'Soccer', path: SOCCER_RECOMMENDATIONS },
  { id: 'us-football', label: 'US Football', path: US_FOOTBALL_PATH },
];

export function soccerFixturePath(fixtureId: number | string): string {
  return `${SOCCER_FIXTURES}/${fixtureId}`;
}

export function sportFromPath(pathname: string): SportId {
  if (pathname === US_FOOTBALL_PATH || pathname.startsWith(`${US_FOOTBALL_PATH}/`)) {
    return 'us-football';
  }
  return 'soccer';
}

/** Fixtures stays active on a fixture detail page. Other items match exactly. */
export function isNavActive(pathname: string, path: string): boolean {
  if (path === SOCCER_FIXTURES) {
    return pathname === path || pathname.startsWith(`${path}/`);
  }
  return pathname === path;
}
