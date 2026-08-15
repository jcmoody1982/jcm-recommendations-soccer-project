import { useState } from 'react';
import { Link } from 'react-router-dom';
import type { Recommendation } from '../types';
import { useShortlist } from '../contexts/ShortlistContext';
import { formatKickoffDisplay } from '../utils/kickoff';
import { formatFactorEntries } from '../utils/recommendationFactors';
import { SECTION_CONFIG } from '../utils/recommendationSections';
import { formatTopVsBottomDisplay } from '../utils/topVsBottomDisplay';
import styles from './RecommendationRow.module.css';

interface Props {
  recommendation: Recommendation;
  showPrice?: boolean;
  showPositionGap?: boolean;
  /** When false, fixture names are plain text (e.g. already on the fixture page). */
  linkToFixture?: boolean;
}

const ConfidenceIcon = ({ level }: { level: string }) => {
  if (level === 'STRONG') {
    return (
      <svg width="16" height="16" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
        <circle cx="10" cy="10" r="9" fill="var(--confidence-strong)" stroke="var(--confidence-strong)" strokeWidth="1"/>
        <path d="M6 10L9 13L14 7" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
      </svg>
    );
  }
  if (level === 'MODERATE') {
    return (
      <svg width="16" height="16" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
        <circle cx="10" cy="10" r="9" fill="var(--confidence-moderate)" stroke="var(--confidence-moderate)" strokeWidth="1"/>
        <path d="M10 6V10" stroke="white" strokeWidth="2" strokeLinecap="round"/>
        <circle cx="10" cy="13" r="1" fill="white"/>
      </svg>
    );
  }
  return (
    <svg width="16" height="16" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
      <circle cx="10" cy="10" r="9" fill="#9ca3af" stroke="#6b7280" strokeWidth="1"/>
      <path d="M7 7L13 13M13 7L7 13" stroke="white" strokeWidth="2" strokeLinecap="round"/>
    </svg>
  );
};

function rowClassName(showPrice: boolean, showPositionGap: boolean): string {
  if (showPrice && showPositionGap) return styles.rowPriceGap;
  if (showPositionGap) return styles.rowGap;
  if (showPrice) return styles.row;
  return styles.rowNoPrice;
}

export function RecommendationRow({
  recommendation,
  showPrice = true,
  showPositionGap = false,
  linkToFixture = true,
}: Props) {
  const { isShortlisted, toggleShortlist } = useShortlist();
  const isInShortlist = isShortlisted(recommendation.fixtureId, recommendation.type);
  const [expanded, setExpanded] = useState(false);

  const handleToggleShortlist = (e: React.MouseEvent) => {
    e.stopPropagation();
    toggleShortlist(recommendation.fixtureId, recommendation.type);
  };

  const toggleExpanded = (e: React.MouseEvent) => {
    e.stopPropagation();
    setExpanded((prev) => !prev);
  };

  const topVsBottom =
    showPositionGap || recommendation.type === 'TOP_VS_BOTTOM'
      ? formatTopVsBottomDisplay(recommendation)
      : null;

  const fixtureLabel =
    topVsBottom?.fixtureLabel
    ?? `${recommendation.homeTeamName} vs ${recommendation.awayTeamName}`;
  const selectionLabel = topVsBottom?.selectionLabel ?? recommendation.market;
  const gapLabel = topVsBottom?.gapLabel ?? null;

  const fixturePath = `/fixtures/${recommendation.fixtureId}`;
  const kickoff = formatKickoffDisplay(recommendation.matchDateUnix);

  const config = SECTION_CONFIG[recommendation.type];
  const score = Number(recommendation.score || 0).toFixed(0);
  const scoreUnit = config?.scoreUnit ?? '%';
  const scoreLabel = config?.scoreLabel ?? 'Score';

  const factorEntries = formatFactorEntries(recommendation.factors);
  const hasWhy =
    Boolean(recommendation.description?.trim()) || factorEntries.length > 0;

  const rowClass = rowClassName(showPrice, showPositionGap);

  const details = expanded && hasWhy && (
    <div className={styles.details} id={`why-${recommendation.fixtureId}-${recommendation.type}`}>
      {recommendation.description?.trim() && (
        <p className={styles.detailsSummary}>{recommendation.description.trim()}</p>
      )}
      {factorEntries.length > 0 && (
        <dl className={styles.factorList}>
          {factorEntries.map((entry) => (
            <div key={entry.label} className={styles.factorItem}>
              <dt className={styles.factorLabel}>{entry.label}</dt>
              <dd className={styles.factorValue}>{entry.value}</dd>
            </div>
          ))}
        </dl>
      )}
    </div>
  );

  return (
    <div className={styles.item}>
      {/* Desktop row layout */}
      <div className={rowClass}>
        <span className={styles.sentiment} title={recommendation.confidence}>
          <ConfidenceIcon level={recommendation.confidence} />
        </span>
        <span
          className={styles.league}
          title={recommendation.leagueName || undefined}
        >
          {recommendation.leagueImage ? (
            <img
              src={recommendation.leagueImage}
              alt=""
              className={styles.leagueIcon}
            />
          ) : (
            <span className={styles.leaguePlaceholder} aria-hidden>
              ⚽
            </span>
          )}
        </span>
        <span
          className={`${styles.datetime} ${styles[`urgency_${kickoff.urgency}`] || ''}`}
          title={kickoff.title}
        >
          <span className={styles.date}>{kickoff.primaryLabel}</span>
          <span className={styles.time}>{kickoff.timeLabel}</span>
        </span>
        <div className={styles.fixtureBlock}>
          {linkToFixture ? (
            <Link to={fixturePath} className={styles.fixtureLink}>
              {fixtureLabel}
            </Link>
          ) : (
            <span className={styles.fixture}>{fixtureLabel}</span>
          )}
          {recommendation.leagueName && (
            <span className={styles.leagueName}>{recommendation.leagueName}</span>
          )}
        </div>
        <span className={styles.market}>{selectionLabel}</span>
        {showPositionGap && (
          <span className={styles.positionGap} title="League position gap">
            {gapLabel ?? '—'}
          </span>
        )}
        {showPrice && (
          <span className={styles.price}>
            {recommendation.odds ? recommendation.odds.toFixed(2) : '-'}
          </span>
        )}
        <span className={styles.score} title={scoreLabel}>
          {score}
          {scoreUnit}
        </span>
        <div className={styles.actions}>
          {hasWhy && (
            <button
              type="button"
              className={`${styles.whyButton} ${expanded ? styles.whyButtonOpen : ''}`}
              onClick={toggleExpanded}
              aria-expanded={expanded}
              aria-controls={`why-${recommendation.fixtureId}-${recommendation.type}`}
              title={expanded ? 'Hide why' : 'Why this pick'}
            >
              Why
            </button>
          )}
          <button
            className={`${styles.starButton} ${isInShortlist ? styles.starred : ''}`}
            onClick={handleToggleShortlist}
            title={isInShortlist ? 'Remove from shortlist' : 'Add to shortlist'}
          >
            {isInShortlist ? '★' : '☆'}
          </button>
        </div>
      </div>

      {/* Mobile card layout */}
      <div className={styles.mobileCard}>
        <div className={styles.mobileCardHeader}>
          <span className={styles.mobileSentiment}>
            <ConfidenceIcon level={recommendation.confidence} />
          </span>
          {recommendation.leagueImage ? (
            <img
              src={recommendation.leagueImage}
              alt=""
              className={styles.mobileLeagueIcon}
            />
          ) : (
            <span className={styles.mobileLeaguePlaceholder} aria-hidden>
              ⚽
            </span>
          )}
          <div className={styles.mobileFixtureBlock}>
            {linkToFixture ? (
              <Link to={fixturePath} className={styles.mobileFixtureLink}>
                {fixtureLabel}
              </Link>
            ) : (
              <span className={styles.mobileFixture}>{fixtureLabel}</span>
            )}
            <div className={styles.mobileMetaLine}>
              {recommendation.leagueName && (
                <span className={styles.mobileLeagueName}>{recommendation.leagueName}</span>
              )}
              <span
                className={`${styles.mobileKickoff} ${styles[`urgency_${kickoff.urgency}`] || ''}`}
                title={kickoff.title}
              >
                {kickoff.primaryLabel} {kickoff.timeLabel}
              </span>
            </div>
          </div>
          <button
            className={`${styles.mobileStarButton} ${isInShortlist ? styles.starred : ''}`}
            onClick={handleToggleShortlist}
            title={isInShortlist ? 'Remove from shortlist' : 'Add to shortlist'}
          >
            {isInShortlist ? '★' : '☆'}
          </button>
        </div>
        <div className={styles.mobileCardFooter}>
          <div className={styles.mobilePickLine}>
            <span className={styles.mobileSelection}>{selectionLabel}</span>
            {showPositionGap && gapLabel && (
              <span className={styles.mobileGap}>{gapLabel}</span>
            )}
            {showPrice && recommendation.odds != null && (
              <span className={styles.mobilePrice}>{recommendation.odds.toFixed(2)}</span>
            )}
            <span className={styles.mobileScore} title={scoreLabel}>
              {score}
              {scoreUnit}
            </span>
          </div>
          {hasWhy && (
            <button
              type="button"
              className={`${styles.mobileWhyButton} ${expanded ? styles.whyButtonOpen : ''}`}
              onClick={toggleExpanded}
              aria-expanded={expanded}
            >
              {expanded ? 'Hide' : 'Why'}
            </button>
          )}
        </div>
      </div>

      {details}
    </div>
  );
}
