export type KickoffWindow = 'all' | 'soon' | 'today' | 'tomorrow';
export type KickoffSort = 'score' | 'kickoff';

const SOON_MS = 3 * 60 * 60 * 1000;

function startOfLocalDay(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

function addLocalDays(date: Date, days: number): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() + days);
}

export function getKickoffMs(matchDateUnix: number): number {
  return matchDateUnix * 1000;
}

/** Kickoffs at or before 12:30 Europe/London are treated as early. */
export const EARLY_KICKOFF_UK_MINUTES = 12 * 60 + 30;

export const EARLY_KICKOFF_WARNING =
  'Early Kick-Off — Proceed with Extreme Caution';

/**
 * True when the fixture kicks off at or before 12:30 UK time
 * (Europe/London, including BST/GMT).
 */
export function isEarlyKickoffUk(matchDateUnix: number): boolean {
  const date = new Date(getKickoffMs(matchDateUnix));
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Europe/London',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(date);

  const hour = Number(parts.find((part) => part.type === 'hour')?.value);
  const minute = Number(parts.find((part) => part.type === 'minute')?.value);
  if (!Number.isFinite(hour) || !Number.isFinite(minute)) {
    return false;
  }
  return hour * 60 + minute <= EARLY_KICKOFF_UK_MINUTES;
}

export function matchesKickoffWindow(
  matchDateUnix: number,
  window: KickoffWindow,
  nowMs: number = Date.now()
): boolean {
  if (window === 'all') return true;

  const kickoffMs = getKickoffMs(matchDateUnix);
  const now = new Date(nowMs);
  const todayStart = startOfLocalDay(now);
  const tomorrowStart = addLocalDays(todayStart, 1);
  const dayAfterStart = addLocalDays(todayStart, 2);

  if (window === 'soon') {
    return kickoffMs >= nowMs && kickoffMs <= nowMs + SOON_MS;
  }

  if (window === 'today') {
    return kickoffMs >= todayStart.getTime() && kickoffMs < tomorrowStart.getTime();
  }

  // tomorrow
  return kickoffMs >= tomorrowStart.getTime() && kickoffMs < dayAfterStart.getTime();
}

export type KickoffUrgency = 'started' | 'soon' | 'today' | 'tomorrow' | 'later';

export interface KickoffDisplay {
  urgency: KickoffUrgency;
  /** Short label for the date column, e.g. "in 2h", "Today", "Sat 2 Aug" */
  primaryLabel: string;
  /** Time of day in the browser local timezone, e.g. "15:00" */
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
    hour: '2-digit',
    minute: '2-digit',
  });

  const absoluteDate = matchDate.toLocaleDateString('en-GB', {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
  });

  const title = `${absoluteDate} ${timeLabel}`;

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

  const now = new Date(nowMs);
  const todayStart = startOfLocalDay(now);
  const tomorrowStart = addLocalDays(todayStart, 1);
  const dayAfterStart = addLocalDays(todayStart, 2);

  if (kickoffMs >= todayStart.getTime() && kickoffMs < tomorrowStart.getTime()) {
    return {
      urgency: 'today',
      primaryLabel: 'Today',
      timeLabel,
      title,
    };
  }

  if (kickoffMs >= tomorrowStart.getTime() && kickoffMs < dayAfterStart.getTime()) {
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
