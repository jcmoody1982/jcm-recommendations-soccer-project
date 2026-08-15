import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
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
import {
  SECTION_ORDER,
  sectionTitle,
} from '../utils/recommendationSections';
import styles from './Recommendations.module.css';

/** Fetch horizon when viewing all kickoffs (Soon/Today/Tomorrow set days automatically). */
const HORIZON_OPTIONS = [3, 7] as const;
const DEFAULT_HORIZON = 3;
const KICKOFF_WINDOWS: KickoffWindow[] = ['all', 'soon', 'today', 'tomorrow'];
const SORT_OPTIONS: KickoffSort[] = ['score', 'kickoff'];

/** tipped = Strong + Moderate (hides WEAK by default). */
type ConfidenceFilter = 'tipped' | 'strong' | 'moderate' | 'all';

const CONFIDENCE_OPTIONS: Array<{ value: ConfidenceFilter; label: string }> = [
  { value: 'tipped', label: 'Strong + Moderate' },
  { value: 'strong', label: 'Strong' },
  { value: 'moderate', label: 'Moderate' },
  { value: 'all', label: 'All (incl. Weak)' },
];

function parseHorizon(value: string | null): number {
  const n = value == null ? NaN : Number(value);
  return (HORIZON_OPTIONS as readonly number[]).includes(n) ? n : DEFAULT_HORIZON;
}

/** Minimum fetch window needed for a kickoff chip. */
function daysForKickoff(window: KickoffWindow): number {
  if (window === 'soon' || window === 'today') return 1;
  if (window === 'tomorrow') return 3;
  return DEFAULT_HORIZON;
}

function parseKickoff(value: string | null): KickoffWindow {
  if (value && (KICKOFF_WINDOWS as string[]).includes(value)) {
    return value as KickoffWindow;
  }
  return 'all';
}

function parseSort(value: string | null): KickoffSort {
  if (value && (SORT_OPTIONS as string[]).includes(value)) {
    return value as KickoffSort;
  }
  return 'score';
}

function parseConfidence(value: string | null): ConfidenceFilter {
  if (value === 'strong' || value === 'moderate' || value === 'all' || value === 'tipped') {
    return value;
  }
  return 'tipped';
}

function parseType(value: string | null): RecommendationType | 'ALL' {
  if (!value || value === 'ALL') return 'ALL';
  if ((SECTION_ORDER as string[]).includes(value)) {
    return value as RecommendationType;
  }
  return 'ALL';
}

function matchesConfidence(confidence: string, filter: ConfidenceFilter): boolean {
  const band = confidence?.toUpperCase();
  if (filter === 'all') return true;
  if (filter === 'strong') return band === 'STRONG';
  if (filter === 'moderate') return band === 'MODERATE';
  // tipped: hide WEAK
  return band === 'STRONG' || band === 'MODERATE';
}

export default function Recommendations() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [searchOpen, setSearchOpen] = useState(() => Boolean(searchParams.get('q')));
  const searchInputRef = useRef<HTMLInputElement>(null);

  const searchQuery = searchParams.get('q') || '';
  const selectedLeague = searchParams.get('league') || 'all';
  const kickoffWindow = parseKickoff(searchParams.get('kickoff'));
  const sortBy = parseSort(searchParams.get('sort'));
  const confidenceFilter = parseConfidence(searchParams.get('confidence'));
  const typeFilter = parseType(searchParams.get('type'));
  const horizon = parseHorizon(searchParams.get('days'));
  // Kickoff chips own the time window; horizon only applies to "All kickoffs".
  const daysAhead =
    kickoffWindow === 'all' ? horizon : daysForKickoff(kickoffWindow);

  const updateParams = useCallback(
    (patch: Record<string, string | null | undefined>) => {
      setSearchParams(
        (prev) => {
          const next = new URLSearchParams(prev);
          for (const [key, value] of Object.entries(patch)) {
            const isDefault =
              (key === 'days' && (value == null || value === String(DEFAULT_HORIZON)))
              || (key === 'q' && (value == null || value === ''))
              || (key === 'league' && (value == null || value === 'all'))
              || (key === 'kickoff' && (value == null || value === 'all'))
              || (key === 'sort' && (value == null || value === 'score'))
              || (key === 'confidence' && (value == null || value === 'tipped'))
              || (key === 'type' && (value == null || value === 'ALL'));
            if (value == null || value === '' || isDefault) {
              next.delete(key);
            } else {
              next.set(key, value);
            }
          }
          return next;
        },
        { replace: true }
      );
    },
    [setSearchParams]
  );

  useEffect(() => {
    if (searchOpen) {
      searchInputRef.current?.focus();
    }
  }, [searchOpen]);

  const closeSearch = () => {
    setSearchOpen(false);
    if (searchQuery) {
      updateParams({ q: null });
    }
  };

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

  useEffect(() => {
    if (
      selectedLeague !== 'all'
      && availableLeagues.length > 0
      && !availableLeagues.some((l) => l.id === selectedLeague)
    ) {
      updateParams({ league: null });
    }
  }, [selectedLeague, availableLeagues, updateParams]);

  const kickoffCounts = useMemo(() => {
    if (!groupedRecommendations) {
      return { soon: 0, today: 0, tomorrow: 0 };
    }

    const all = Object.values(groupedRecommendations).flat();
    const seenSoon = new Set<number>();
    const seenToday = new Set<number>();
    const seenTomorrow = new Set<number>();

    for (const rec of all) {
      if (!matchesConfidence(rec.confidence, confidenceFilter)) continue;
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
  }, [groupedRecommendations, confidenceFilter]);

  const filteredRecommendations = useMemo(() => {
    if (!groupedRecommendations) return null;

    const filtered: Record<RecommendationType, Recommendation[]> =
      {} as Record<RecommendationType, Recommendation[]>;
    const searchLower = searchQuery.toLowerCase().trim();
    const nowMs = Date.now();

    for (const type of SECTION_ORDER) {
      if (typeFilter !== 'ALL' && type !== typeFilter) {
        filtered[type] = [];
        continue;
      }

      const recs = groupedRecommendations[type] || [];
      const next = recs.filter((rec: Recommendation) => {
        if (!matchesConfidence(rec.confidence, confidenceFilter)) {
          return false;
        }

        if (searchLower) {
          const matchesSearch =
            rec.homeTeamName.toLowerCase().includes(searchLower)
            || rec.awayTeamName.toLowerCase().includes(searchLower)
            || (rec.leagueName && rec.leagueName.toLowerCase().includes(searchLower));
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
  }, [
    groupedRecommendations,
    searchQuery,
    selectedLeague,
    kickoffWindow,
    sortBy,
    confidenceFilter,
    typeFilter,
  ]);

  const typeCounts = useMemo(() => {
    if (!groupedRecommendations) return {} as Record<RecommendationType, number>;

    const searchLower = searchQuery.toLowerCase().trim();
    const nowMs = Date.now();
    const counts = {} as Record<RecommendationType, number>;

    for (const type of SECTION_ORDER) {
      const recs = groupedRecommendations[type] || [];
      counts[type] = recs.filter((rec: Recommendation) => {
        if (!matchesConfidence(rec.confidence, confidenceFilter)) return false;
        if (searchLower) {
          const matchesSearch =
            rec.homeTeamName.toLowerCase().includes(searchLower)
            || rec.awayTeamName.toLowerCase().includes(searchLower)
            || (rec.leagueName && rec.leagueName.toLowerCase().includes(searchLower));
          if (!matchesSearch) return false;
        }
        if (selectedLeague !== 'all' && String(rec.leagueId) !== selectedLeague) {
          return false;
        }
        if (!matchesKickoffWindow(rec.matchDateUnix, kickoffWindow, nowMs)) {
          return false;
        }
        return true;
      }).length;
    }

    return counts;
  }, [
    groupedRecommendations,
    searchQuery,
    selectedLeague,
    kickoffWindow,
    confidenceFilter,
  ]);

  const typesWithPicks = useMemo(
    () => SECTION_ORDER.filter((type) => (typeCounts[type] || 0) > 0),
    [typeCounts]
  );

  useEffect(() => {
    if (
      typeFilter !== 'ALL'
      && typesWithPicks.length > 0
      && !typesWithPicks.includes(typeFilter)
    ) {
      updateParams({ type: null });
    }
  }, [typeFilter, typesWithPicks, updateParams]);

  const totalCount = filteredRecommendations
    ? Object.values(filteredRecommendations).reduce((sum, recs) => sum + recs.length, 0)
    : 0;

  const clearFilters = () => {
    updateParams({
      q: null,
      league: null,
      kickoff: null,
      days: null,
      sort: null,
      confidence: null,
      type: null,
    });
  };

  const hasActiveFilters =
    Boolean(searchQuery)
    || selectedLeague !== 'all'
    || kickoffWindow !== 'all'
    || (kickoffWindow === 'all' && horizon !== DEFAULT_HORIZON)
    || confidenceFilter !== 'tipped'
    || typeFilter !== 'ALL';

  const setKickoffWindow = (value: KickoffWindow) => {
    const patch: Record<string, string | null> = { kickoff: value };
    // Keep `days` so returning to "All kickoffs" restores the chosen horizon.
    if (value !== 'all' && sortBy === 'score') {
      patch.sort = 'kickoff';
    }
    updateParams(patch);
  };

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <h1 className={styles.title}>Recommendations</h1>
        <div className={styles.headerActions}>
          {searchOpen ? (
            <div className={styles.headerSearch}>
              <input
                ref={searchInputRef}
                type="search"
                placeholder="Search teams or leagues..."
                value={searchQuery}
                onChange={(e) => updateParams({ q: e.target.value || null })}
                className={styles.headerSearchInput}
                aria-label="Search teams or leagues"
              />
              <button
                type="button"
                className={styles.headerIconButton}
                onClick={closeSearch}
                aria-label="Close search"
              >
                ✕
              </button>
            </div>
          ) : (
            <button
              type="button"
              className={`${styles.headerIconButton} ${searchQuery ? styles.headerIconActive : ''}`}
              onClick={() => setSearchOpen(true)}
              aria-label="Search teams or leagues"
              title="Search"
            >
              <svg width="20" height="20" viewBox="0 0 20 20" fill="none" aria-hidden>
                <circle cx="8.5" cy="8.5" r="5.5" stroke="currentColor" strokeWidth="1.75" />
                <path d="M12.5 12.5L16.5 16.5" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" />
              </svg>
            </button>
          )}
        </div>
      </header>

      <div className={styles.filters}>
        <select
          value={selectedLeague}
          onChange={(e) => updateParams({ league: e.target.value })}
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
              {
                value: 'soon' as const,
                label: kickoffCounts.soon > 0 ? `Soon (${kickoffCounts.soon})` : 'Soon',
              },
              {
                value: 'today' as const,
                label: kickoffCounts.today > 0 ? `Today (${kickoffCounts.today})` : 'Today',
              },
              {
                value: 'tomorrow' as const,
                label:
                  kickoffCounts.tomorrow > 0
                    ? `Tomorrow (${kickoffCounts.tomorrow})`
                    : 'Tomorrow',
              },
              { value: 'all' as const, label: 'All kickoffs' },
            ]
          ).map((option) => (
            <button
              key={option.value}
              type="button"
              className={`${styles.kickoffChip} ${
                kickoffWindow === option.value ? styles.kickoffChipActive : ''
              }`}
              onClick={() => setKickoffWindow(option.value)}
            >
              {option.label}
            </button>
          ))}
        </div>

        {kickoffWindow === 'all' && (
          <div className={styles.horizonGroup} role="group" aria-label="Look-ahead horizon">
            <span className={styles.horizonLabel}>Horizon</span>
            {HORIZON_OPTIONS.map((days) => (
              <button
                key={days}
                type="button"
                className={`${styles.kickoffChip} ${
                  horizon === days ? styles.kickoffChipActive : ''
                }`}
                onClick={() => updateParams({ days: String(days) })}
              >
                {days}d
              </button>
            ))}
          </div>
        )}

        <select
          value={sortBy}
          onChange={(e) => updateParams({ sort: e.target.value })}
          className={styles.select}
          aria-label="Sort recommendations"
        >
          <option value="score">Sort: Best score</option>
          <option value="kickoff">Sort: Soonest kickoff</option>
        </select>

        {hasActiveFilters && (
          <button className={styles.clearFilters} onClick={clearFilters}>
            Clear filters
          </button>
        )}
      </div>

      <div className={styles.filterBlock}>
        <div className={styles.filterGroup}>
          <span className={styles.filterLabel}>Confidence</span>
          <div className={styles.filterRowScroll} role="group" aria-label="Confidence filter">
            {CONFIDENCE_OPTIONS.map((option) => (
              <button
                key={option.value}
                type="button"
                className={`${styles.chip} ${
                  confidenceFilter === option.value ? styles.chipActive : ''
                }`}
                onClick={() => updateParams({ confidence: option.value })}
              >
                {option.label}
              </button>
            ))}
          </div>
        </div>

        {typesWithPicks.length > 0 && (
          <div className={styles.filterGroup}>
            <label className={styles.filterLabel} htmlFor="market-filter">
              Market Filter
            </label>
            <select
              id="market-filter"
              value={typeFilter}
              onChange={(e) =>
                updateParams({
                  type: e.target.value === 'ALL' ? null : e.target.value,
                })
              }
              className={`${styles.select} ${styles.marketSelect}`}
              aria-label="Market filter"
            >
              <option value="ALL">
                All markets ({typesWithPicks.reduce((sum, t) => sum + (typeCounts[t] || 0), 0)})
              </option>
              {typesWithPicks.map((type) => (
                <option key={type} value={type}>
                  {sectionTitle(type)} ({typeCounts[type]})
                </option>
              ))}
            </select>
          </div>
        )}
      </div>

      {isLoading ? (
        <RecommendationsPageSkeleton />
      ) : isError ? (
        <div className={styles.error}>
          <div className={styles.errorIcon}>!</div>
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
            if (typeFilter !== 'ALL' && type !== typeFilter) {
              return null;
            }
            const recommendations = filteredRecommendations[type] || [];
            if (recommendations.length === 0) {
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
              : confidenceFilter === 'strong'
                ? 'No Strong recommendations match these filters.'
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
              updateParams({ kickoff: 'soon', sort: 'kickoff' });
            }}
          >
            {kickoffCounts.soon} starting within 3 hours
          </button>
        )}
      </footer>
    </div>
  );
}
