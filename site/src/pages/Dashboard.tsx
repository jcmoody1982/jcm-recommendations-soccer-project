import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { leagueService } from '../services/api';
import { CountrySection } from '../components/CountrySection';
import type { CountryGroup } from '../types';
import styles from './Dashboard.module.css';

export default function Dashboard() {
  const { data: overview, isLoading, error } = useQuery({
    queryKey: ['league-overview'],
    queryFn: () => leagueService.getOverview(),
  });

  const filteredCountries = useMemo(() => {
    if (!overview) return [];

    return overview.countries
      .map((country): CountryGroup => ({
        ...country,
        competitions: country.competitions.filter((comp) => comp.fixtureCount > 0),
      }))
      .filter((country) => country.competitions.length > 0);
  }, [overview]);

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

  if (!overview || filteredCountries.length === 0) {
    return (
      <div className={styles.empty}>
        <h2>No upcoming fixtures</h2>
        <p>Check back later for upcoming matches.</p>
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
        {filteredCountries.map((countryGroup) => (
          <CountrySection key={countryGroup.country} countryGroup={countryGroup} />
        ))}
      </div>
    </div>
  );
}
