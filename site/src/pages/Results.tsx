import { useCallback, useEffect, useMemo } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { resultsService } from '../services/api';
import type {
  DayResults,
  PerformanceBucket,
  PerformancePeriod,
  PickOutcome,
  ResultsDaySummary,
  ResultsFixture,
  ResultsPerformance,
  ResultsPick,
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
  return type.replaceAll('_', ' ');
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

function flattenPicks(fixtures: ResultsFixture[]): ResultsPick[] {
  return fixtures.flatMap((fixture) => fixture.picks);
}

function isPricedOdds(odds: number | null | undefined): odds is number {
  return odds != null && !Number.isNaN(odds) && odds > 0;
}

function formatUsd(amount: number): string {
  return amount.toLocaleString('en-US', {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

function allWinReturns(picks: ResultsPick[], stake = LEVEL_STAKE_USD) {
  const pricedOdds = picks.map((pick) => pick.odds).filter(isPricedOdds);
  const singlesReturn = pricedOdds.reduce((total, odds) => total + stake * odds, 0);
  const combinedOdds = pricedOdds.reduce((product, odds) => product * odds, 1);
  return {
    priced: pricedOdds.length,
    unpriced: picks.length - pricedOdds.length,
    singlesCount: pricedOdds.length,
    singlesReturn,
    accumulatorReturn: pricedOdds.length > 0 ? stake * combinedOdds : 0,
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

function SummaryStrip({
  title,
  summary,
  tone,
}: {
  title: string;
  summary: ResultsDaySummary;
  tone: 'strong' | 'moderate' | 'all';
}) {
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
        <h2 className={styles.summaryTitle}>{title}</h2>
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

function ReturnsPanel({ fixtures }: { fixtures: ResultsFixture[] }) {
  const stats = allWinReturns(flattenPicks(fixtures));

  return (
    <section className={styles.returnsPanel}>
      <h2 className={styles.returnsTitle}>Returns</h2>
      {stats.priced === 0 ? (
        <p className={styles.returnsStatement}>
          None of the Elite picks in this view have a known price, so $10 returns
          cannot be calculated.
        </p>
      ) : (
        <>
          <p className={styles.returnsStatement}>
            If every priced Elite pick in this view had won:
          </p>
          <div className={styles.returnsRows}>
            <div className={styles.returnsRow}>
              <span className={styles.returnsLabel}>
                {stats.singlesCount} × $10 singles
              </span>
              <span className={styles.returnsValue}>{formatUsd(stats.singlesReturn)}</span>
            </div>
            <div className={styles.returnsRow}>
              <span className={styles.returnsLabel}>$10 accumulator</span>
              <span className={styles.returnsValue}>{formatUsd(stats.accumulatorReturn)}</span>
            </div>
          </div>
        </>
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

function FixtureList({
  fixtures,
  emptyTitle,
  emptyBody,
  showEliteRank = false,
}: {
  fixtures: ResultsFixture[];
  emptyTitle: string;
  emptyBody: string;
  showEliteRank?: boolean;
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
              return (
                <li key={pick.id} className={styles.pickRow}>
                  <div className={styles.pickMain}>
                    {showEliteRank && pick.eliteRank != null && (
                      <span className={styles.eliteRank} aria-label={`Elite rank ${pick.eliteRank}`}>
                        #{pick.eliteRank}
                      </span>
                    )}
                    <div className={styles.pickSelection}>
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
}: {
  title: string;
  tone: 'strong' | 'moderate';
  summary: ResultsDaySummary;
  fixtures: ResultsFixture[];
}) {
  return (
    <section className={styles.confidenceSection}>
      <SummaryStrip title={title} summary={summary} tone={tone} />
      <FixtureList
        fixtures={fixtures}
        emptyTitle={`No ${title.toLowerCase()} picks`}
        emptyBody={`No ${title.toLowerCase()} picks for this filter.`}
      />
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
          <div className={styles.splitSections}>
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
            <div className={styles.tableWrap}>
              <table className={styles.perfTable}>
                <thead>
                  <tr>
                    <th>Type</th>
                    <th>Sample</th>
                    <th>W</th>
                    <th>L</th>
                    <th>Hit rate</th>
                    <th>Strong</th>
                    <th>Moderate</th>
                  </tr>
                </thead>
                <tbody>
                  {data.byType.map((row) => (
                    <tr key={row.type}>
                      <td>{formatType(row.type)}</td>
                      <td>{row.overall.sampleSize}</td>
                      <td className={styles.win}>{row.overall.wins}</td>
                      <td className={styles.loss}>{row.overall.losses}</td>
                      <td>
                        <span className={`${styles.hitBadge} ${hitBadgeClass(row.overall)}`}>
                          {formatBucketHit(row.overall)}
                        </span>
                      </td>
                      <td>
                        {formatBucketHit(row.byConfidence.STRONG)}
                        <span className={styles.sampleHint}> ({row.byConfidence.STRONG.sampleSize})</span>
                      </td>
                      <td>
                        {formatBucketHit(row.byConfidence.MODERATE)}
                        <span className={styles.sampleHint}> ({row.byConfidence.MODERATE.sampleSize})</span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
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
  const allSummary = useMemo(() => {
    if (!data) return undefined;
    if (typeFilter === 'ALL') return data.summary;
    return summaryFromFixtures([...strongFixtures, ...moderateFixtures]);
  }, [data, typeFilter, strongFixtures, moderateFixtures]);

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
          {allSummary && data?.snapshotDate && (
            <SummaryStrip title="All picks" summary={allSummary} tone="all" />
          )}

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

          {!isLoading && !isError && data?.snapshotDate && strongFixtures.length === 0 && moderateFixtures.length === 0 && (
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

          {!isLoading && !isError && data?.snapshotDate && (strongFixtures.length > 0 || moderateFixtures.length > 0) && (
            <div className={styles.splitSections}>
              {strongFixtures.length > 0 && strongSummary && (
                <ConfidenceSection
                  title="Strong"
                  tone="strong"
                  summary={strongSummary}
                  fixtures={strongFixtures}
                />
              )}
              {moderateFixtures.length > 0 && moderateSummary && (
                <ConfidenceSection
                  title="Moderate"
                  tone="moderate"
                  summary={moderateSummary}
                  fixtures={moderateFixtures}
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
