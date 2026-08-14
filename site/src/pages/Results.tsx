import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
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

type ConfidenceBand = 'STRONG' | 'MODERATE';
type ResultsView = 'day' | 'performance';

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

function fixturesForConfidence(fixtures: ResultsFixture[], confidence: ConfidenceBand): ResultsFixture[] {
  return fixtures
    .map((fixture) => ({
      ...fixture,
      picks: fixture.picks.filter((pick) => pick.confidence?.toUpperCase() === confidence),
    }))
    .filter((fixture) => fixture.picks.length > 0);
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

function FixtureList({
  fixtures,
  emptyTitle,
  emptyBody,
}: {
  fixtures: ResultsFixture[];
  emptyTitle: string;
  emptyBody: string;
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
        <section key={fixture.fixtureId} className={styles.fixtureBlock}>
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
            {fixture.picks.map((pick) => (
              <li key={pick.id} className={styles.pickRow}>
                <div className={styles.pickMain}>
                  <span className={styles.pickMarket}>{pick.market}</span>
                  <span className={styles.pickType}>{formatType(pick.type)}</span>
                </div>
                <div className={styles.pickSide}>
                  <span className={`${styles.outcome} ${styles[`outcome${pick.outcome}`]}`}>
                    {outcomeLabel(pick.outcome)}
                  </span>
                </div>
              </li>
            ))}
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
  outcomeFilter,
  snapshotDate,
}: {
  title: string;
  tone: 'strong' | 'moderate';
  summary: ResultsDaySummary;
  fixtures: ResultsFixture[];
  outcomeFilter: PickOutcome | 'ALL';
  snapshotDate: string;
}) {
  const emptyBody =
    outcomeFilter === 'ALL'
      ? `No ${title.toLowerCase()} picks for ${snapshotDate}.`
      : `No ${outcomeLabel(outcomeFilter).toLowerCase()} ${title.toLowerCase()} picks on ${snapshotDate}.`;

  return (
    <section className={styles.confidenceSection}>
      <SummaryStrip title={title} summary={summary} tone={tone} />
      <FixtureList
        fixtures={fixtures}
        emptyTitle={`No ${title.toLowerCase()} picks`}
        emptyBody={emptyBody}
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
      <div className={styles.outcomeFilters}>
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
  const [view, setView] = useState<ResultsView>('day');
  const [selectedDate, setSelectedDate] = useState<string | undefined>(undefined);
  const [outcomeFilter, setOutcomeFilter] = useState<PickOutcome | 'ALL'>('ALL');
  const [period, setPeriod] = useState<PerformancePeriod>('30d');

  const { data: dates = [] } = useQuery({
    queryKey: ['results-dates'],
    queryFn: () => resultsService.getDates(),
  });

  useEffect(() => {
    if (!selectedDate && dates.length > 0) {
      setSelectedDate(dates[0]);
    }
  }, [dates, selectedDate]);

  const dateIndex = selectedDate ? dates.indexOf(selectedDate) : -1;

  const {
    data: rawData,
    isLoading,
    isError,
    error,
    refetch,
  } = useQuery({
    queryKey: ['results-day', selectedDate, outcomeFilter],
    queryFn: () => resultsService.getDay(selectedDate, outcomeFilter),
    enabled: view === 'day' && (Boolean(selectedDate) || dates.length === 0),
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

  const strongFixtures = useMemo(
    () => fixturesForConfidence(data?.fixtures ?? [], 'STRONG'),
    [data?.fixtures],
  );
  const moderateFixtures = useMemo(
    () => fixturesForConfidence(data?.fixtures ?? [], 'MODERATE'),
    [data?.fixtures],
  );

  const goPrev = () => {
    if (dateIndex < 0 || dateIndex >= dates.length - 1) return;
    setSelectedDate(dates[dateIndex + 1]);
  };

  const goNext = () => {
    if (dateIndex <= 0) return;
    setSelectedDate(dates[dateIndex - 1]);
  };

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <div>
          <h1 className={styles.title}>Results</h1>
          <p className={styles.subtitle}>Day board for graded picks · Performance for longer-run accuracy</p>
        </div>
      </header>

      <div className={styles.viewToggle} role="tablist" aria-label="Results view">
        <button
          type="button"
          role="tab"
          aria-selected={view === 'day'}
          className={`${styles.viewToggleButton} ${view === 'day' ? styles.viewToggleActive : ''}`}
          onClick={() => setView('day')}
        >
          Day board
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={view === 'performance'}
          className={`${styles.viewToggleButton} ${view === 'performance' ? styles.viewToggleActive : ''}`}
          onClick={() => setView('performance')}
        >
          Performance
        </button>
      </div>

      {view === 'day' && (
        <>
          <div className={styles.controls}>
            <div className={styles.dateNav}>
              <button type="button" className={styles.navButton} onClick={goPrev} disabled={dateIndex < 0 || dateIndex >= dates.length - 1}>
                ← Older
              </button>
              <label className={styles.dateLabel}>
                <span className={styles.srOnly}>Snapshot date</span>
                <select
                  className={styles.select}
                  value={selectedDate ?? ''}
                  onChange={(e) => setSelectedDate(e.target.value || undefined)}
                  disabled={dates.length === 0}
                >
                  {dates.length === 0 && <option value="">No snapshots yet</option>}
                  {dates.map((d) => (
                    <option key={d} value={d}>{d}</option>
                  ))}
                </select>
              </label>
              <button type="button" className={styles.navButton} onClick={goNext} disabled={dateIndex <= 0}>
                Newer →
              </button>
            </div>

            <div className={styles.outcomeFilters}>
              {OUTCOME_FILTERS.map((outcome) => (
                <button
                  key={outcome}
                  type="button"
                  className={`${styles.chip} ${outcomeFilter === outcome ? styles.chipActive : ''}`}
                  onClick={() => setOutcomeFilter(outcome)}
                >
                  {outcome === 'ALL' ? 'All' : outcomeLabel(outcome)}
                </button>
              ))}
            </div>
          </div>

          {data?.summary && data.snapshotDate && (
            <SummaryStrip title="All picks" summary={data.summary} tone="all" />
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

          {!isLoading && !isError && data?.snapshotDate && (
            <div className={styles.splitSections}>
              <ConfidenceSection
                title="Strong"
                tone="strong"
                summary={data.strongSummary}
                fixtures={strongFixtures}
                outcomeFilter={outcomeFilter}
                snapshotDate={data.snapshotDate}
              />
              <ConfidenceSection
                title="Moderate"
                tone="moderate"
                summary={data.moderateSummary}
                fixtures={moderateFixtures}
                outcomeFilter={outcomeFilter}
                snapshotDate={data.snapshotDate}
              />
            </div>
          )}
        </>
      )}

      {view === 'performance' && (
        <PerformancePanel
          data={performance}
          period={period}
          onPeriodChange={setPeriod}
          isLoading={perfLoading}
          isError={perfError}
          error={perfErr instanceof Error ? perfErr : null}
          onRetry={() => refetchPerf()}
        />
      )}
    </div>
  );
}
