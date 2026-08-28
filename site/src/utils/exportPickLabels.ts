import type { Recommendation } from '../types';
import { sectionTitle } from './recommendationSections';
import { formatTopVsBottomDisplay } from './topVsBottomDisplay';

export interface ExportPickLabels {
  leagueLabel: string | null;
  fixtureLabel: string;
  /** Bet selection shown in the shortlist Selection column. */
  selectionLabel: string;
  /** Engine/market category (e.g. Match Result, Both Teams To Score). */
  marketTypeLabel: string;
}

export function formatExportPickLabels(rec: Recommendation): ExportPickLabels {
  const topVsBottom = rec.type === 'TOP_VS_BOTTOM' ? formatTopVsBottomDisplay(rec) : null;

  return {
    leagueLabel: rec.leagueName?.trim() || null,
    fixtureLabel:
      topVsBottom?.fixtureLabel ?? `${rec.homeTeamName} vs ${rec.awayTeamName}`,
    selectionLabel: topVsBottom?.selectionLabel ?? rec.market,
    marketTypeLabel: sectionTitle(rec.type),
  };
}

/** Full selection line for exports: category + specific pick + optional odds suffix. */
export function formatExportSelectionLine(
  rec: Recommendation,
  labels: ExportPickLabels = formatExportPickLabels(rec),
): string {
  const oddsStr = rec.odds != null && rec.odds > 0 ? ` @ ${rec.odds.toFixed(2)}` : '';
  return `${labels.marketTypeLabel}: ${labels.selectionLabel}${oddsStr}`;
}
