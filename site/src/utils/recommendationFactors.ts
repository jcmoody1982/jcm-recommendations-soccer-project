/** Keys that are internal engine flags and rarely help tip reading. */
const SKIP_KEYS = new Set([
  'missingDataRenormalized',
  'cardsPotentialIsCardCount',
  'refereeRequiredForStrong',
  'highCardsBoostApplied',
  'refereeStrictnessBoostApplied',
  'boostCapped',
  'signalsUsed',
  'formDataAvailable',
  'refereeDataAvailable',
]);

const MAX_FACTORS = 8;

export interface FactorEntry {
  label: string;
  value: string;
}

export function humanizeFactorKey(key: string): string {
  return key
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replace(/_/g, ' ')
    .replace(/\bPct\b/gi, '%')
    .replace(/\bAvg\b/gi, 'avg')
    .replace(/\bId\b/gi, 'ID')
    .replace(/^./, (c) => c.toUpperCase());
}

function formatFactorValue(value: unknown): string | null {
  if (value == null) return null;
  if (typeof value === 'boolean') return value ? 'Yes' : 'No';
  if (typeof value === 'number') {
    if (!Number.isFinite(value)) return null;
    if (Number.isInteger(value)) return String(value);
    return Math.abs(value) >= 10 ? value.toFixed(1) : value.toFixed(2);
  }
  if (typeof value === 'string') {
    const trimmed = value.trim();
    return trimmed || null;
  }
  if (Array.isArray(value)) {
    if (value.length === 0) return null;
    return value.map((v) => formatFactorValue(v) ?? String(v)).join(', ');
  }
  if (typeof value === 'object') {
    try {
      return JSON.stringify(value);
    } catch {
      return null;
    }
  }
  return String(value);
}

/**
 * Turns a recommendation factors map into a short, readable list for the UI.
 */
export function formatFactorEntries(
  factors: Record<string, unknown> | null | undefined,
  limit: number = MAX_FACTORS
): FactorEntry[] {
  if (!factors) return [];

  const entries: FactorEntry[] = [];
  for (const [key, raw] of Object.entries(factors)) {
    if (SKIP_KEYS.has(key)) continue;
    if (typeof raw === 'boolean' && raw === false) continue;
    const value = formatFactorValue(raw);
    if (value == null) continue;
    entries.push({ label: humanizeFactorKey(key), value });
    if (entries.length >= limit) break;
  }
  return entries;
}
