export type KickoffWindow = 'all' | 'soon' | 'today' | 'tomorrow';
export type KickoffSort = 'score' | 'kickoff';

const SOON_MS = 3 * 60 * 60 * 1000;
const LONDON = 'Europe/London';

/** Calendar day key in Europe/London, e.g. "2026-08-15". */
export function londonDayKey(ms: number = Date.now()): string {
  return new Date(ms).toLocaleDateString('en-CA', { timeZone: LONDON });
}

function addCalendarDays(dayKey: string, days: number): string {
  const [year, month, day] = dayKey.split('-').map(Number);
  const utcNoon = Date.UTC(year, month - 1, day + days, 12, 0, 0);
  return new Date(utcNoon).toISOString().slice(0, 10);
}

export function getKickoffMs(matchDateUnix: number): number {
  return matchDateUnix * 1000;
}

export function matchesKickoffWindow(
  matchDateUnix: number,
  window: KickoffWindow,
  nowMs: number = Date.now()
): boolean {
  if (window === 'all') return true;

  const kickoffMs = getKickoffMs(matchDateUnix);

  if (window === 'soon') {
    return kickoffMs >= nowMs && kickoffMs <= nowMs + SOON_MS;
  }

  const kickoffDay = londonDayKey(kickoffMs);
  const today = londonDayKey(nowMs);

  if (window === 'today') {
    return kickoffDay === today;
  }

  // tomorrow
  return kickoffDay === addCalendarDays(today, 1);
}

export type KickoffUrgency = 'started' | 'soon' | 'today' | 'tomorrow' | 'later';

export interface KickoffDisplay {
  urgency: KickoffUrgency;
  /** Short label for the date column, e.g. "in 2h", "Today", "Sat 2 Aug" */
  primaryLabel: string;
  /** Time of day in Europe/London, e.g. "15:00" */
  timeLabel: string;
  /** Full accessible description */
  title: string;
}

export function formatKickoffDisplay(
  matchDateUnix: number,
  nowMs: number = Date.now()
): KickoffDisplay {
  const kickoffMs = getKickoffMs(matchDateUnix);
  const matchDate = new Date(kickoffMs);
  const diffMs = kickoffMs - nowMs;

  const timeLabel = matchDate.toLocaleTimeString('en-GB', {
    timeZone: LONDON,
    hour: '2-digit',
    minute: '2-digit',
  });

  const absoluteDate = matchDate.toLocaleDateString('en-GB', {
    timeZone: LONDON,
    weekday: 'short',
    day: 'numeric',
    month: 'short',
  });

  const title = `${absoluteDate} ${timeLabel} (UK)`;

  if (diffMs < 0) {
    return {
      urgency: 'started',
      primaryLabel: 'Started',
      timeLabel,
      title,
    };
  }

  if (diffMs <= SOON_MS) {
    const minutes = Math.max(1, Math.round(diffMs / 60000));
    const primaryLabel =
      minutes < 60
        ? `in ${minutes}m`
        : `in ${Math.floor(minutes / 60)}h ${minutes % 60}m`;
    return {
      urgency: 'soon',
      primaryLabel,
      timeLabel,
      title,
    };
  }

  const kickoffDay = londonDayKey(kickoffMs);
  const today = londonDayKey(nowMs);
  const tomorrow = addCalendarDays(today, 1);

  if (kickoffDay === today) {
    return {
      urgency: 'today',
      primaryLabel: 'Today',
      timeLabel,
      title,
    };
  }

  if (kickoffDay === tomorrow) {
    return {
      urgency: 'tomorrow',
      primaryLabel: 'Tomorrow',
      timeLabel,
      title,
    };
  }

  return {
    urgency: 'later',
    primaryLabel: absoluteDate,
    timeLabel,
    title,
  };
}

export function compareByKickoff(aUnix: number, bUnix: number): number {
  return aUnix - bUnix;
}

export function compareByScoreDesc(aScore: number, bScore: number): number {
  return (bScore || 0) - (aScore || 0);
}
