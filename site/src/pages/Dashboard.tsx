import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { leagueService } from '../services/api';
import { CountrySection, DashboardSkeleton } from '../components';
import { WarningIcon } from '../components/Icons';
import type { CountryGroup } from '../types';
import styles from './Dashboard.module.css';

export default function Dashboard() {
  const { data: overview, isLoading, isError, error, refetch } = useQuery({
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
    return (
      <div className={styles.dashboard}>
        <header className={styles.header}>
          <h1 className={styles.title}>Fixtures</h1>
        </header>
        <DashboardSkeleton />
      </div>
    );
  }

  if (isError) {
    return (
      <div className={styles.dashboard}>
        <header className={styles.header}>
          <h1 className={styles.title}>Fixtures</h1>
        </header>
        <div className={styles.error}>
          <div className={styles.errorIcon}><WarningIcon size={48} /></div>
          <h2 className={styles.errorTitle}>Failed to load fixtures</h2>
          <p className={styles.errorMessage}>
            {error instanceof Error ? error.message : 'An unexpected error occurred.'}
          </p>
          <button className={styles.retryButton} onClick={() => refetch()}>
            Try Again
          </button>
        </div>
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
        <h1 className={styles.title}>Fixtures</h1>
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
