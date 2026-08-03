import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { resultsService } from '../services/api';
import type { PickOutcome } from '../types';
import styles from './Results.module.css';

const OUTCOME_FILTERS: Array<PickOutcome | 'ALL'> = [
  'ALL',
  'WIN',
  'LOSS',
  'VOID',
  'PENDING',
  'UNSUPPORTED',
];

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

export default function Results() {
  const [selectedDate, setSelectedDate] = useState<string | undefined>(undefined);
  const [outcomeFilter, setOutcomeFilter] = useState<PickOutcome | 'ALL'>('ALL');

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

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['results-day', selectedDate, outcomeFilter],
    queryFn: () => resultsService.getDay(selectedDate, outcomeFilter),
    enabled: Boolean(selectedDate) || dates.length === 0,
  });

  const summary = data?.summary;
  const hitRateLabel = useMemo(() => {
    if (summary?.hitRate == null) return '—';
    return `${summary.hitRate.toFixed(0)}%`;
  }, [summary]);

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
          <p className={styles.subtitle}>How the daily Strong &amp; Moderate picks landed</p>
        </div>
      </header>

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

      {summary && data?.snapshotDate && (
        <div className={styles.summaryStrip}>
          <div className={styles.summaryStat}>
            <span className={styles.summaryValue}>{hitRateLabel}</span>
            <span className={styles.summaryLabel}>Hit rate</span>
          </div>
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

      {!isLoading && !isError && data?.snapshotDate && data.fixtures.length === 0 && (
        <div className={styles.empty}>
          <h2>No picks for this filter</h2>
          <p>
            {outcomeFilter === 'ALL'
              ? 'No snapshotted picks for this date.'
              : `No ${outcomeLabel(outcomeFilter).toLowerCase()} picks on ${data.snapshotDate}.`}
          </p>
        </div>
      )}

      {!isLoading && !isError && data?.fixtures && data.fixtures.length > 0 && (
        <div className={styles.fixtureList}>
          {data.fixtures.map((fixture) => (
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
                      <span className={`${styles.confidence} ${pick.confidence === 'STRONG' ? styles.strong : styles.moderate}`}>
                        {pick.confidence === 'STRONG' ? 'Strong' : 'Moderate'}
                      </span>
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
      )}
    </div>
  );
}
