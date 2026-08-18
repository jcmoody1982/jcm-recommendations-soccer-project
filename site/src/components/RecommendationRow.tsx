import { useState } from 'react';
import { Link } from 'react-router-dom';
import type { Recommendation } from '../types';
import { useShortlist } from '../contexts/ShortlistContext';
import {
  EARLY_KICKOFF_WARNING,
  formatKickoffDisplay,
  isEarlyKickoffUk,
} from '../utils/kickoff';
import { formatFactorEntries } from '../utils/recommendationFactors';
import { SECTION_CONFIG } from '../utils/recommendationSections';
import { formatTopVsBottomDisplay } from '../utils/topVsBottomDisplay';
import { EarlyKickoffBadge, EarlyKickoffStrip } from './EarlyKickoffWarning';
import { EliteBolt } from './EliteBolt';
import styles from './RecommendationRow.module.css';

interface Props {
  recommendation: Recommendation;
  showPrice?: boolean;
  showPositionGap?: boolean;
  /** When false, fixture names are plain text (e.g. already on the fixture page). */
  linkToFixture?: boolean;
  isElite?: boolean;
}

const ConfidenceIcon = ({ level, isElite }: { level: string; isElite?: boolean }) => {
  if (isElite) {
    return <EliteBolt variant="badge" size={16} />;
  }
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
  isElite = false,
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
  const isEarlyKickoff = isEarlyKickoffUk(recommendation.matchDateUnix);
  const kickoffTitle = isEarlyKickoff
    ? `${kickoff.title} · ${EARLY_KICKOFF_WARNING}`
    : kickoff.title;

  const config = SECTION_CONFIG[recommendation.type];
  const score = Number(recommendation.score || 0).toFixed(0);
  const scoreUnit = config?.scoreUnit ?? '%';
  const scoreLabel = config?.scoreLabel ?? 'Score';

  const factorEntries = formatFactorEntries(recommendation.factors, undefined, {
    type: recommendation.type,
    recommendation,
  });
  const hasInfo =
    Boolean(recommendation.description?.trim()) || factorEntries.length > 0;

  const rowClass = rowClassName(showPrice, showPositionGap);

  const details = expanded && hasInfo && (
    <div className={styles.details} id={`info-${recommendation.fixtureId}-${recommendation.type}`}>
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
    <div className={`${styles.item} ${isEarlyKickoff ? styles.itemEarly : ''}`}>
      {/* Desktop row layout */}
      <div className={rowClass}>
        <span className={styles.sentiment} title={isElite ? 'Elite pick' : recommendation.confidence}>
          <ConfidenceIcon level={recommendation.confidence} isElite={isElite} />
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
          className={`${styles.datetime} ${styles[`urgency_${kickoff.urgency}`] || ''} ${
            isEarlyKickoff ? styles.datetimeEarly : ''
          }`}
          title={kickoffTitle}
        >
          <span className={styles.date}>{kickoff.primaryLabel}</span>
          <span className={styles.timeRow}>
            <span className={styles.time}>{kickoff.timeLabel}</span>
            {isEarlyKickoff && <EarlyKickoffBadge />}
          </span>
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
          {hasInfo && (
            <button
              type="button"
              className={`${styles.whyButton} ${expanded ? styles.whyButtonOpen : ''}`}
              onClick={toggleExpanded}
              aria-expanded={expanded}
              aria-controls={`info-${recommendation.fixtureId}-${recommendation.type}`}
              title={expanded ? 'Hide info' : 'Show info'}
            >
              Info
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
          <span className={styles.mobileSentiment} title={isElite ? 'Elite pick' : recommendation.confidence}>
            <ConfidenceIcon level={recommendation.confidence} isElite={isElite} />
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
            {recommendation.leagueName && (
              <span className={styles.mobileLeagueName}>{recommendation.leagueName}</span>
            )}
          </div>
          <button
            className={`${styles.mobileStarButton} ${isInShortlist ? styles.starred : ''}`}
            onClick={handleToggleShortlist}
            title={isInShortlist ? 'Remove from shortlist' : 'Add to shortlist'}
          >
            {isInShortlist ? '★' : '☆'}
          </button>
        </div>
        <div className={styles.mobileCardBody}>
          <div className={styles.mobileInfo}>
            <span className={styles.mobileLabel}>When</span>
            <span
              className={`${styles.mobileValue} ${styles[`urgency_${kickoff.urgency}`] || ''} ${
                isEarlyKickoff ? styles.mobileValueEarly : ''
              }`}
              title={kickoffTitle}
            >
              <span>
                {kickoff.primaryLabel} {kickoff.timeLabel}
              </span>
              {isEarlyKickoff && <EarlyKickoffBadge />}
            </span>
          </div>
          <div className={styles.mobileInfo}>
            <span className={styles.mobileLabel}>Selection</span>
            <span className={styles.mobileValue}>{selectionLabel}</span>
          </div>
          {showPositionGap && (
            <div className={styles.mobileInfo}>
              <span className={styles.mobileLabel}>Pos gap</span>
              <span className={styles.mobileGap}>{gapLabel ?? '—'}</span>
            </div>
          )}
          {showPrice && recommendation.odds && (
            <div className={styles.mobileInfo}>
              <span className={styles.mobileLabel}>Price</span>
              <span className={styles.mobileValue}>{recommendation.odds.toFixed(2)}</span>
            </div>
          )}
          <div className={styles.mobileInfo}>
            <span className={styles.mobileLabel}>{scoreLabel}</span>
            <span className={styles.mobileScore}>
              {score}
              {scoreUnit}
            </span>
          </div>
        </div>
        {hasInfo && (
          <button
            type="button"
            className={`${styles.mobileWhyButton} ${expanded ? styles.whyButtonOpen : ''}`}
            onClick={toggleExpanded}
            aria-expanded={expanded}
          >
            {expanded ? 'Hide info' : 'Info'}
          </button>
        )}
      </div>

      {isEarlyKickoff && (
        <div className={styles.earlyStripDesktop}>
          <EarlyKickoffStrip flushTop />
        </div>
      )}

      {details}
    </div>
  );
}
