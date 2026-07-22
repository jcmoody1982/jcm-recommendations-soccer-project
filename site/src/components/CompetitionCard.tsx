import { useState } from 'react';
import type { Competition } from '../types';
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
            competition.fixtures.map((fixture) => (
              <div key={fixture.fixtureId} className={styles.fixture}>
                <span className={styles.teams}>
                  {fixture.homeTeam} <span className={styles.vs}>vs</span> {fixture.awayTeam}
                </span>
                <span className={styles.date}>{formatDate(fixture.matchDate)}</span>
              </div>
            ))
          ) : (
            <p className={styles.noFixtures}>No upcoming fixtures</p>
          )}
        </div>
      )}
    </div>
  );
}
