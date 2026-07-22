import { useQuery } from '@tanstack/react-query';
import { leagueService } from '../services/api';
import { CountrySection } from '../components/CountrySection';
import styles from './Dashboard.module.css';

export default function Dashboard() {
  const { data: overview, isLoading, error } = useQuery({
    queryKey: ['league-overview'],
    queryFn: () => leagueService.getOverview(),
  });

  if (isLoading) {
    return <div className={styles.loading}>Loading competitions...</div>;
  }

  if (error) {
    return (
      <div className={styles.error}>
        Failed to load competitions. Please try again later.
      </div>
    );
  }

  if (!overview || overview.countries.length === 0) {
    return (
      <div className={styles.empty}>
        <h2>No competitions configured</h2>
        <p>Check back later for upcoming fixtures.</p>
      </div>
    );
  }

  return (
    <div className={styles.dashboard}>
      <header className={styles.header}>
        <h1 className={styles.title}>Competitions</h1>
        <span className={styles.totalFixtures}>
          {overview.totalFixtures} upcoming fixtures
        </span>
      </header>

      <div className={styles.countryList}>
        {overview.countries.map((countryGroup) => (
          <CountrySection key={countryGroup.country} countryGroup={countryGroup} />
        ))}
      </div>
    </div>
  );
}
