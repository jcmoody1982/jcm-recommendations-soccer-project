import { useState, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { recommendationService } from '../services/api';
import { RecommendationSection, RecommendationsPageSkeleton } from '../components';
import type { Recommendation, RecommendationType } from '../types';
import {
  compareByKickoff,
  compareByScoreDesc,
  matchesKickoffWindow,
  type KickoffSort,
  type KickoffWindow,
} from '../utils/kickoff';
import styles from './Recommendations.module.css';

const SECTION_ORDER: RecommendationType[] = [
  'MATCH_RESULT',
  'BTTS',
  'DOUBLE_CHANCE',
  'RESULT_BTTS',
  'TOP_VS_BOTTOM',
  'DRAW',
  'FIRST_HALF_GOALS',
  'SECOND_HALF_GOALS',
  'VALUE_BET',
  'OVER_GOALS',
  'UNDER_GOALS',
  'CLEAN_SHEET',
  'BOOKING_POINTS',
  'OVER_CORNERS',
  'UNDER_CORNERS',
  'HOME_AWAY_SPECIALIST',
  'WINNING_FORM_MISMATCH',
  'LOSING_FORM_MISMATCH',
];

export default function Recommendations() {
  const [daysAhead, setDaysAhead] = useState(7);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedLeague, setSelectedLeague] = useState<string>('all');
  const [kickoffWindow, setKickoffWindow] = useState<KickoffWindow>('all');
  const [sortBy, setSortBy] = useState<KickoffSort>('score');
  const [hideEmptySections, setHideEmptySections] = useState(true);

  const { data: groupedRecommendations, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['recommendations-grouped', daysAhead],
    queryFn: () => recommendationService.getGrouped(daysAhead),
  });

  const availableLeagues = useMemo(() => {
    if (!groupedRecommendations) return [];
    const leagueMap = new Map<string, string>();

    Object.values(groupedRecommendations).flat().forEach((rec: Recommendation) => {
      if (rec.leagueName && rec.leagueId) {
        leagueMap.set(String(rec.leagueId), rec.leagueName);
      }
    });

    return Array.from(leagueMap.entries())
      .map(([id, name]) => ({ id, name }))
      .sort((a, b) => a.name.localeCompare(b.name));
  }, [groupedRecommendations]);

  const kickoffCounts = useMemo(() => {
    if (!groupedRecommendations) {
      return { soon: 0, today: 0, tomorrow: 0 };
    }

    const all = Object.values(groupedRecommendations).flat();
    const seenSoon = new Set<number>();
    const seenToday = new Set<number>();
    const seenTomorrow = new Set<number>();

    for (const rec of all) {
      if (matchesKickoffWindow(rec.matchDateUnix, 'soon')) {
        seenSoon.add(rec.fixtureId);
      }
      if (matchesKickoffWindow(rec.matchDateUnix, 'today')) {
        seenToday.add(rec.fixtureId);
      }
      if (matchesKickoffWindow(rec.matchDateUnix, 'tomorrow')) {
        seenTomorrow.add(rec.fixtureId);
      }
    }

    return {
      soon: seenSoon.size,
      today: seenToday.size,
      tomorrow: seenTomorrow.size,
    };
  }, [groupedRecommendations]);

  const filteredRecommendations = useMemo(() => {
    if (!groupedRecommendations) return null;

    const filtered: Record<RecommendationType, Recommendation[]> =
      {} as Record<RecommendationType, Recommendation[]>;
    const searchLower = searchQuery.toLowerCase().trim();
    const nowMs = Date.now();

    for (const type of SECTION_ORDER) {
      const recs = groupedRecommendations[type] || [];
      const next = recs.filter((rec: Recommendation) => {
        if (searchLower) {
          const matchesSearch =
            rec.homeTeamName.toLowerCase().includes(searchLower) ||
            rec.awayTeamName.toLowerCase().includes(searchLower) ||
            (rec.leagueName && rec.leagueName.toLowerCase().includes(searchLower));
          if (!matchesSearch) return false;
        }

        if (selectedLeague !== 'all' && String(rec.leagueId) !== selectedLeague) {
          return false;
        }

        if (!matchesKickoffWindow(rec.matchDateUnix, kickoffWindow, nowMs)) {
          return false;
        }

        return true;
      });

      next.sort((a, b) => {
        if (sortBy === 'kickoff') {
          const kickoffDiff = compareByKickoff(a.matchDateUnix, b.matchDateUnix);
          if (kickoffDiff !== 0) return kickoffDiff;
          return compareByScoreDesc(a.score, b.score);
        }

        const scoreDiff = compareByScoreDesc(a.score, b.score);
        if (scoreDiff !== 0) return scoreDiff;
        return compareByKickoff(a.matchDateUnix, b.matchDateUnix);
      });

      filtered[type] = next;
    }

    return filtered;
  }, [groupedRecommendations, searchQuery, selectedLeague, kickoffWindow, sortBy]);

  const totalCount = filteredRecommendations
    ? Object.values(filteredRecommendations).reduce((sum, recs) => sum + recs.length, 0)
    : 0;

  const clearFilters = () => {
    setSearchQuery('');
    setSelectedLeague('all');
    setKickoffWindow('all');
  };

  const hasActiveFilters =
    Boolean(searchQuery) || selectedLeague !== 'all' || kickoffWindow !== 'all';

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <h1 className={styles.title}>Recommendations</h1>
        <div className={styles.headerRight}>
          <select
            value={daysAhead}
            onChange={(e) => setDaysAhead(Number(e.target.value))}
            className={styles.select}
            aria-label="Days ahead"
          >
            <option value={0.5}>Next 12 hours</option>
            <option value={1}>Next 24 hours</option>
            <option value={3}>Next 3 days</option>
            <option value={7}>Next 7 days</option>
          </select>
        </div>
      </header>

      <div className={styles.filters}>
        <div className={styles.searchContainer}>
          <span className={styles.searchIcon}>🔍</span>
          <input
            type="text"
            placeholder="Search teams or leagues..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className={styles.searchInput}
          />
          {searchQuery && (
            <button
              className={styles.clearSearch}
              onClick={() => setSearchQuery('')}
              aria-label="Clear search"
            >
              ✕
            </button>
          )}
        </div>

        <select
          value={selectedLeague}
          onChange={(e) => setSelectedLeague(e.target.value)}
          className={styles.select}
          aria-label="League filter"
        >
          <option value="all">All Leagues</option>
          {availableLeagues.map((league) => (
            <option key={league.id} value={league.id}>
              {league.name}
            </option>
          ))}
        </select>

        <div className={styles.kickoffGroup} role="group" aria-label="Kickoff window">
          {(
            [
              { value: 'all', label: 'All kickoffs' },
              {
                value: 'soon',
                label: kickoffCounts.soon > 0 ? `Soon (${kickoffCounts.soon})` : 'Soon',
              },
              {
                value: 'today',
                label: kickoffCounts.today > 0 ? `Today (${kickoffCounts.today})` : 'Today',
              },
              {
                value: 'tomorrow',
                label:
                  kickoffCounts.tomorrow > 0
                    ? `Tomorrow (${kickoffCounts.tomorrow})`
                    : 'Tomorrow',
              },
            ] as const
          ).map((option) => (
            <button
              key={option.value}
              type="button"
              className={`${styles.kickoffChip} ${
                kickoffWindow === option.value ? styles.kickoffChipActive : ''
              }`}
              onClick={() => {
                setKickoffWindow(option.value);
                if (option.value !== 'all' && sortBy === 'score') {
                  setSortBy('kickoff');
                }
              }}
            >
              {option.label}
            </button>
          ))}
        </div>

        <select
          value={sortBy}
          onChange={(e) => setSortBy(e.target.value as KickoffSort)}
          className={styles.select}
          aria-label="Sort recommendations"
        >
          <option value="score">Sort: Best score</option>
          <option value="kickoff">Sort: Soonest kickoff</option>
        </select>

        <label className={styles.checkboxLabel}>
          <input
            type="checkbox"
            checked={hideEmptySections}
            onChange={(e) => setHideEmptySections(e.target.checked)}
            className={styles.checkbox}
          />
          <span>Hide empty sections</span>
        </label>

        {hasActiveFilters && (
          <button className={styles.clearFilters} onClick={clearFilters}>
            Clear filters
          </button>
        )}
      </div>

      {isLoading ? (
        <RecommendationsPageSkeleton />
      ) : isError ? (
        <div className={styles.error}>
          <div className={styles.errorIcon}>⚠️</div>
          <h2 className={styles.errorTitle}>Failed to load recommendations</h2>
          <p className={styles.errorMessage}>
            {error instanceof Error ? error.message : 'An unexpected error occurred. Please try again.'}
          </p>
          <button className={styles.retryButton} onClick={() => refetch()}>
            Try Again
          </button>
        </div>
      ) : filteredRecommendations && totalCount > 0 ? (
        <div className={styles.sections}>
          {SECTION_ORDER.map((type) => {
            const recommendations = filteredRecommendations[type] || [];
            if (hideEmptySections && recommendations.length === 0) {
              return null;
            }
            return (
              <RecommendationSection
                key={type}
                type={type}
                recommendations={recommendations}
                initialItems={5}
              />
            );
          })}
        </div>
      ) : (
        <div className={styles.empty}>
          <p>
            {kickoffWindow !== 'all'
              ? 'No recommendations in that kickoff window.'
              : 'No recommendations found.'}
          </p>
          {hasActiveFilters && (
            <button className={styles.clearFiltersAlt} onClick={clearFilters}>
              Clear filters
            </button>
          )}
        </div>
      )}

      <footer className={styles.footer}>
        <span className={styles.totalCount}>{totalCount} total picks</span>
        {kickoffCounts.soon > 0 && kickoffWindow === 'all' && (
          <button
            type="button"
            className={styles.soonHint}
            onClick={() => {
              setKickoffWindow('soon');
              setSortBy('kickoff');
            }}
          >
            {kickoffCounts.soon} starting within 3 hours
          </button>
        )}
      </footer>
    </div>
  );
}
