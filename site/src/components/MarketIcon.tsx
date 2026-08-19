import type { RecommendationType } from '../types';
import styles from './MarketIcon.module.css';

type MarketIconId = RecommendationType | 'ELITE';

interface Props {
  type: MarketIconId;
  className?: string;
  title?: string;
}

/**
 * Soft duotone badge icons for recommendation market sections (Style B).
 * Colours come from CSS vars so light/dark themes stay consistent.
 */
export function MarketIcon({ type, className, title }: Props) {
  const label = title ?? type.replaceAll('_', ' ');
  return (
    <span className={`${styles.badge} ${className ?? ''}`} role="img" aria-label={label}>
      <svg viewBox="0 0 40 40" className={styles.svg} aria-hidden="true">
        <circle cx="20" cy="20" r="18" className={styles.disc} />
        {glyph(type)}
      </svg>
    </span>
  );
}

function glyph(type: MarketIconId) {
  switch (type) {
    case 'MATCH_RESULT':
      return (
        <g>
          <path
            className={styles.fillPrimary}
            d="M20 8.5l2.1 4.3 4.7.7-3.4 3.3.8 4.7L20 19.2l-4.2 2.3.8-4.7-3.4-3.3 4.7-.7z"
          />
          <path className={styles.fillSecondary} d="M13 28.5h14v1.8H13zm2.2-1.8h9.6v1.8h-9.6z" />
        </g>
      );
    case 'BTTS':
      return (
        <g>
          <circle cx="14.5" cy="20" r="6.2" className={styles.strokePrimary} strokeWidth="1.8" />
          <circle cx="25.5" cy="20" r="6.2" className={styles.strokeSecondary} strokeWidth="1.8" />
          <path
            className={styles.fillPrimary}
            d="M14.5 15.2l1.4 2.8 3 .4-2.2 2.1.5 3-2.7-1.5-2.7 1.5.5-3-2.2-2.1 3-.4z"
          />
          <path
            className={styles.fillSecondary}
            d="M25.5 15.2l1.4 2.8 3 .4-2.2 2.1.5 3-2.7-1.5-2.7 1.5.5-3-2.2-2.1 3-.4z"
          />
        </g>
      );
    case 'DOUBLE_CHANCE':
      return (
        <g>
          <rect x="8.5" y="13" width="7" height="14" rx="1.5" className={styles.fillPrimary} />
          <rect x="16.5" y="13" width="7" height="14" rx="1.5" className={styles.fillSecondary} />
          <rect x="24.5" y="13" width="7" height="14" rx="1.5" className={styles.fillPrimary} opacity="0.55" />
          <text x="12" y="23.2" textAnchor="middle" className={styles.mark}>1</text>
          <text x="20" y="23.2" textAnchor="middle" className={styles.mark}>X</text>
          <text x="28" y="23.2" textAnchor="middle" className={styles.mark}>2</text>
        </g>
      );
    case 'RESULT_BTTS':
      return (
        <g>
          <circle cx="15" cy="20" r="7" className={styles.strokePrimary} strokeWidth="1.8" />
          <circle cx="15" cy="20" r="2.2" className={styles.fillPrimary} />
          <circle cx="27" cy="20" r="5.2" className={styles.strokeSecondary} strokeWidth="1.6" />
          <path
            className={styles.fillSecondary}
            d="M27 16.2l1.1 2.2 2.4.3-1.7 1.7.4 2.4-2.2-1.2-2.2 1.2.4-2.4-1.7-1.7 2.4-.3z"
          />
        </g>
      );
    case 'TOP_VS_BOTTOM':
      return (
        <g>
          <path className={styles.fillPrimary} d="M20 8.5l5.5 8.5H14.5z" />
          <path className={styles.fillSecondary} d="M20 31.5l-5.5-8.5h11z" />
          <rect x="18.6" y="16.5" width="2.8" height="7" className={styles.fillPrimary} opacity="0.45" />
        </g>
      );
    case 'DRAW':
      return (
        <g>
          <rect x="10" y="17.2" width="20" height="2.6" rx="1.2" className={styles.fillPrimary} />
          <rect x="10" y="22.2" width="20" height="2.6" rx="1.2" className={styles.fillSecondary} />
        </g>
      );
    case 'FIRST_HALF_GOALS':
      return (
        <g>
          <rect x="9" y="11" width="22" height="18" rx="3" className={styles.strokeSecondary} strokeWidth="1.6" />
          <path className={styles.strokeSecondary} d="M20 11v18" strokeWidth="1.6" />
          <text x="14.5" y="24" textAnchor="middle" className={styles.halfMark}>1</text>
        </g>
      );
    case 'SECOND_HALF_GOALS':
      return (
        <g>
          <rect x="9" y="11" width="22" height="18" rx="3" className={styles.strokeSecondary} strokeWidth="1.6" />
          <path className={styles.strokeSecondary} d="M20 11v18" strokeWidth="1.6" />
          <text x="25.5" y="24" textAnchor="middle" className={styles.halfMark}>2</text>
        </g>
      );
    case 'VALUE_BET':
      return (
        <g>
          <path
            className={styles.fillPrimary}
            d="M12 12.5h11.5l5.5 5.5V28a1.5 1.5 0 0 1-1.5 1.5H12A1.5 1.5 0 0 1 10.5 28V14A1.5 1.5 0 0 1 12 12.5z"
          />
          <circle cx="16.5" cy="18.5" r="2.2" className={styles.fillSecondary} />
        </g>
      );
    case 'OVER_GOALS':
      return (
        <g>
          <path className={styles.strokeSecondary} d="M10 26.5h20" strokeWidth="1.8" />
          <path className={styles.strokePrimary} d="M20 25V12.5" strokeWidth="2" />
          <path className={styles.strokePrimary} d="M15.5 17L20 12.5 24.5 17" strokeWidth="2" />
        </g>
      );
    case 'OVER_15_GOALS':
      return (
        <g>
          <path className={styles.strokeSecondary} d="M10 24.5h20" strokeWidth="1.8" />
          <path className={styles.strokePrimary} d="M20 23V11" strokeWidth="2" />
          <path className={styles.strokePrimary} d="M15.5 15.5L20 11 24.5 15.5" strokeWidth="2" />
          <text x="20" y="33" textAnchor="middle" className={styles.halfMark} style={{ fontSize: '7.5px' }}>1.5</text>
        </g>
      );
    case 'OVER_25_GOALS':
      return (
        <g>
          <path className={styles.strokeSecondary} d="M10 24.5h20" strokeWidth="1.8" />
          <path className={styles.strokePrimary} d="M20 23V11" strokeWidth="2" />
          <path className={styles.strokePrimary} d="M15.5 15.5L20 11 24.5 15.5" strokeWidth="2" />
          <text x="20" y="33" textAnchor="middle" className={styles.halfMark} style={{ fontSize: '7.5px' }}>2.5</text>
        </g>
      );
    case 'PLAYER_TO_SCORE':
      return (
        <g>
          <circle cx="20" cy="16" r="4" className={styles.strokePrimary} />
          <path className={styles.strokePrimary} d="M20 20.5v6.5M16 32.5l4-5.5 4 5.5" strokeWidth="1.8" />
          <circle cx="28.5" cy="12.5" r="2.4" className={styles.fillSecondary} />
        </g>
      );
    case 'PLAYER_TO_ASSIST':
      return (
        <g>
          <circle cx="14.5" cy="16" r="3.4" className={styles.strokePrimary} />
          <path className={styles.strokePrimary} d="M14.5 19.8v6.2M11.5 31.5l3-5.4 3 5.4" strokeWidth="1.8" />
          <path className={styles.strokeSecondary} d="M19 18.5h8.5" strokeWidth="1.8" />
          <path className={styles.strokeSecondary} d="M24.5 15.5L28.5 18.5 24.5 21.5" strokeWidth="1.8" />
        </g>
      );
    case 'UNDER_GOALS':
      return (
        <g>
          <path className={styles.strokeSecondary} d="M10 13.5h20" strokeWidth="1.8" />
          <path className={styles.strokePrimary} d="M20 15v12.5" strokeWidth="2" />
          <path className={styles.strokePrimary} d="M15.5 23L20 27.5 24.5 23" strokeWidth="2" />
        </g>
      );
    case 'CLEAN_SHEET':
      return (
        <g>
          <path
            className={styles.fillPrimary}
            d="M13 16.5c0-2.4 2-4.2 4.4-3.8 1-.8 2.4-.8 3.4 0 2.4-.4 4.4 1.4 4.4 3.8v3.2c0 3.4-2.6 6.2-6.1 6.2S13 23.1 13 19.7z"
          />
          <path className={styles.strokeSecondary} d="M16 20.5h8M17.2 23h5.6" strokeWidth="1.5" />
        </g>
      );
    case 'BOOKING_POINTS':
      return (
        <g>
          <rect x="13.5" y="10.5" width="13" height="19" rx="2" className={styles.booking} />
          <rect x="16" y="14" width="8" height="1.6" rx="0.6" className={styles.bookingMark} />
          <rect x="16" y="17.5" width="8" height="1.6" rx="0.6" className={styles.bookingMark} />
        </g>
      );
    case 'OVER_CORNERS':
      return (
        <g>
          <path className={styles.strokeSecondary} d="M11 29V12.5h16.5" strokeWidth="2" />
          <path className={styles.fillPrimary} d="M22 18.5l5-5 1.2 4.2-4.2 1.2z" />
        </g>
      );
    case 'UNDER_CORNERS':
      return (
        <g>
          <path className={styles.strokeSecondary} d="M11 11v16.5h16.5" strokeWidth="2" />
          <path className={styles.fillPrimary} d="M22 21.5l5 5 1.2-4.2-4.2-1.2z" />
        </g>
      );
    case 'HOME_AWAY_SPECIALIST':
      return (
        <g>
          <path className={styles.fillPrimary} d="M8.5 22.5L20 12l11.5 10.5H27v7H13v-7z" />
          <rect x="17.2" y="23.5" width="5.6" height="6" className={styles.fillSecondary} />
        </g>
      );
    case 'WINNING_FORM_MISMATCH':
      return (
        <g>
          <path
            className={styles.fillPrimary}
            d="M20 9.5c2.2 3.2 6.2 5.4 6.2 10.2A6.2 6.2 0 0 1 20 26a6.2 6.2 0 0 1-6.2-6.3c0-4.8 4-7 6.2-10.2z"
          />
          <path
            className={styles.fillSecondary}
            d="M20 15.5c1.1 1.6 3 2.7 3 5a3 3 0 1 1-6 0c0-2.3 1.9-3.4 3-5z"
          />
        </g>
      );
    case 'LOSING_FORM_MISMATCH':
      return (
        <g>
          <path className={styles.strokeSecondary} d="M11 12.5v16M11 28.5h18" strokeWidth="1.8" />
          <path className={styles.strokePrimary} d="M14 16.5l5 5 4-3.5 6 7" strokeWidth="2" />
        </g>
      );
    case 'ELITE':
      return (
        <path
          className={styles.fillPrimary}
          d="M22.4 8.2L13.6 20.2h6.2L17.4 31.8l10.4-13.4h-6.4L22.4 8.2z"
        />
      );
    default:
      return <circle cx="20" cy="20" r="5" className={styles.fillPrimary} />;
  }
}
