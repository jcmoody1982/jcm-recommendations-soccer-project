import { useCallback, useEffect, useMemo } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { resultsService } from '../services/api';
import { EliteBolt } from '../components';
import { SECTION_ORDER, sectionTitle } from '../utils/recommendationSections';
import type {
  DayResults,
  PerformanceBucket,
  PerformancePeriod,
  PickOutcome,
  ResultsDaySummary,
  ResultsFixture,
  ResultsPerformance,
  ResultsPick,
  TypePerformance,
} from '../types';
import styles from './Results.module.css';

const OUTCOME_FILTERS: Array<PickOutcome | 'ALL'> = [
  'ALL',
  'WIN',
  'LOSS',
  'VOID',
  'PENDING',
  'UNSUPPORTED',
];

const PERIODS: PerformancePeriod[] = ['7d', '30d', '90d', 'all'];
const OUTCOME_SET = new Set<string>(OUTCOME_FILTERS);
const PERIOD_SET = new Set<string>(PERIODS);

type ConfidenceBand = 'STRONG' | 'MODERATE';
type ResultsView = 'day' | 'performance' | 'elite';

function londonToday(): string {
  return new Date().toLocaleDateString('en-CA', { timeZone: 'Europe/London' });
}

function formatKickoff(unix: number | null | undefined): string {
  if (!unix) return '';
  return new Date(unix * 1000).toLocaleString('en-GB', {
    timeZone: 'Europe/London',
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function formatType(type: string): string {
  return sectionTitle(type);
}

function orderTypeRows(rows: TypePerformance[]): TypePerformance[] {
  const byType = new Map(rows.map((row) => [row.type, row]));
  const ordered: TypePerformance[] = [];
  for (const type of SECTION_ORDER) {
    const row = byType.get(type);
    if (row) {
      ordered.push(row);
      byType.delete(type);
    }
  }
  for (const row of rows) {
    if (byType.has(row.type)) {
      ordered.push(row);
      byType.delete(row.type);
    }
  }
  return ordered;
}

function outcomeLabel(outcome: string): string {
  return outcome.charAt(0) + outcome.slice(1).toLowerCase();
}

function formatScore(score: number | null | undefined): string {
  if (score == null || Number.isNaN(score)) return '—';
  return Number.isInteger(score) ? String(score) : score.toFixed(1);
}

function formatPrice(odds: number | null | undefined): string | null {
  if (odds == null || Number.isNaN(odds) || odds <= 0) return null;
  return odds.toFixed(2);
}

const LEVEL_STAKE_USD = 10;
const COMBINATION_STAKE_USD = 1;

const FOLD_NAMES = [
  '',
  '',
  'double',
  'treble',
  'four-fold',
  'five-fold',
  'six-fold',
  'seven-fold',
  'eight-fold',
  'nine-fold',
  'ten-fold',
] as const;

type PricedPick = ResultsPick & { odds: number };
type ReturnAmount =
  | { status: 'ready'; amount: number }
  | { status: 'pending' }
  | { status: 'na' };

interface CombinationCoverLine {
  betCount: number;
  mixLabel: string;
  amount: ReturnAmount;
  detail?: string;
}

interface ReturnColumnStats {
  singlesCount: number;
  singlesAmount: ReturnAmount;
  singlesDetail?: string;
  accumulatorAmount: ReturnAmount;
  combinationCover: CombinationCoverLine | null;
}

function flattenPicks(fixtures: ResultsFixture[]): ResultsPick[] {
  return fixtures.flatMap((fixture) => fixture.picks);
}

function isPricedOdds(odds: number | null | undefined): odds is number {
  return odds != null && !Number.isNaN(odds) && odds > 0;
}

function pricedPicksOf(picks: ResultsPick[]): PricedPick[] {
  return picks.filter((pick): pick is PricedPick => isPricedOdds(pick.odds));
}

function formatUsd(amount: number): string {
  return amount.toLocaleString('en-US', {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

function formatReturnAmount(amount: ReturnAmount): string {
  if (amount.status === 'pending') return 'Pending';
  if (amount.status === 'na') return '—';
  return formatUsd(amount.amount);
}

function combinations(n: number, k: number): number {
  if (k < 0 || k > n) return 0;
  const size = Math.min(k, n - k);
  let result = 1;
  for (let i = 1; i <= size; i += 1) {
    result = (result * (n - size + i)) / i;
  }
  return Math.round(result);
}

function foldName(size: number, count: number): string {
  const singular = FOLD_NAMES[size] ?? `${size}-fold`;
  return `${count} ${count === 1 ? singular : `${singular}s`}`;
}

function combinationMix(n: number): { betCount: number; mixLabel: string } | null {
  if (n < 2) return null;
  const mix = [];
  for (let size = 2; size <= n; size += 1) {
    mix.push(foldName(size, combinations(n, size)));
  }
  return { betCount: 2 ** n - 1 - n, mixLabel: mix.join(', ') };
}

function potentialCombinationCover(odds: number[], stake = COMBINATION_STAKE_USD): CombinationCoverLine | null {
  const mix = combinationMix(odds.length);
  if (!mix) return null;
  const productPlusOne = odds.reduce((product, price) => product * (1 + price), 1);
  const singlesSum = odds.reduce((sum, price) => sum + price, 0);
  return {
    ...mix,
    amount: { status: 'ready', amount: stake * (productPlusOne - 1 - singlesSum) },
  };
}

function isOpenOutcome(outcome: PickOutcome | null | undefined): boolean {
  return outcome == null || outcome === 'PENDING' || outcome === 'UNSUPPORTED';
}

/** Win/loss/void settlement for a single fold. A lost leg settles the bet at $0 even if others are pending. */
function settleFold(legs: PricedPick[], stake: number): ReturnAmount {
  if (legs.length === 0) return { status: 'na' };
  if (legs.some((leg) => leg.outcome === 'LOSS')) {
    return { status: 'ready', amount: 0 };
  }
  if (legs.some((leg) => isOpenOutcome(leg.outcome))) {
    return { status: 'pending' };
  }
  const winners = legs.filter((leg) => leg.outcome === 'WIN');
  if (winners.length === 0) {
    return { status: 'ready', amount: stake };
  }
  const product = winners.reduce((total, leg) => total * leg.odds, 1);
  return { status: 'ready', amount: stake * product };
}

function actualSingles(picks: PricedPick[], stake: number): { amount: ReturnAmount; pending: number } {
  let total = 0;
  let pending = 0;
  let settled = 0;
  for (const pick of picks) {
    const result = settleFold([pick], stake);
    if (result.status === 'pending') {
      pending += 1;
    } else if (result.status === 'ready') {
      settled += 1;
      total += result.amount;
    }
  }
  if (picks.length === 0) return { amount: { status: 'na' }, pending };
  if (settled === 0 && pending > 0) return { amount: { status: 'pending' }, pending };
  return { amount: { status: 'ready', amount: total }, pending };
}

function actualCombinationCover(picks: PricedPick[], stake = COMBINATION_STAKE_USD): CombinationCoverLine | null {
  const mix = combinationMix(picks.length);
  if (!mix) return null;

  let settledReturn = 0;
  let pendingBets = 0;
  let settledBets = 0;
  const n = picks.length;
  const limit = 1 << n;
  for (let mask = 0; mask < limit; mask += 1) {
    const subset: PricedPick[] = [];
    for (let i = 0; i < n; i += 1) {
      if (mask & (1 << i)) subset.push(picks[i]);
    }
    if (subset.length < 2) continue;
    const result = settleFold(subset, stake);
    if (result.status === 'pending') {
      pendingBets += 1;
    } else if (result.status === 'ready') {
      settledBets += 1;
      settledReturn += result.amount;
    }
  }

  const amount: ReturnAmount =
    settledBets === 0 && pendingBets > 0
      ? { status: 'pending' }
      : { status: 'ready', amount: settledReturn };

  return {
    ...mix,
    amount,
    detail: pendingBets > 0 ? `${pendingBets} still pending` : undefined,
  };
}

function computeReturns(picks: ResultsPick[]) {
  const priced = pricedPicksOf(picks);
  const odds = priced.map((pick) => pick.odds);
  const singles = actualSingles(priced, LEVEL_STAKE_USD);
  const combinedOdds = odds.reduce((product, price) => product * price, 1);

  const potential: ReturnColumnStats = {
    singlesCount: priced.length,
    singlesAmount: priced.length > 0
      ? { status: 'ready', amount: odds.reduce((total, price) => total + LEVEL_STAKE_USD * price, 0) }
      : { status: 'na' },
    accumulatorAmount: priced.length > 0
      ? { status: 'ready', amount: LEVEL_STAKE_USD * combinedOdds }
      : { status: 'na' },
    combinationCover: potentialCombinationCover(odds),
  };

  const actual: ReturnColumnStats = {
    singlesCount: priced.length,
    singlesAmount: singles.amount,
    singlesDetail: singles.pending > 0 ? `${singles.pending} still pending` : undefined,
    accumulatorAmount: settleFold(priced, LEVEL_STAKE_USD),
    combinationCover: actualCombinationCover(priced),
  };

  return {
    priced: priced.length,
    unpriced: picks.length - priced.length,
    potential,
    actual,
  };
}

function formatHitRate(summary?: { hitRate: number | null } | null): string {
  if (summary?.hitRate == null) return '—';
  return `${summary.hitRate.toFixed(0)}%`;
}

function formatBucketHit(bucket?: PerformanceBucket | null): string {
  if (!bucket) return '—';
  if (!bucket.enoughData) return 'n/a';
  if (bucket.hitRate == null) return '—';
  return `${bucket.hitRate.toFixed(0)}%`;
}

function hitBadgeClass(bucket?: PerformanceBucket | null): string {
  if (!bucket?.enoughData || bucket.hitRate == null) return styles.hitSparse;
  if (bucket.hitRate >= 55) return styles.hitGood;
  return styles.hitLow;
}

function parseView(value: string | null): ResultsView {
  if (value === 'performance') return 'performance';
  if (value === 'elite') return 'elite';
  return 'day';
}

function parsePeriod(value: string | null): PerformancePeriod {
  if (value && PERIOD_SET.has(value)) return value as PerformancePeriod;
  return '30d';
}

function parseOutcome(value: string | null): PickOutcome | 'ALL' {
  if (value && OUTCOME_SET.has(value)) return value as PickOutcome | 'ALL';
  return 'ALL';
}

function confidenceClass(confidence: string | null | undefined): string {
  const band = confidence?.toUpperCase();
  if (band === 'STRONG') return styles.confidenceStrong;
  if (band === 'MODERATE') return styles.confidenceModerate;
  return styles.confidenceOther;
}

function fixturesForConfidence(
  fixtures: ResultsFixture[],
  confidence: ConfidenceBand,
  typeFilter: string,
): ResultsFixture[] {
  return fixtures
    .map((fixture) => ({
      ...fixture,
      picks: fixture.picks.filter((pick) => {
        if (pick.confidence?.toUpperCase() !== confidence) return false;
        if (typeFilter !== 'ALL' && pick.type !== typeFilter) return false;
        return true;
      }),
    }))
    .filter((fixture) => fixture.picks.length > 0);
}

function collectTypes(fixtures: ResultsFixture[]): string[] {
  const types = new Set<string>();
  for (const fixture of fixtures) {
    for (const pick of fixture.picks) {
      if (pick.type) types.add(pick.type);
    }
  }
  return Array.from(types).sort((a, b) => a.localeCompare(b));
}

function summaryFromFixtures(fixtures: ResultsFixture[]): ResultsDaySummary {
  let wins = 0;
  let losses = 0;
  let voids = 0;
  let pending = 0;
  let unsupported = 0;

  for (const fixture of fixtures) {
    for (const pick of fixture.picks) {
      switch (pick.outcome) {
        case 'WIN':
          wins += 1;
          break;
        case 'LOSS':
          losses += 1;
          break;
        case 'VOID':
          voids += 1;
          break;
        case 'PENDING':
          pending += 1;
          break;
        default:
          unsupported += 1;
          break;
      }
    }
  }

  const graded = wins + losses;
  return {
    wins,
    losses,
    voids,
    pending,
    unsupported,
    hitRate: graded > 0 ? (wins * 100) / graded : null,
  };
}

type OutcomeCounts = {
  wins: number;
  losses: number;
  voids: number;
  pending: number;
  unsupported: number;
  hitRate: number | null;
};

function buildOutcomeGradient(summary: OutcomeCounts): string {
  const segments = [
    { value: summary.wins, color: 'var(--confidence-strong)' },
    { value: summary.losses, color: '#e07070' },
    { value: summary.voids, color: 'var(--confidence-moderate)' },
    { value: summary.pending, color: 'var(--accent-color)' },
    { value: summary.unsupported, color: 'var(--text-muted)' },
  ].filter((segment) => segment.value > 0);

  const total = segments.reduce((sum, segment) => sum + segment.value, 0);
  if (total === 0) {
    return 'conic-gradient(var(--border-color) 0deg 360deg)';
  }

  let angle = 0;
  const stops = segments.map((segment) => {
    const start = angle;
    angle += (segment.value / total) * 360;
    return `${segment.color} ${start}deg ${angle}deg`;
  });
  return `conic-gradient(${stops.join(', ')})`;
}

function outcomeBreakdownLabel(summary: OutcomeCounts): string {
  const parts = [
    summary.wins > 0 ? `${summary.wins} win${summary.wins === 1 ? '' : 's'}` : '',
    summary.losses > 0 ? `${summary.losses} loss${summary.losses === 1 ? '' : 'es'}` : '',
    summary.voids > 0 ? `${summary.voids} void${summary.voids === 1 ? '' : 's'}` : '',
    summary.pending > 0 ? `${summary.pending} pending` : '',
    summary.unsupported > 0 ? `${summary.unsupported} unsupported` : '',
  ].filter(Boolean);
  return parts.length > 0 ? parts.join(' · ') : 'No picks';
}

function OutcomeDonut({
  summary,
  size = 'sm',
}: {
  summary: OutcomeCounts;
  size?: 'sm' | 'md';
}) {
  const total = summary.wins + summary.losses + summary.voids + summary.pending + summary.unsupported;
  const centerLabel =
    summary.hitRate != null ? `${summary.hitRate.toFixed(0)}%` : total > 0 ? '—' : '0';

  return (
    <div
      className={`${styles.outcomeDonut} ${size === 'md' ? styles.outcomeDonutMd : ''}`}
      style={{ background: buildOutcomeGradient(summary) }}
      title={outcomeBreakdownLabel(summary)}
      aria-label={`Outcome mix: ${outcomeBreakdownLabel(summary)}`}
      role="img"
    >
      <span className={styles.outcomeDonutCenter}>{centerLabel}</span>
    </div>
  );
}

function SummaryStrip({
  title,
  summary,
  tone,
  compact = false,
}: {
  title: string;
  summary: ResultsDaySummary;
  tone: 'strong' | 'moderate' | 'all';
  compact?: boolean;
}) {
  if (compact) {
    return (
      <div className={`${styles.compactSummary} ${styles[`summary${tone[0].toUpperCase()}${tone.slice(1)}`]}`}>
        <OutcomeDonut summary={summary} />
        <h2 className={styles.compactSummaryTitle}>{title}</h2>
        <span className={styles.compactStats}>
          {summary.wins}W · {summary.losses}L · {summary.voids}V · {summary.pending}P
          {summary.unsupported > 0 ? ` · ${summary.unsupported}U` : ''}
        </span>
      </div>
    );
  }

  return (
    <section className={`${styles.summaryPanel} ${styles[`summary${tone[0].toUpperCase()}${tone.slice(1)}`]}`}>
      <div className={styles.summaryHeading}>
        <h2 className={styles.summaryTitle}>{title}</h2>
        <span className={styles.summaryHitRate}>{formatHitRate(summary)}</span>
      </div>
      <div className={styles.summaryStrip}>
        <div className={styles.summaryStat}>
          <span className={`${styles.summaryValue} ${styles.win}`}>{summary.wins}</span>
          <span className={styles.summaryLabel}>Wins</span>
        </div>
        <div className={styles.summaryStat}>
          <span className={`${styles.summaryValue} ${styles.loss}`}>{summary.losses}</span>
          <span className={styles.summaryLabel}>Losses</span>
        </div>
        <div className={styles.summaryStat}>
          <span className={styles.summaryValue}>{summary.voids}</span>
          <span className={styles.summaryLabel}>Voids</span>
        </div>
        <div className={styles.summaryStat}>
          <span className={styles.summaryValue}>{summary.pending}</span>
          <span className={styles.summaryLabel}>Pending</span>
        </div>
        <div className={styles.summaryStat}>
          <span className={`${styles.summaryValue} ${styles.unsupported}`}>{summary.unsupported}</span>
          <span className={styles.summaryLabel}>Unsupported</span>
        </div>
      </div>
    </section>
  );
}

function PerformanceSummary({
  title,
  bucket,
  tone,
}: {
  title: string;
  bucket: PerformanceBucket;
  tone: 'strong' | 'moderate' | 'all';
}) {
  return (
    <section className={`${styles.summaryPanel} ${styles[`summary${tone[0].toUpperCase()}${tone.slice(1)}`]}`}>
      <div className={styles.summaryHeading}>
        <div className={styles.summaryTitleRow}>
          <OutcomeDonut summary={bucket} size="md" />
          <h2 className={styles.summaryTitle}>{title}</h2>
        </div>
        <span className={styles.summaryHitRate}>{formatBucketHit(bucket)}</span>
      </div>
      <div className={styles.summaryStrip}>
        <div className={styles.summaryStat}>
          <span className={`${styles.summaryValue} ${styles.win}`}>{bucket.wins}</span>
          <span className={styles.summaryLabel}>Wins</span>
        </div>
        <div className={styles.summaryStat}>
          <span className={`${styles.summaryValue} ${styles.loss}`}>{bucket.losses}</span>
          <span className={styles.summaryLabel}>Losses</span>
        </div>
        <div className={styles.summaryStat}>
          <span className={styles.summaryValue}>{bucket.sampleSize}</span>
          <span className={styles.summaryLabel}>Graded</span>
        </div>
        <div className={styles.summaryStat}>
          <span className={styles.summaryValue}>{bucket.voids}</span>
          <span className={styles.summaryLabel}>Voids</span>
        </div>
        <div className={styles.summaryStat}>
          <span className={styles.summaryValue}>{bucket.enoughData ? 'OK' : '<10'}</span>
          <span className={styles.summaryLabel}>Sample</span>
        </div>
      </div>
    </section>
  );
}

function returnValueClass(amount: ReturnAmount): string {
  if (amount.status === 'pending' || amount.status === 'na') return styles.returnsValueMuted;
  if (amount.status === 'ready' && amount.amount === 0) return styles.returnsValueZero;
  return styles.returnsValue;
}

function CombinationReturnRow({ cover }: { cover: CombinationCoverLine | null }) {
  if (!cover) {
    return (
      <div className={styles.returnsRow}>
        <span className={styles.returnsLabelBlock}>
          <span className={styles.returnsLabel}>$1 doubles, trebles and above</span>
          <span className={styles.returnsDetail}>Needs at least two priced picks</span>
        </span>
        <span className={styles.returnsValueMuted}>—</span>
      </div>
    );
  }

  const totalStake = formatUsd(cover.betCount * COMBINATION_STAKE_USD);
  const detail = [cover.mixLabel, cover.detail].filter(Boolean).join(' · ');

  return (
    <div className={styles.returnsRow}>
      <span className={styles.returnsLabelBlock}>
        <span className={styles.returnsLabel}>
          $1 doubles, trebles and above. Total Stake: {totalStake}
        </span>
        <span className={styles.returnsDetail}>{detail}</span>
      </span>
      <span className={returnValueClass(cover.amount)}>{formatReturnAmount(cover.amount)}</span>
    </div>
  );
}

function ReturnsColumn({
  title,
  intro,
  stats,
}: {
  title: string;
  intro: string;
  stats: ReturnColumnStats;
}) {
  return (
    <div className={styles.returnsColumn}>
      <h3 className={styles.returnsColumnTitle}>{title}</h3>
      <p className={styles.returnsStatement}>{intro}</p>
      <div className={styles.returnsRows}>
        <div className={styles.returnsRow}>
          <span className={styles.returnsLabelBlock}>
            <span className={styles.returnsLabel}>
              {stats.singlesCount} × $10 singles
            </span>
            {stats.singlesDetail && (
              <span className={styles.returnsDetail}>{stats.singlesDetail}</span>
            )}
          </span>
          <span className={returnValueClass(stats.singlesAmount)}>
            {formatReturnAmount(stats.singlesAmount)}
          </span>
        </div>
        <div className={styles.returnsRow}>
          <span className={styles.returnsLabel}>$10 accumulator</span>
          <span className={returnValueClass(stats.accumulatorAmount)}>
            {formatReturnAmount(stats.accumulatorAmount)}
          </span>
        </div>
        <CombinationReturnRow cover={stats.combinationCover} />
      </div>
    </div>
  );
}

function ReturnsPanel({ fixtures }: { fixtures: ResultsFixture[] }) {
  const stats = computeReturns(flattenPicks(fixtures));

  return (
    <section className={styles.returnsPanel}>
      <h2 className={styles.returnsTitle}>Returns</h2>
      {stats.priced === 0 ? (
        <p className={styles.returnsStatement}>
          None of the Elite picks in this view have a known price, so returns
          cannot be calculated.
        </p>
      ) : (
        <div className={styles.returnsSplit}>
          <ReturnsColumn
            title="Actual Returns"
            intro="From wins, losses and voids in this view:"
            stats={stats.actual}
          />
          <ReturnsColumn
            title="Potential Returns"
            intro="If every priced Elite pick in this view had won:"
            stats={stats.potential}
          />
        </div>
      )}
      {stats.unpriced > 0 && stats.priced > 0 && (
        <p className={styles.returnsHint}>
          {stats.unpriced} pick{stats.unpriced === 1 ? '' : 's'} with no stored price
          {stats.unpriced === 1 ? ' is' : ' are'} excluded.
        </p>
      )}
    </section>
  );
}

function CompactPickRow({
  fixture,
  pick,
  showEliteRank,
  eliteKeys,
}: {
  fixture: ResultsFixture;
  pick: ResultsPick;
  showEliteRank: boolean;
  eliteKeys?: Set<string>;
}) {
  const price = formatPrice(pick.odds);
  const isElite =
    showEliteRank
    || pick.eliteRank != null
    || Boolean(eliteKeys?.has(`${fixture.fixtureId}:${pick.type}`));
  const score = fixture.scoreline
    ? `${fixture.scoreline.home}–${fixture.scoreline.away}`
    : fixture.matchStatus === 'incomplete'
      ? '…'
      : '—';
  const gridClass = showEliteRank ? styles.compactPickGridWithRank : styles.compactPickGrid;

  return (
    <li
      className={`${styles.compactPickRow} ${gridClass}${showEliteRank ? ` ${styles.compactPickRowWithRank}` : ''}`}
    >
      {showEliteRank && (
        <span className={styles.compactRank}>
          {pick.eliteRank != null ? pick.eliteRank : '—'}
        </span>
      )}
      <div className={styles.compactMatch}>
        <Link to={`/fixtures/${fixture.fixtureId}`} className={styles.compactMatchLink}>
          {fixture.homeTeamName} v {fixture.awayTeamName}
        </Link>
        {fixture.leagueName && (
          <span className={styles.compactLeague}>{fixture.leagueName}</span>
        )}
      </div>
      <span className={styles.compactScore}>{score}</span>
      <div className={styles.compactPick}>
        <span className={styles.compactPickInner}>
          {isElite && <EliteBolt variant="badge" size={14} />}
          <span>{pick.market}</span>
          {price && <span className={styles.compactPrice}>@{price}</span>}
        </span>
      </div>
      <span className={styles.compactType}>{formatType(pick.type)}</span>
      <div className={styles.compactOutcome}>
        <span className={`${styles.outcomeCompact} ${styles[`outcome${pick.outcome}`]}`}>
          {outcomeLabel(pick.outcome)}
        </span>
      </div>
      <div className={styles.compactMobileMeta}>
        <span className={styles.compactMobileScore}>{score}</span>
        <span className={styles.compactMobileType}>{formatType(pick.type)}</span>
      </div>
    </li>
  );
}

function CompactPickTable({
  fixtures,
  showEliteRank = false,
  eliteKeys,
}: {
  fixtures: ResultsFixture[];
  showEliteRank?: boolean;
  eliteKeys?: Set<string>;
}) {
  const rows = fixtures.flatMap((fixture) =>
    fixture.picks.map((pick) => ({ fixture, pick })),
  );

  if (rows.length === 0) return null;

  const gridClass = showEliteRank ? styles.compactPickGridWithRank : styles.compactPickGrid;

  return (
    <div className={styles.compactPickListWrap}>
      <div className={`${styles.compactPickHeader} ${gridClass}`} aria-hidden="true">
        {showEliteRank && <span>#</span>}
        <span>Match</span>
        <span>Scr</span>
        <span>Pick</span>
        <span>Type</span>
        <span>Result</span>
      </div>
      <ul className={styles.compactPickList}>
        {rows.map(({ fixture, pick }) => (
          <CompactPickRow
            key={pick.id}
            fixture={fixture}
            pick={pick}
            showEliteRank={showEliteRank}
            eliteKeys={eliteKeys}
          />
        ))}
      </ul>
    </div>
  );
}

function FixtureList({
  fixtures,
  emptyTitle,
  emptyBody,
  showEliteRank = false,
  eliteKeys,
}: {
  fixtures: ResultsFixture[];
  emptyTitle: string;
  emptyBody: string;
  showEliteRank?: boolean;
  eliteKeys?: Set<string>;
}) {
  if (fixtures.length === 0) {
    return (
      <div className={styles.sectionEmpty}>
        <h3>{emptyTitle}</h3>
        <p>{emptyBody}</p>
      </div>
    );
  }

  return (
    <div className={styles.fixtureList}>
      {fixtures.map((fixture) => (
        <section key={`${fixture.fixtureId}-${fixture.picks[0]?.id ?? 'x'}`} className={styles.fixtureBlock}>
          <header className={styles.fixtureHeader}>
            <div>
              <Link to={`/fixtures/${fixture.fixtureId}`} className={styles.fixtureTitle}>
                {fixture.homeTeamName} vs {fixture.awayTeamName}
              </Link>
              <div className={styles.fixtureMeta}>
                {fixture.leagueName && <span>{fixture.leagueName}</span>}
                {fixture.matchDateUnix != null && <span>{formatKickoff(fixture.matchDateUnix)}</span>}
              </div>
            </div>
            <div className={styles.scoreline}>
              {fixture.scoreline
                ? `${fixture.scoreline.home} – ${fixture.scoreline.away}`
                : fixture.matchStatus === 'incomplete'
                  ? 'Pending'
                  : '—'}
            </div>
          </header>

          <ul className={styles.pickList}>
            {fixture.picks.map((pick) => {
              const price = formatPrice(pick.odds);
              const isElite =
                showEliteRank
                || pick.eliteRank != null
                || Boolean(eliteKeys?.has(`${fixture.fixtureId}:${pick.type}`));
              return (
                <li key={pick.id} className={styles.pickRow}>
                  <div className={styles.pickMain}>
                    {showEliteRank && pick.eliteRank != null && (
                      <span className={styles.eliteRank} aria-label={`Elite rank ${pick.eliteRank}`}>
                        #{pick.eliteRank}
                      </span>
                    )}
                    <div className={styles.pickSelection}>
                      {isElite && <EliteBolt variant="badge" size={16} />}
                      <span className={styles.pickMarket}>{pick.market}</span>
                      {price && (
                        <span className={styles.pickPrice} title="Selection price">
                          @ {price}
                        </span>
                      )}
                    </div>
                    <div className={styles.pickMeta}>
                      <span className={styles.pickType}>{formatType(pick.type)}</span>
                      <span className={`${styles.confidenceBadge} ${confidenceClass(pick.confidence)}`}>
                        {pick.confidence ? outcomeLabel(pick.confidence) : '—'}
                      </span>
                      <span className={styles.pickScore} title="Model score">
                        {formatScore(pick.score)}
                      </span>
                    </div>
                  </div>
                  <div className={styles.pickSide}>
                    <span className={`${styles.outcome} ${styles[`outcome${pick.outcome}`]}`}>
                      {outcomeLabel(pick.outcome)}
                    </span>
                  </div>
                </li>
              );
            })}
          </ul>
        </section>
      ))}
    </div>
  );
}

function ConfidenceSection({
  title,
  tone,
  summary,
  fixtures,
  eliteKeys,
  showEliteRank = false,
  compact = false,
}: {
  title: string;
  tone: 'strong' | 'moderate';
  summary: ResultsDaySummary;
  fixtures: ResultsFixture[];
  eliteKeys?: Set<string>;
  showEliteRank?: boolean;
  compact?: boolean;
}) {
  if (fixtures.length === 0) return null;

  return (
    <section className={compact ? styles.confidenceSectionCompact : styles.confidenceSection}>
      <SummaryStrip title={title} summary={summary} tone={tone} compact={compact} />
      {compact ? (
        <CompactPickTable
          fixtures={fixtures}
          eliteKeys={eliteKeys}
          showEliteRank={showEliteRank}
        />
      ) : (
        <FixtureList
          fixtures={fixtures}
          emptyTitle={`No ${title.toLowerCase()} picks`}
          emptyBody={`No ${title.toLowerCase()} picks for this filter.`}
          eliteKeys={eliteKeys}
          showEliteRank={showEliteRank}
        />
      )}
    </section>
  );
}

function emptySummary(): ResultsDaySummary {
  return { wins: 0, losses: 0, voids: 0, pending: 0, unsupported: 0, hitRate: null };
}

function withConfidenceDefaults(data: DayResults | undefined): DayResults | undefined {
  if (!data) return data;
  return {
    ...data,
    strongSummary: data.strongSummary ?? emptySummary(),
    moderateSummary: data.moderateSummary ?? emptySummary(),
    eliteSummary: data.eliteSummary ?? emptySummary(),
    eliteFixtures: data.eliteFixtures ?? [],
  };
}

function formatBandHit(bucket: PerformanceBucket): string {
  return `${formatBucketHit(bucket)} (${bucket.sampleSize})`;
}

function TypePerformanceList({ rows }: { rows: TypePerformance[] }) {
  return (
    <div className={styles.typePerfListWrap}>
      <div className={styles.typePerfHeader} aria-hidden="true">
        <span>Type</span>
        <span>n</span>
        <span>W–L</span>
        <span>Hit</span>
        <span>Elite</span>
        <span>Strong</span>
        <span>Moderate</span>
      </div>
      <ul className={styles.typePerfList}>
        {orderTypeRows(rows).map((row) => {
          const eliteBucket = row.byConfidence.ELITE ?? emptyPerformanceBucket();
          return (
            <li key={row.type} className={styles.typePerfRow}>
              <div className={styles.typePerfName}>{formatType(row.type)}</div>
              <div className={styles.typePerfSample}>{row.overall.sampleSize}</div>
              <div className={styles.typePerfRecord}>
                <span className={styles.win}>{row.overall.wins}</span>
                <span className={styles.typePerfRecordSep}>–</span>
                <span className={styles.loss}>{row.overall.losses}</span>
              </div>
              <div className={styles.typePerfHit}>
                <span className={`${styles.hitBadge} ${hitBadgeClass(row.overall)}`}>
                  {formatBucketHit(row.overall)}
                </span>
              </div>
              <div className={styles.typePerfMobileMeta}>
                <span>{row.overall.sampleSize} graded</span>
                <span>
                  <span className={styles.win}>{row.overall.wins}</span>
                  –
                  <span className={styles.loss}>{row.overall.losses}</span>
                </span>
              </div>
              <div className={styles.typePerfBands}>
                <div className={styles.typePerfBand}>
                  <span className={styles.typePerfBandLabel}>Elite</span>
                  <span className={styles.typePerfBandValue}>{formatBandHit(eliteBucket)}</span>
                </div>
                <div className={styles.typePerfBand}>
                  <span className={styles.typePerfBandLabel}>Strong</span>
                  <span className={styles.typePerfBandValue}>
                    {formatBandHit(row.byConfidence.STRONG)}
                  </span>
                </div>
                <div className={styles.typePerfBand}>
                  <span className={styles.typePerfBandLabel}>Moderate</span>
                  <span className={styles.typePerfBandValue}>
                    {formatBandHit(row.byConfidence.MODERATE)}
                  </span>
                </div>
              </div>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

function emptyPerformanceBucket(): PerformanceBucket {
  return {
    wins: 0,
    losses: 0,
    voids: 0,
    pending: 0,
    unsupported: 0,
    hitRate: null,
    sampleSize: 0,
    enoughData: false,
  };
}

function PerformancePanel({
  data,
  period,
  onPeriodChange,
  isLoading,
  isError,
  error,
  onRetry,
}: {
  data?: ResultsPerformance;
  period: PerformancePeriod;
  onPeriodChange: (period: PerformancePeriod) => void;
  isLoading: boolean;
  isError: boolean;
  error: Error | null;
  onRetry: () => void;
}) {
  return (
    <>
      <div className={styles.filterRow}>
        {PERIODS.map((p) => (
          <button
            key={p}
            type="button"
            className={`${styles.chip} ${period === p ? styles.chipActive : ''}`}
            onClick={() => onPeriodChange(p)}
          >
            {p === 'all' ? 'All' : p}
          </button>
        ))}
      </div>

      {isLoading && <div className={styles.message}>Loading performance…</div>}

      {isError && (
        <div className={styles.error}>
          <p>Could not load performance.</p>
          <p className={styles.errorDetail}>{error?.message ?? 'Unknown error'}</p>
          <button type="button" className={styles.navButton} onClick={onRetry}>Retry</button>
        </div>
      )}

      {!isLoading && !isError && data && (
        <>
          <PerformanceSummary
            title={`Overall · ${data.period === 'all' ? 'all time' : `last ${data.period}`}`}
            bucket={data.overall}
            tone="all"
          />
          <p className={styles.performanceMeta}>
            Window by snapshot date (Europe/London)
            {data.fromDate ? `: ${data.fromDate} → ${data.toDate}` : ` through ${data.toDate}`}.
            {' '}Voids excluded from hit rate. Minimum sample {data.minSample}.
          </p>
          <div className={styles.performanceBands}>
            <PerformanceSummary title="Elite" bucket={data.elite ?? emptyPerformanceBucket()} tone="strong" />
            <PerformanceSummary title="Strong" bucket={data.byConfidence.STRONG} tone="strong" />
            <PerformanceSummary title="Moderate" bucket={data.byConfidence.MODERATE} tone="moderate" />
          </div>

          <h2 className={styles.tableTitle}>By recommendation type</h2>
          {data.byType.length === 0 ? (
            <div className={styles.empty}>
              <h2>No graded picks yet</h2>
              <p>Performance fills in as daily snapshots settle.</p>
            </div>
          ) : (
            <TypePerformanceList rows={data.byType} />
          )}
        </>
      )}
    </>
  );
}

export default function Results() {
  const [searchParams, setSearchParams] = useSearchParams();

  const view = parseView(searchParams.get('view'));
  const selectedDate = searchParams.get('date') || undefined;
  const outcomeFilter = parseOutcome(searchParams.get('outcome'));
  const typeFilter = searchParams.get('type') || 'ALL';
  const period = parsePeriod(searchParams.get('period'));

  const updateParams = useCallback((patch: Record<string, string | null | undefined>) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      for (const [key, value] of Object.entries(patch)) {
        const isDefault =
          (key === 'view' && (value == null || value === 'day'))
          || (key === 'outcome' && (value == null || value === 'ALL'))
          || (key === 'type' && (value == null || value === 'ALL'))
          || (key === 'period' && (value == null || value === '30d'));
        if (value == null || value === '' || isDefault) {
          next.delete(key);
        } else {
          next.set(key, value);
        }
      }
      return next;
    }, { replace: true });
  }, [setSearchParams]);

  const { data: dates = [] } = useQuery({
    queryKey: ['results-dates'],
    queryFn: () => resultsService.getDates(),
  });

  useEffect(() => {
    if (!selectedDate && dates.length > 0) {
      updateParams({ date: dates[0] });
    }
  }, [dates, selectedDate, updateParams]);

  const dateIndex = selectedDate ? dates.indexOf(selectedDate) : -1;
  const today = londonToday();
  const todayTarget = dates.includes(today) ? today : dates[0];
  const isOnToday = Boolean(todayTarget) && selectedDate === todayTarget;

  const {
    data: rawData,
    isLoading,
    isError,
    error,
    refetch,
  } = useQuery({
    queryKey: ['results-day', selectedDate, outcomeFilter],
    queryFn: () => resultsService.getDay(selectedDate, outcomeFilter),
    enabled: (view === 'day' || view === 'elite') && (Boolean(selectedDate) || dates.length === 0),
  });

  const {
    data: performance,
    isLoading: perfLoading,
    isError: perfError,
    error: perfErr,
    refetch: refetchPerf,
  } = useQuery({
    queryKey: ['results-performance', period],
    queryFn: () => resultsService.getPerformance(period),
    enabled: view === 'performance',
  });

  const data = withConfidenceDefaults(rawData);

  const availableTypes = useMemo(
    () => collectTypes(data?.fixtures ?? []),
    [data?.fixtures],
  );

  const eliteTypes = useMemo(
    () => collectTypes(data?.eliteFixtures ?? []),
    [data?.eliteFixtures],
  );

  useEffect(() => {
    const types = view === 'elite' ? eliteTypes : availableTypes;
    if (typeFilter !== 'ALL' && types.length > 0 && !types.includes(typeFilter)) {
      updateParams({ type: null });
    }
  }, [typeFilter, availableTypes, eliteTypes, view, updateParams]);

  const strongFixtures = useMemo(
    () => fixturesForConfidence(data?.fixtures ?? [], 'STRONG', typeFilter),
    [data?.fixtures, typeFilter],
  );
  const moderateFixtures = useMemo(
    () => fixturesForConfidence(data?.fixtures ?? [], 'MODERATE', typeFilter),
    [data?.fixtures, typeFilter],
  );

  const strongSummary = useMemo(
    () => (typeFilter === 'ALL' ? data?.strongSummary : summaryFromFixtures(strongFixtures)),
    [typeFilter, data?.strongSummary, strongFixtures],
  );
  const moderateSummary = useMemo(
    () => (typeFilter === 'ALL' ? data?.moderateSummary : summaryFromFixtures(moderateFixtures)),
    [typeFilter, data?.moderateSummary, moderateFixtures],
  );

  const eliteFixtures = useMemo(() => {
    const fixtures = data?.eliteFixtures ?? [];
    if (typeFilter === 'ALL') return fixtures;
    return fixtures
      .map((fixture) => ({
        ...fixture,
        picks: fixture.picks.filter((pick) => pick.type === typeFilter),
      }))
      .filter((fixture) => fixture.picks.length > 0);
  }, [data?.eliteFixtures, typeFilter]);

  const eliteSummary = useMemo(() => {
    if (!data) return undefined;
    if (typeFilter === 'ALL') return data.eliteSummary;
    return summaryFromFixtures(eliteFixtures);
  }, [data, typeFilter, eliteFixtures]);

  const eliteKeys = useMemo(() => {
    const keys = new Set<string>();
    for (const fixture of data?.eliteFixtures ?? []) {
      for (const pick of fixture.picks) {
        keys.add(`${fixture.fixtureId}:${pick.type}`);
      }
    }
    return keys;
  }, [data?.eliteFixtures]);

  const goPrev = () => {
    if (dateIndex < 0 || dateIndex >= dates.length - 1) return;
    updateParams({ date: dates[dateIndex + 1] });
  };

  const goNext = () => {
    if (dateIndex <= 0) return;
    updateParams({ date: dates[dateIndex - 1] });
  };

  const goToday = () => {
    if (!todayTarget) return;
    updateParams({ date: todayTarget, view: view === 'elite' ? 'elite' : 'day' });
  };

  const showDayNav = view === 'day' || view === 'elite';

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <div>
          <h1 className={styles.title}>Results</h1>
          <p className={styles.subtitle}>
            Daily Dashboard · Overall Performance Tracker · Elite Pick Performance
          </p>
        </div>
      </header>

      <div className={styles.viewToggle} role="tablist" aria-label="Results view">
        <button
          type="button"
          role="tab"
          aria-selected={view === 'day'}
          className={`${styles.viewToggleButton} ${view === 'day' ? styles.viewToggleActive : ''}`}
          onClick={() => updateParams({ view: 'day' })}
        >
          1 - Daily Dashboard
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={view === 'performance'}
          className={`${styles.viewToggleButton} ${view === 'performance' ? styles.viewToggleActive : ''}`}
          onClick={() => updateParams({ view: 'performance' })}
        >
          2 - Overall Performance Tracker
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={view === 'elite'}
          className={`${styles.viewToggleButton} ${view === 'elite' ? styles.viewToggleActive : ''}`}
          onClick={() => updateParams({ view: 'elite' })}
        >
          3 - Elite Pick Performance
        </button>
      </div>

      {showDayNav && (
        <>
          <div className={styles.controls}>
            <div className={styles.dateNav}>
              <button
                type="button"
                className={styles.navButton}
                onClick={goPrev}
                disabled={dateIndex < 0 || dateIndex >= dates.length - 1}
              >
                ← Older
              </button>
              <label className={styles.dateLabel}>
                <span className={styles.srOnly}>Snapshot date</span>
                <select
                  className={styles.select}
                  value={selectedDate ?? ''}
                  onChange={(e) => updateParams({ date: e.target.value || null })}
                  disabled={dates.length === 0}
                >
                  {dates.length === 0 && <option value="">No snapshots yet</option>}
                  {dates.map((d) => (
                    <option key={d} value={d}>{d}</option>
                  ))}
                </select>
              </label>
              <button
                type="button"
                className={styles.navButton}
                onClick={goNext}
                disabled={dateIndex <= 0}
              >
                Newer →
              </button>
              <button
                type="button"
                className={`${styles.navButton} ${isOnToday ? styles.navButtonActive : ''}`}
                onClick={goToday}
                disabled={!todayTarget || isOnToday}
                title={dates.includes(today) ? `Jump to ${today}` : 'Jump to latest snapshot'}
              >
                Today
              </button>
            </div>
          </div>

          <div className={styles.filterBlock}>
            <div className={styles.filterGroup}>
              <span className={styles.filterLabel}>Outcome</span>
              <div className={styles.filterRow}>
                {OUTCOME_FILTERS.map((outcome) => (
                  <button
                    key={outcome}
                    type="button"
                    className={`${styles.chip} ${outcomeFilter === outcome ? styles.chipActive : ''}`}
                    onClick={() => updateParams({ outcome })}
                  >
                    {outcome === 'ALL' ? 'All' : outcomeLabel(outcome)}
                  </button>
                ))}
              </div>
            </div>

            {(view === 'day' ? availableTypes : eliteTypes).length > 0 && (
              <div className={styles.filterGroup}>
                <span className={styles.filterLabel}>Type</span>
                <div className={styles.filterRow}>
                  <button
                    type="button"
                    className={`${styles.chip} ${typeFilter === 'ALL' ? styles.chipActive : ''}`}
                    onClick={() => updateParams({ type: 'ALL' })}
                  >
                    All
                  </button>
                  {(view === 'day' ? availableTypes : eliteTypes).map((type) => (
                    <button
                      key={type}
                      type="button"
                      className={`${styles.chip} ${typeFilter === type ? styles.chipActive : ''}`}
                      onClick={() => updateParams({ type })}
                    >
                      {formatType(type)}
                    </button>
                  ))}
                </div>
              </div>
            )}
          </div>
        </>
      )}

      {view === 'day' && (
        <>
          {isLoading && <div className={styles.message}>Loading results…</div>}

          {isError && (
            <div className={styles.error}>
              <p>Could not load results.</p>
              <p className={styles.errorDetail}>{error instanceof Error ? error.message : 'Unknown error'}</p>
              <button type="button" className={styles.navButton} onClick={() => refetch()}>Retry</button>
            </div>
          )}

          {!isLoading && !isError && dates.length === 0 && (
            <div className={styles.empty}>
              <h2>No results yet</h2>
              <p>Picks are recorded after the daily sync. Run a snapshot from admin, then check back here.</p>
            </div>
          )}

          {!isLoading && !isError && data?.snapshotDate && strongFixtures.length === 0 && moderateFixtures.length === 0 && eliteFixtures.length === 0 && (
            <div className={styles.empty}>
              <h2>No picks for this filter</h2>
              <p>
                Nothing matched
                {outcomeFilter !== 'ALL' ? ` ${outcomeLabel(outcomeFilter).toLowerCase()}` : ''}
                {typeFilter !== 'ALL' ? ` ${formatType(typeFilter)}` : ''}
                {' '}on {data.snapshotDate}.
              </p>
            </div>
          )}

          {!isLoading && !isError && data?.snapshotDate && (strongFixtures.length > 0 || moderateFixtures.length > 0 || eliteFixtures.length > 0) && (
            <div className={styles.dashboardSections}>
              {eliteFixtures.length > 0 && eliteSummary && (
                <ConfidenceSection
                  title="Elite Picks"
                  tone="strong"
                  summary={eliteSummary}
                  fixtures={eliteFixtures}
                  showEliteRank
                  compact
                />
              )}
              {strongFixtures.length > 0 && strongSummary && (
                <ConfidenceSection
                  title="Strong"
                  tone="strong"
                  summary={strongSummary}
                  fixtures={strongFixtures}
                  eliteKeys={eliteKeys}
                  compact
                />
              )}
              {moderateFixtures.length > 0 && moderateSummary && (
                <ConfidenceSection
                  title="Moderate"
                  tone="moderate"
                  summary={moderateSummary}
                  fixtures={moderateFixtures}
                  eliteKeys={eliteKeys}
                  compact
                />
              )}
            </div>
          )}
        </>
      )}

      {view === 'elite' && (
        <>
          {eliteSummary && data?.snapshotDate && (
            <SummaryStrip title="Elite Pick Performance" summary={eliteSummary} tone="strong" />
          )}

          {!isLoading && !isError && eliteFixtures.length > 0 && (
            <ReturnsPanel fixtures={eliteFixtures} />
          )}

          {isLoading && <div className={styles.message}>Loading elite picks…</div>}

          {isError && (
            <div className={styles.error}>
              <p>Could not load elite picks.</p>
              <p className={styles.errorDetail}>{error instanceof Error ? error.message : 'Unknown error'}</p>
              <button type="button" className={styles.navButton} onClick={() => refetch()}>Retry</button>
            </div>
          )}

          {!isLoading && !isError && dates.length === 0 && (
            <div className={styles.empty}>
              <h2>No results yet</h2>
              <p>Elite picks are tagged when the daily snapshot runs. Check back after the next sync.</p>
            </div>
          )}

          {!isLoading && !isError && data?.snapshotDate && eliteFixtures.length === 0 && (
            <div className={styles.empty}>
              <h2>No Elite picks</h2>
              <p>
                No Elite candidates
                {outcomeFilter !== 'ALL' ? ` matching ${outcomeLabel(outcomeFilter).toLowerCase()}` : ''}
                {typeFilter !== 'ALL' ? ` for ${formatType(typeFilter)}` : ''}
                {' '}on {data.snapshotDate}.
              </p>
            </div>
          )}

          {!isLoading && !isError && data?.snapshotDate && eliteFixtures.length > 0 && (
            <FixtureList
              fixtures={eliteFixtures}
              emptyTitle="No Elite picks"
              emptyBody="No Elite picks for this filter."
              showEliteRank
            />
          )}
        </>
      )}

      {view === 'performance' && (
        <PerformancePanel
          data={performance}
          period={period}
          onPeriodChange={(next) => updateParams({ period: next })}
          isLoading={perfLoading}
          isError={perfError}
          error={perfErr instanceof Error ? perfErr : null}
          onRetry={() => refetchPerf()}
        />
      )}
    </div>
  );
}
