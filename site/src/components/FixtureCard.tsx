import { Link } from 'react-router-dom';
import type { Fixture } from '../types';
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

  return (
    <Link to={`/fixtures/${fixture.id}`} className={styles.card}>
      <div className={styles.time}>{formattedTime}</div>
      <div className={styles.teams}>
        <span className={styles.team}>{fixture.homeTeamName}</span>
        <span className={styles.vs}>vs</span>
        <span className={styles.team}>{fixture.awayTeamName}</span>
      </div>
      <div className={styles.meta}>
        {fixture.stadium && <span className={styles.stadium}>{fixture.stadium}</span>}
        {fixture.gameWeek && <span className={styles.gameweek}>GW{fixture.gameWeek}</span>}
      </div>
    </Link>
  );
}
