import type { Recommendation } from '../types';

function asPosition(value: unknown): number | null {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return Math.trunc(value);
  }
  if (typeof value === 'string' && value.trim() !== '') {
    const n = Number(value);
    return Number.isFinite(n) ? Math.trunc(n) : null;
  }
  return null;
}

export function formatTeamWithPosition(name: string, position: number | null | undefined): string {
  if (position == null) return name;
  return `${name} (${position})`;
}

export interface TopVsBottomDisplay {
  fixtureLabel: string;
  selectionLabel: string;
  /** e.g. "+15" when a position gap is available */
  gapLabel: string | null;
}

/**
 * Formats Top vs Bottom fixture/selection labels with league positions
 * and a signed position-gap string from recommendation factors.
 */
export function formatTopVsBottomDisplay(rec: Recommendation): TopVsBottomDisplay {
  const factors = rec.factors || {};
  const homePos = asPosition(factors.homePosition);
  const awayPos = asPosition(factors.awayPosition);
  const gap = asPosition(factors.positionGap);

  const fixtureLabel = `${formatTeamWithPosition(rec.homeTeamName, homePos)} vs ${formatTeamWithPosition(rec.awayTeamName, awayPos)}`;

  let selectionPos: number | null = null;
  if (factors.homeIsFavorite === true) {
    selectionPos = homePos;
  } else if (factors.homeIsFavorite === false) {
    selectionPos = awayPos;
  } else if (rec.market === rec.homeTeamName) {
    selectionPos = homePos;
  } else if (rec.market === rec.awayTeamName) {
    selectionPos = awayPos;
  }

  const selectionLabel = formatTeamWithPosition(rec.market, selectionPos);
  const gapLabel = gap != null && gap > 0 ? `+${gap}` : gap != null ? String(gap) : null;

  return { fixtureLabel, selectionLabel, gapLabel };
}
