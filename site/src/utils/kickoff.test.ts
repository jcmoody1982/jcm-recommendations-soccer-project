import { describe, expect, it } from 'vitest';
import {
  compareByKickoff,
  compareByScoreDesc,
  formatKickoffDisplay,
  isEarlyKickoffUk,
  matchesKickoffWindow,
  toKickoffDate,
} from './kickoff';

// Tests run with TZ=Europe/London (see package.json), so "local day" is the UK day.
const NOW = new Date(2026, 9, 10, 14, 0).getTime(); // Sat 10 Oct 2026 14:00
const unix = (date: Date) => Math.floor(date.getTime() / 1000);
const at = (day: number, hour: number, minute = 0) => unix(new Date(2026, 9, day, hour, minute));

describe('matchesKickoffWindow', () => {
  it('passes everything for "all"', () => {
    expect(matchesKickoffWindow(0, 'all', NOW)).toBe(true);
  });

  it('"soon" covers the next three hours inclusive and excludes past kickoffs', () => {
    expect(matchesKickoffWindow(at(10, 14), 'soon', NOW)).toBe(true);
    expect(matchesKickoffWindow(at(10, 17), 'soon', NOW)).toBe(true);
    expect(matchesKickoffWindow(at(10, 17, 1), 'soon', NOW)).toBe(false);
    expect(matchesKickoffWindow(at(10, 13, 59), 'soon', NOW)).toBe(false);
  });

  it('"today" is the local calendar day, including earlier kickoffs', () => {
    expect(matchesKickoffWindow(at(10, 0), 'today', NOW)).toBe(true);
    expect(matchesKickoffWindow(at(10, 23, 59), 'today', NOW)).toBe(true);
    expect(matchesKickoffWindow(at(11, 0), 'today', NOW)).toBe(false);
  });

  it('"tomorrow" is the next local calendar day only', () => {
    expect(matchesKickoffWindow(at(11, 0), 'tomorrow', NOW)).toBe(true);
    expect(matchesKickoffWindow(at(11, 23, 59), 'tomorrow', NOW)).toBe(true);
    expect(matchesKickoffWindow(at(12, 0), 'tomorrow', NOW)).toBe(false);
    expect(matchesKickoffWindow(at(10, 23), 'tomorrow', NOW)).toBe(false);
  });

  it('handles the BST to GMT change at the end of October', () => {
    const lateOct = new Date(2026, 9, 24, 20, 0).getTime(); // Sat before clocks go back
    expect(matchesKickoffWindow(unix(new Date(2026, 9, 25, 23, 30)), 'tomorrow', lateOct)).toBe(true);
    expect(matchesKickoffWindow(unix(new Date(2026, 9, 26, 0, 0)), 'tomorrow', lateOct)).toBe(false);
  });
});

describe('formatKickoffDisplay', () => {
  it('marks past kickoffs as started', () => {
    expect(formatKickoffDisplay(at(10, 13), NOW).urgency).toBe('started');
  });

  it('counts down in minutes, then hours and minutes, within three hours', () => {
    expect(formatKickoffDisplay(at(10, 14, 45), NOW)).toMatchObject({ urgency: 'soon', primaryLabel: 'in 45m' });
    expect(formatKickoffDisplay(at(10, 16, 30), NOW)).toMatchObject({ urgency: 'soon', primaryLabel: 'in 2h 30m' });
    expect(formatKickoffDisplay(unix(new Date(NOW + 10_000)), NOW).primaryLabel).toBe('in 1m');
  });

  it('labels later today, tomorrow and beyond', () => {
    expect(formatKickoffDisplay(at(10, 20), NOW)).toMatchObject({ urgency: 'today', primaryLabel: 'Today', timeLabel: '20:00' });
    expect(formatKickoffDisplay(at(11, 15), NOW)).toMatchObject({ urgency: 'tomorrow', primaryLabel: 'Tomorrow' });
    const later = formatKickoffDisplay(at(13, 19, 45), NOW);
    expect(later.urgency).toBe('later');
    expect(later.primaryLabel).toBe('Tue 13 Oct');
    expect(later.title).toBe('Tue 13 Oct 19:45');
  });
});

describe('toKickoffDate', () => {
  const instant = new Date(Date.UTC(2026, 9, 10, 12, 0));

  it('accepts unix seconds, unix ms and numeric strings', () => {
    expect(toKickoffDate(instant.getTime() / 1000)?.getTime()).toBe(instant.getTime());
    expect(toKickoffDate(instant.getTime())?.getTime()).toBe(instant.getTime());
    expect(toKickoffDate(String(instant.getTime() / 1000))?.getTime()).toBe(instant.getTime());
  });

  it('accepts ISO strings, Dates and Jackson arrays', () => {
    expect(toKickoffDate('2026-10-10T12:00:00Z')?.getTime()).toBe(instant.getTime());
    expect(toKickoffDate(instant)).toBe(instant);
    expect(toKickoffDate([instant.getTime() / 1000, 0])?.getTime()).toBe(instant.getTime());
    expect(toKickoffDate([2026, 10, 10, 12, 0])?.getTime()).toBe(instant.getTime());
  });

  it('rejects junk', () => {
    expect(toKickoffDate(null)).toBeNull();
    expect(toKickoffDate('')).toBeNull();
    expect(toKickoffDate('not a date')).toBeNull();
    expect(toKickoffDate(Number.NaN)).toBeNull();
    expect(toKickoffDate(new Date('invalid'))).toBeNull();
    expect(toKickoffDate({})).toBeNull();
  });
});

describe('isEarlyKickoffUk', () => {
  it('flags 08:00–12:30 UK time inclusive in BST', () => {
    expect(isEarlyKickoffUk('2026-10-10T07:00:00Z')).toBe(true); // 08:00 BST
    expect(isEarlyKickoffUk('2026-10-10T11:30:00Z')).toBe(true); // 12:30 BST
    expect(isEarlyKickoffUk('2026-10-10T11:31:00Z')).toBe(false);
    expect(isEarlyKickoffUk('2026-10-10T06:59:00Z')).toBe(false);
  });

  it('follows GMT after the clocks change', () => {
    expect(isEarlyKickoffUk('2026-11-07T12:30:00Z')).toBe(true); // 12:30 GMT
    expect(isEarlyKickoffUk('2026-11-07T07:30:00Z')).toBe(false); // 07:30 GMT
  });

  it('is false for unparseable input', () => {
    expect(isEarlyKickoffUk(undefined)).toBe(false);
  });
});

describe('comparators', () => {
  it('sorts kickoff ascending and score descending', () => {
    expect([3, 1, 2].sort(compareByKickoff)).toEqual([1, 2, 3]);
    expect([70, 90, 80].sort(compareByScoreDesc)).toEqual([90, 80, 70]);
  });
});
