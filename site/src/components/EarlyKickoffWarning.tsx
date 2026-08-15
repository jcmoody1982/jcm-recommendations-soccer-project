import {
  EARLY_KICKOFF_STRIP,
  EARLY_KICKOFF_WARNING,
} from '../utils/kickoff';
import styles from './EarlyKickoffWarning.module.css';

function EarlyWarningIcon({ size = 12 }: { size?: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 20 20"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden
    >
      <path
        d="M10 2.5L18 17H2L10 2.5Z"
        fill="currentColor"
        stroke="currentColor"
        strokeWidth="1.2"
        strokeLinejoin="round"
      />
      <path d="M10 8V12" stroke="white" strokeWidth="1.8" strokeLinecap="round" />
      <circle cx="10" cy="14.5" r="1" fill="white" />
    </svg>
  );
}

interface BadgeProps {
  className?: string;
}

/** Compact red EARLY KO chip used next to kickoff times. */
export function EarlyKickoffBadge({ className }: BadgeProps) {
  return (
    <span
      className={`${styles.badge} ${className ?? ''}`.trim()}
      title={EARLY_KICKOFF_WARNING}
    >
      <EarlyWarningIcon size={10} />
      EARLY KO
    </span>
  );
}

interface StripProps {
  /** Flush top edge when the strip sits under a card/row. */
  flushTop?: boolean;
  className?: string;
}

/** Desktop caution strip: Proceed with Extreme Caution. */
export function EarlyKickoffStrip({ flushTop = false, className }: StripProps) {
  return (
    <div
      className={`${styles.strip} ${flushTop ? styles.stripFlushTop : ''} ${className ?? ''}`.trim()}
      role="status"
    >
      <EarlyWarningIcon size={13} />
      <span>{EARLY_KICKOFF_STRIP}</span>
    </div>
  );
}
