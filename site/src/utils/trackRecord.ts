import type { PerformanceBucket } from '../types';

/** Same floor the performance API uses for `enoughData`; ROI on fewer priced picks is noise. */
const MIN_PRICED_SAMPLE = 10;

/**
 * One-line record for a board header, e.g. "Last 30 days: 64% hit · 120 picks · ROI +3.1%".
 * Null when the sample is too small to mean anything, so thin markets show nothing rather than
 * a flattering or alarming number.
 */
export function formatTrackRecord(bucket: PerformanceBucket | null | undefined, periodLabel = 'Last 30 days'): string | null {
  if (!bucket?.enoughData || bucket.hitRate == null) return null;
  const parts = [
    `${periodLabel}: ${bucket.hitRate.toFixed(0)}% hit`,
    `${bucket.sampleSize} pick${bucket.sampleSize === 1 ? '' : 's'}`,
  ];
  if (bucket.roi != null && bucket.pricedSample >= MIN_PRICED_SAMPLE) {
    parts.push(`ROI ${bucket.roi > 0 ? '+' : ''}${bucket.roi.toFixed(1)}%`);
  }
  return parts.join(' · ');
}
