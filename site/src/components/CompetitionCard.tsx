import { useState } from 'react';
import { Link } from 'react-router-dom';
import type { Competition } from '../types';
import { EARLY_KICKOFF_WARNING, isEarlyKickoffUk } from '../utils/kickoff';
import { EarlyKickoffBadge } from './EarlyKickoffWarning';
import styles from './CompetitionCard.module.css';

interface CompetitionCardProps {
  competition: Competition;
}

export function CompetitionCard({ competition }: CompetitionCardProps) {
  const [isExpanded, setIsExpanded] = useState(false);

  const formatDate = (dateString: string) => {
    const date = new Date(dateString);
    return date.toLocaleDateString('en-GB', {
      weekday: 'short',
      day: 'numeric',
      month: 'short',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  return (
    <div className={styles.card}>
      <button
        className={styles.header}
        onClick={() => setIsExpanded(!isExpanded)}
        aria-expanded={isExpanded}
      >
        <div className={styles.leagueInfo}>
          {competition.logoUrl && (
            <img
              src={competition.logoUrl}
              alt={competition.name}
              className={styles.logo}
            />
          )}
          <span className={styles.name}>{competition.name}</span>
        </div>
        <div className={styles.fixtureCount}>
          <span className={styles.countIcon}>📅</span>
          <span className={styles.countNumber}>{competition.fixtureCount}</span>
          <span className={`${styles.chevron} ${isExpanded ? styles.expanded : ''}`}>
            ▼
          </span>
        </div>
      </button>

      {isExpanded && (
        <div className={styles.fixtureList}>
          {competition.fixtures.length > 0 ? (
            competition.fixtures.map((fixture) => {
              const isEarlyKickoff = isEarlyKickoffUk(fixture.matchDate);
              return (
                <Link
                  key={fixture.fixtureId}
                  to={`/fixtures/${fixture.fixtureId}`}
                  className={`${styles.fixture} ${isEarlyKickoff ? styles.fixtureEarly : ''}`}
                >
                  <span className={styles.teams}>
                    {fixture.homeTeam} <span className={styles.vs}>vs</span> {fixture.awayTeam}
                  </span>
                  <span
                    className={`${styles.date} ${isEarlyKickoff ? styles.dateEarly : ''}`}
                    title={isEarlyKickoff ? EARLY_KICKOFF_WARNING : undefined}
                  >
                    <span>{formatDate(fixture.matchDate)}</span>
                    {isEarlyKickoff && <EarlyKickoffBadge />}
                  </span>
                  <span className={styles.fixtureCue} aria-hidden="true">→</span>
                </Link>
              );
            })
          ) : (
            <p className={styles.noFixtures}>No upcoming fixtures</p>
          )}
        </div>
      )}
    </div>
  );
}
