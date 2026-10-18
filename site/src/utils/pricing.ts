/** Bookmaker prices at or below this carry no usable return to compare against. */
const MIN_COMPARABLE_ODDS = 1.01;

export interface PriceComparison {
  /** Decimal price our probability implies: 100 ÷ score. */
  fairOdds: number;
  /** Expected return per unit at the book price, in percent: score% × odds − 100. */
  edgePct: number;
  /** Win probability the book price implies, in percent: 100 ÷ odds (margin included). */
  impliedPct: number;
}

/**
 * Our price against the book's, for picks whose score is a win probability in percent.
 * Null when either side is missing or out of range, so callers can simply skip the line.
 */
export function comparePrice(scorePct: number | null | undefined, odds: number | null | undefined): PriceComparison | null {
  if (scorePct == null || odds == null) return null;
  if (!(scorePct > 0 && scorePct < 100) || !(odds >= MIN_COMPARABLE_ODDS)) return null;
  return {
    fairOdds: 100 / scorePct,
    edgePct: scorePct * odds - 100,
    impliedPct: 100 / odds,
  };
}

export function formatEdge(edgePct: number): string {
  const rounded = Math.round(edgePct);
  return `${rounded > 0 ? '+' : ''}${rounded}%`;
}
