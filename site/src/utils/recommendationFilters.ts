/** tipped = Strong + Moderate (excludes WEAK). Default chip is Strong only. */
export type ConfidenceFilter = 'tipped' | 'strong' | 'moderate' | 'all';

/** all = every competition; featured = curated watchlist in featuredCompetitions.ts */
export type CompetitionScope = 'all' | 'featured';

export const CONFIDENCE_OPTIONS: Array<{ value: ConfidenceFilter; label: string }> = [
  { value: 'strong', label: 'Strong' },
  { value: 'moderate', label: 'Moderate' },
  { value: 'tipped', label: 'Strong + Moderate' },
  { value: 'all', label: 'All' },
];

export const COMPETITION_SCOPE_OPTIONS: Array<{ value: CompetitionScope; label: string }> = [
  { value: 'all', label: 'All competitions' },
  { value: 'featured', label: 'Featured' },
];

export function matchesConfidence(confidence: string, filter: ConfidenceFilter): boolean {
  const band = confidence?.toUpperCase();
  if (filter === 'all') return true;
  if (filter === 'strong') return band === 'STRONG';
  if (filter === 'moderate') return band === 'MODERATE';
  // tipped: hide WEAK
  return band === 'STRONG' || band === 'MODERATE';
}
