import { Link } from 'react-router-dom';
import type { Fixture } from '../types';
import { EARLY_KICKOFF_WARNING, isEarlyKickoffUk } from '../utils/kickoff';
import { EarlyKickoffBadge } from './EarlyKickoffWarning';
import styles from './FixtureCard.module.css';

interface Props {
  fixture: Fixture;
}

export function FixtureCard({ fixture }: Props) {
  const matchDate = new Date(fixture.dateUnix * 1000);
  const formattedTime = matchDate.toLocaleTimeString('en-GB', {
    hour: '2-digit',
    minute: '2-digit',
  });
  const isEarlyKickoff = isEarlyKickoffUk(fixture.dateUnix);

  return (
    <Link
      to={`/fixtures/${fixture.id}`}
      className={`${styles.card} ${isEarlyKickoff ? styles.cardEarly : ''}`}
    >
      <div
        className={`${styles.time} ${isEarlyKickoff ? styles.timeEarly : ''}`}
        title={isEarlyKickoff ? EARLY_KICKOFF_WARNING : undefined}
      >
        <span>{formattedTime}</span>
        {isEarlyKickoff && <EarlyKickoffBadge />}
      </div>
      <div className={styles.teams}>
        <span className={styles.team}>{fixture.homeTeamName}</span>
        <span className={styles.vs}>vs</span>
        <span className={styles.team}>{fixture.awayTeamName}</span>
      </div>
      <div className={styles.meta}>
        {(fixture.stadiumName || fixture.stadium) && (
          <span className={styles.stadium}>{fixture.stadiumName || fixture.stadium}</span>
        )}
        {fixture.gameWeek != null && (
          <span className={styles.gameweek}>GW{fixture.gameWeek}</span>
        )}
      </div>
    </Link>
  );
}
