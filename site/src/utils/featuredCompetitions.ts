/**
 * Curated competition set for the Recommendations "Featured" filter.
 *
 * Match against FootyStats-style league names (e.g. "England Premier League").
 * Edit {@link FEATURED_COMPETITION_RULES} to add/remove leagues — no other file changes needed.
 */

export interface FeaturedCompetitionRule {
  /** Display label for docs / debugging. */
  label: string;
  /**
   * All of these substrings must appear in the normalised league name.
   * Prefer country + competition (e.g. "germany" + "bundesliga") to avoid
   * colliding with similarly named divisions elsewhere.
   */
  includes: readonly string[];
  /** Any of these substrings disqualifies a match (e.g. "2. bundesliga"). */
  excludes?: readonly string[];
}

/**
 * Initial Featured set. Tuned for FootyStats `league.name` strings like
 * "England Premier League", "USA MLS", "Mexico Liga MX".
 */
export const FEATURED_COMPETITION_RULES: readonly FeaturedCompetitionRule[] = [
  { label: 'English Premier League', includes: ['england', 'premier league'] },
  {
    label: 'German Bundesliga',
    includes: ['germany', 'bundesliga'],
    excludes: ['2.'],
  },
  { label: 'Italian Serie A', includes: ['italy', 'serie a'] },
  { label: 'Spanish La Liga', includes: ['spain', 'la liga'] },
  { label: 'Austrian Bundesliga', includes: ['austria', 'bundesliga'] },
  { label: 'Brazilian Serie A', includes: ['brazil', 'serie a'] },
  { label: 'Mexico Liga MX', includes: ['mexico', 'liga mx'] },
  { label: 'Mexico Liga MX (alt)', includes: ['mexico', ' mx'] },
  { label: 'US MLS', includes: ['usa', 'mls'] },
  { label: 'US MLS (alt)', includes: ['major league soccer'] },
  { label: 'English Championship', includes: ['england', 'championship'] },
  { label: 'Swiss Super League', includes: ['switzerland', 'super league'] },
  { label: 'Norwegian Eliteserien', includes: ['norway', 'eliteserien'] },
  { label: 'Norwegian Eliteserien (alt)', includes: ['eliteserien'] },
  {
    label: 'Argentinian Primera División',
    includes: ['argentina', 'primera'],
  },
  {
    label: 'Argentinian Liga Profesional',
    includes: ['argentina', 'profesional'],
  },
  { label: 'Uruguay Primera División', includes: ['uruguay', 'primera'] },
  { label: 'Colombian Primera División', includes: ['colombia', 'primera'] },
];

/** Normalise league names for substring matching (case, accents, punctuation). */
export function normalizeLeagueName(name: string): string {
  return name
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .toLowerCase()
    .replace(/[^a-z0-9.]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function matchesRule(normalized: string, rule: FeaturedCompetitionRule): boolean {
  if (rule.excludes?.some((ex) => normalized.includes(ex))) {
    return false;
  }
  return rule.includes.every((inc) => normalized.includes(inc));
}

/** True when the league is in the configurable Featured set. */
export function isFeaturedCompetition(leagueName: string | null | undefined): boolean {
  if (!leagueName?.trim()) {
    return false;
  }
  const normalized = normalizeLeagueName(leagueName);
  return FEATURED_COMPETITION_RULES.some((rule) => matchesRule(normalized, rule));
}
