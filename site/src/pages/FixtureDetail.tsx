import { useMemo } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { fixtureService, recommendationService } from '../services/api';
import { EarlyKickoffBadge, EarlyKickoffStrip, MarketIcon, RecommendationRow } from '../components';
import type { Recommendation, RecommendationType } from '../types';
import {
  EARLY_KICKOFF_WARNING,
  isEarlyKickoffUk,
} from '../utils/kickoff';
import { SECTION_CONFIG, SECTION_ORDER, includeInMarketSection } from '../utils/recommendationSections';
import {
  elitePickKey,
  flattenGroupedRecommendations,
  selectElitePicks,
  toEliteKeySet,
} from '../utils/elitePicks';
import styles from './FixtureDetail.module.css';

const CONFIDENCE_ORDER: Record<string, number> = {
  STRONG: 0,
  MODERATE: 1,
  WEAK: 2,
};

export default function FixtureDetail() {
  const { fixtureId } = useParams<{ fixtureId: string }>();
  const id = Number(fixtureId);

  const {
    data: fixture,
    isLoading: fixtureLoading,
    isError: fixtureError,
    error: fixtureErrorObj,
    refetch: refetchFixture,
  } = useQuery({
    queryKey: ['fixture', id],
    queryFn: () => fixtureService.getById(id),
    enabled: Number.isFinite(id) && id > 0,
  });

  const {
    data: recommendations = [],
    isLoading: recsLoading,
    isError: recsError,
    error: recsErrorObj,
    refetch: refetchRecs,
  } = useQuery({
    queryKey: ['recommendations-fixture', id],
    queryFn: () => recommendationService.getByFixture(id),
    enabled: Number.isFinite(id) && id > 0,
  });

  const { data: groupedRecommendations } = useQuery({
    queryKey: ['recommendations-grouped', 7],
    queryFn: () => recommendationService.getGrouped(7),
  });

  const eliteKeys = useMemo(
    () => toEliteKeySet(selectElitePicks(flattenGroupedRecommendations(groupedRecommendations))),
    [groupedRecommendations],
  );

  const groupedByType = useMemo(() => {
    const sorted = [...recommendations].sort((a, b) => {
      const confDiff =
        (CONFIDENCE_ORDER[a.confidence] ?? 9) - (CONFIDENCE_ORDER[b.confidence] ?? 9);
      if (confDiff !== 0) return confDiff;
      return (b.score || 0) - (a.score || 0);
    });

    return sorted.reduce((acc, rec) => {
      if (!includeInMarketSection(rec)) {
        return acc;
      }
      const type = rec.type as RecommendationType;
      if (!acc[type]) {
        acc[type] = [];
      }
      acc[type].push(rec);
      return acc;
    }, {} as Record<RecommendationType, Recommendation[]>);
  }, [recommendations]);

  const sortedTypes = SECTION_ORDER.filter((type) => groupedByType[type]?.length);

  if (!Number.isFinite(id) || id <= 0) {
    return (
      <div className={styles.page}>
        <div className={styles.error}>
          <h2 className={styles.errorTitle}>Invalid fixture</h2>
          <p className={styles.errorMessage}>That fixture link looks wrong.</p>
          <Link to="/fixtures" className={styles.backLink}>
            ← Back to fixtures
          </Link>
        </div>
      </div>
    );
  }

  if (fixtureLoading) {
    return (
      <div className={styles.page}>
        <div className={styles.loading}>Loading fixture...</div>
      </div>
    );
  }

  if (fixtureError || !fixture) {
    return (
      <div className={styles.page}>
        <div className={styles.error}>
          <h2 className={styles.errorTitle}>Fixture not found</h2>
          <p className={styles.errorMessage}>
            {fixtureErrorObj instanceof Error
              ? fixtureErrorObj.message
              : 'This fixture could not be loaded.'}
          </p>
          <div className={styles.errorActions}>
            <button className={styles.retryButton} onClick={() => refetchFixture()}>
              Try Again
            </button>
            <Link to="/fixtures" className={styles.backLink}>
              ← Back to fixtures
            </Link>
          </div>
        </div>
      </div>
    );
  }

  const matchDate = new Date(fixture.dateUnix * 1000);
  const formattedDate = matchDate.toLocaleDateString('en-GB', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  });
  const formattedTime = matchDate.toLocaleTimeString('en-GB', {
    hour: '2-digit',
    minute: '2-digit',
  });
  const stadium = fixture.stadiumName || fixture.stadium;
  const isEarlyKickoff = isEarlyKickoffUk(fixture.dateUnix);

  return (
    <div className={styles.page}>
      <Link to="/fixtures" className={styles.backLink}>
        ← Back to fixtures
      </Link>

      <header className={`${styles.header} ${isEarlyKickoff ? styles.headerEarly : ''}`}>
        <div className={styles.matchup}>
          <h1 className={styles.title}>
            <span className={styles.team}>{fixture.homeTeamName}</span>
            <span className={styles.vs}>vs</span>
            <span className={styles.team}>{fixture.awayTeamName}</span>
          </h1>
        </div>
        <div className={styles.meta}>
          <span
            className={`${styles.kickoff} ${isEarlyKickoff ? styles.kickoffEarly : ''}`}
            title={
              isEarlyKickoff
                ? `${formattedDate} · ${formattedTime} · ${EARLY_KICKOFF_WARNING}`
                : undefined
            }
          >
            <span>
              {formattedDate} · {formattedTime}
            </span>
            {isEarlyKickoff && <EarlyKickoffBadge />}
          </span>
          {stadium && <span className={styles.stadium}>{stadium}</span>}
          {fixture.gameWeek != null && (
            <span className={styles.gameweek}>Gameweek {fixture.gameWeek}</span>
          )}
        </div>
        {isEarlyKickoff && (
          <div className={styles.earlyStripWrap}>
            <EarlyKickoffStrip />
          </div>
        )}
      </header>

      <section className={styles.recommendations}>
        <div className={styles.sectionHeading}>
          <h2 className={styles.sectionTitle}>Recommendations</h2>
          {!recsLoading && (
            <span className={styles.pickCount}>
              {recommendations.length} pick{recommendations.length !== 1 ? 's' : ''}
            </span>
          )}
        </div>

        {recsLoading ? (
          <div className={styles.loading}>Loading recommendations...</div>
        ) : recsError ? (
          <div className={styles.error}>
            <h2 className={styles.errorTitle}>Failed to load recommendations</h2>
            <p className={styles.errorMessage}>
              {recsErrorObj instanceof Error
                ? recsErrorObj.message
                : 'An unexpected error occurred.'}
            </p>
            <button className={styles.retryButton} onClick={() => refetchRecs()}>
              Try Again
            </button>
          </div>
        ) : sortedTypes.length > 0 ? (
          <div className={styles.sections}>
            {sortedTypes.map((type) => {
              const typeRecs = groupedByType[type];
              const config = SECTION_CONFIG[type] || {
                title: type,
                showPrice: true,
                scoreLabel: 'Score',
                scoreUnit: '',
              };

              return (
                <section key={type} className={styles.section}>
                  <h3 className={styles.typeHeader}>
                    <MarketIcon type={type} title={config.title} className={styles.typeIcon} />
                    <span className={styles.typeTitle}>{config.title}</span>
                    <span className={styles.typeCount}>
                      {typeRecs.length} pick{typeRecs.length !== 1 ? 's' : ''}
                    </span>
                  </h3>
                  <div className={styles.list}>
                    {typeRecs.map((rec) => (
                      <RecommendationRow
                        key={`${rec.fixtureId}-${rec.type}`}
                        recommendation={rec}
                        showPrice={config.showPrice}
                        showPositionGap={Boolean(config.showPositionGap)}
                        linkToFixture={false}
                        isElite={eliteKeys.has(elitePickKey(rec.fixtureId, rec.type))}
                      />
                    ))}
                  </div>
                </section>
              );
            })}
          </div>
        ) : (
          <div className={styles.empty}>
            <h2>No recommendations for this fixture</h2>
            <p>
              There are no picks for this match yet. It may not have enough data, or none of
              the engines produced a recommendation.
            </p>
            <Link to="/" className={styles.backLink}>
              Browse all recommendations
            </Link>
          </div>
        )}
      </section>
    </div>
  );
}
