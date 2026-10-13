import { describe, expect, it } from 'vitest';
import type { PerformanceBucket } from '../types';
import { formatTrackRecord } from './trackRecord';

function bucket(overrides: Partial<PerformanceBucket> = {}): PerformanceBucket {
  return {
    wins: 77,
    losses: 43,
    voids: 0,
    pending: 0,
    unsupported: 0,
    hitRate: 64.2,
    sampleSize: 120,
    enoughData: true,
    pricedSample: 110,
    avgOdds: 1.65,
    breakEvenRate: 60.6,
    profitUnits: 3.4,
    roi: 3.09,
    ...overrides,
  };
}

describe('formatTrackRecord', () => {
  it('shows hit rate, sample and ROI', () => {
    expect(formatTrackRecord(bucket())).toBe('Last 30 days: 64% hit · 120 picks · ROI +3.1%');
  });

  it('leaves ROI out when too few picks were priced', () => {
    expect(formatTrackRecord(bucket({ pricedSample: 4 }))).toBe('Last 30 days: 64% hit · 120 picks');
    expect(formatTrackRecord(bucket({ roi: null }))).toBe('Last 30 days: 64% hit · 120 picks');
  });

  it('shows negative ROI unsigned by us', () => {
    expect(formatTrackRecord(bucket({ roi: -4.25 }))).toContain('ROI -4.3%');
  });

  it('shows nothing on a thin or missing sample', () => {
    expect(formatTrackRecord(bucket({ enoughData: false }))).toBeNull();
    expect(formatTrackRecord(undefined)).toBeNull();
  });
});
