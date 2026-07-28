interface IconProps {
  size?: number;
  className?: string;
}

export function BttsIcon({ size = 20, className }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" className={className}>
      <circle cx="8" cy="12" r="5" stroke="#22c55e" strokeWidth="2" fill="none"/>
      <circle cx="16" cy="12" r="5" stroke="#22c55e" strokeWidth="2" fill="none"/>
      <circle cx="8" cy="12" r="2" fill="#22c55e"/>
      <circle cx="16" cy="12" r="2" fill="#22c55e"/>
    </svg>
  );
}

export function OverGoalsIcon({ size = 20, className }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" className={className}>
      <circle cx="12" cy="12" r="9" stroke="#3b82f6" strokeWidth="2"/>
      <path d="M12 8V16M8 12H16" stroke="#3b82f6" strokeWidth="2" strokeLinecap="round"/>
    </svg>
  );
}

export function UnderGoalsIcon({ size = 20, className }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" className={className}>
      <circle cx="12" cy="12" r="9" stroke="#6366f1" strokeWidth="2"/>
      <path d="M8 12H16" stroke="#6366f1" strokeWidth="2" strokeLinecap="round"/>
    </svg>
  );
}

export function BookingPointsIcon({ size = 20, className }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" className={className}>
      <rect x="6" y="4" width="12" height="16" rx="1" fill="#eab308" stroke="#ca8a04" strokeWidth="1"/>
      <path d="M9 8H15M9 11H15M9 14H13" stroke="white" strokeWidth="1.5" strokeLinecap="round"/>
    </svg>
  );
}

export function ValueBetIcon({ size = 20, className }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" className={className}>
      <circle cx="12" cy="12" r="9" stroke="#10b981" strokeWidth="2"/>
      <path d="M12 7V12L15 15" stroke="#10b981" strokeWidth="2" strokeLinecap="round"/>
      <path d="M8 17L12 12L16 17" stroke="#10b981" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
    </svg>
  );
}

export function FormMismatchIcon({ size = 20, className }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" className={className}>
      <path d="M4 18L8 10L12 14L16 6L20 12" stroke="#f97316" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
      <circle cx="20" cy="12" r="2" fill="#f97316"/>
    </svg>
  );
}

export function LosingFormIcon({ size = 20, className }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" className={className}>
      <path d="M4 6L8 14L12 10L16 18L20 12" stroke="#ef4444" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
      <circle cx="16" cy="18" r="2" fill="#ef4444"/>
    </svg>
  );
}

export function OverCornersIcon({ size = 20, className }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" className={className}>
      <path d="M4 4V20H20" stroke="#8b5cf6" strokeWidth="2" strokeLinecap="round"/>
      <path d="M4 4L12 12" stroke="#8b5cf6" strokeWidth="2" strokeLinecap="round"/>
      <circle cx="16" cy="8" r="3" stroke="#8b5cf6" strokeWidth="2" fill="none"/>
    </svg>
  );
}

export function UnderCornersIcon({ size = 20, className }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" className={className}>
      <path d="M4 4V20H20" stroke="#a855f7" strokeWidth="2" strokeLinecap="round"/>
      <path d="M4 4L10 10" stroke="#a855f7" strokeWidth="2" strokeLinecap="round"/>
    </svg>
  );
}

export function CleanSheetIcon({ size = 20, className }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" className={className}>
      <path d="M12 3L4 7V12C4 16.4 7.4 20.4 12 21C16.6 20.4 20 16.4 20 12V7L12 3Z" stroke="#14b8a6" strokeWidth="2" fill="none"/>
      <path d="M9 12L11 14L15 10" stroke="#14b8a6" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
    </svg>
  );
}

export function FirstHalfIcon({ size = 20, className }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" className={className}>
      <circle cx="12" cy="12" r="9" stroke="#0ea5e9" strokeWidth="2"/>
      <path d="M12 3V12H3" stroke="#0ea5e9" strokeWidth="2" strokeLinecap="round"/>
      <text x="12" y="16" fontSize="8" fontWeight="bold" fill="#0ea5e9" textAnchor="middle">1</text>
    </svg>
  );
}

export function SecondHalfIcon({ size = 20, className }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" className={className}>
      <circle cx="12" cy="12" r="9" stroke="#06b6d4" strokeWidth="2"/>
      <path d="M12 21V12H21" stroke="#06b6d4" strokeWidth="2" strokeLinecap="round"/>
      <text x="12" y="16" fontSize="8" fontWeight="bold" fill="#06b6d4" textAnchor="middle">2</text>
    </svg>
  );
}

export function MatchResultIcon({ size = 20, className }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" className={className}>
      <path d="M12 2L15 8L22 9L17 14L18 21L12 18L6 21L7 14L2 9L9 8L12 2Z" stroke="#fbbf24" strokeWidth="2" fill="#fbbf24" fillOpacity="0.2"/>
    </svg>
  );
}

export function HomeAwayIcon({ size = 20, className }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" className={className}>
      <path d="M3 12L12 4L21 12" stroke="#ec4899" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
      <path d="M5 10V20H19V10" stroke="#ec4899" strokeWidth="2" strokeLinecap="round"/>
      <path d="M9 20V14H15V20" stroke="#ec4899" strokeWidth="2"/>
    </svg>
  );
}

export function DrawIcon({ size = 20, className }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" className={className}>
      <path d="M8 8C8 5.8 9.8 4 12 4C14.2 4 16 5.8 16 8C16 9.5 15 10.8 13.5 11.5L13 12" stroke="#64748b" strokeWidth="2" strokeLinecap="round"/>
      <path d="M11 8C11 10.2 12.8 12 15 12C17.2 12 19 13.8 19 16C19 18.2 17.2 20 15 20" stroke="#64748b" strokeWidth="2" strokeLinecap="round"/>
      <circle cx="9" cy="16" r="4" stroke="#64748b" strokeWidth="2" fill="none"/>
    </svg>
  );
}

export function DoubleChanceIcon({ size = 20, className }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" className={className}>
      <rect x="3" y="6" width="8" height="12" rx="2" stroke="#7c3aed" strokeWidth="2"/>
      <rect x="13" y="6" width="8" height="12" rx="2" stroke="#7c3aed" strokeWidth="2"/>
      <circle cx="7" cy="12" r="2" fill="#7c3aed"/>
      <circle cx="17" cy="12" r="2" fill="#7c3aed"/>
    </svg>
  );
}

export function ResultBttsIcon({ size = 20, className }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" className={className}>
      <circle cx="8" cy="8" r="4" stroke="#059669" strokeWidth="2"/>
      <circle cx="16" cy="16" r="4" stroke="#059669" strokeWidth="2"/>
      <path d="M11 5L19 13" stroke="#059669" strokeWidth="2" strokeLinecap="round"/>
      <circle cx="8" cy="8" r="1.5" fill="#059669"/>
      <circle cx="16" cy="16" r="1.5" fill="#059669"/>
    </svg>
  );
}

export function TopVsBottomIcon({ size = 20, className }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" className={className}>
      <path d="M12 4L16 8H8L12 4Z" fill="#22c55e" stroke="#22c55e" strokeWidth="1"/>
      <path d="M12 20L8 16H16L12 20Z" fill="#ef4444" stroke="#ef4444" strokeWidth="1"/>
      <path d="M12 8V16" stroke="#94a3b8" strokeWidth="2" strokeDasharray="2 2"/>
    </svg>
  );
}

export function SoccerBallIcon({ size = 20, className }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" className={className}>
      <circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="2"/>
      <path d="M12 3V7M12 17V21M3 12H7M17 12H21" stroke="currentColor" strokeWidth="1.5"/>
      <circle cx="12" cy="12" r="3" stroke="currentColor" strokeWidth="1.5"/>
    </svg>
  );
}

export function StarFilledIcon({ size = 20, className }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" className={className}>
      <path 
        d="M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26L12 2Z" 
        fill="#fbbf24" 
        stroke="#f59e0b" 
        strokeWidth="1"
      />
    </svg>
  );
}

export function StarOutlineIcon({ size = 20, className }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" className={className}>
      <path 
        d="M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26L12 2Z" 
        stroke="currentColor" 
        strokeWidth="2" 
        strokeLinecap="round" 
        strokeLinejoin="round"
      />
    </svg>
  );
}

export function WarningIcon({ size = 20, className }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" className={className}>
      <path 
        d="M12 2L2 22H22L12 2Z" 
        stroke="#f59e0b" 
        strokeWidth="2" 
        fill="#fef3c7"
        strokeLinejoin="round"
      />
      <path d="M12 10V14" stroke="#f59e0b" strokeWidth="2" strokeLinecap="round"/>
      <circle cx="12" cy="17" r="1" fill="#f59e0b"/>
    </svg>
  );
}

export function CloseIcon({ size = 20, className }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" className={className}>
      <path d="M6 6L18 18M18 6L6 18" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
    </svg>
  );
}

export function ChartIcon({ size = 20, className }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" className={className}>
      <path d="M4 20V10M10 20V4M16 20V14M22 20V8" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
    </svg>
  );
}

export function SearchIcon({ size = 20, className }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" className={className}>
      <circle cx="10" cy="10" r="6" stroke="currentColor" strokeWidth="2"/>
      <path d="M14.5 14.5L20 20" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
    </svg>
  );
}

export function RefreshIcon({ size = 20, className }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" className={className}>
      <path d="M21 12C21 16.9706 16.9706 21 12 21C7.02944 21 3 16.9706 3 12C3 7.02944 7.02944 3 12 3C15.3019 3 18.1885 4.77814 19.7545 7.42909" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
      <path d="M21 3V8H16" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
    </svg>
  );
}

export function ExportIcon({ size = 20, className }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" className={className}>
      <path d="M12 3V15M12 3L7 8M12 3L17 8" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
      <path d="M3 15V19C3 20.1046 3.89543 21 5 21H19C20.1046 21 21 20.1046 21 19V15" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
    </svg>
  );
}

export function ImageIcon({ size = 20, className }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" className={className}>
      <rect x="3" y="3" width="18" height="18" rx="2" stroke="currentColor" strokeWidth="2"/>
      <circle cx="8" cy="8" r="2" stroke="currentColor" strokeWidth="2"/>
      <path d="M21 15L16 10L6 21" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
    </svg>
  );
}

export function TextIcon({ size = 20, className }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" className={className}>
      <path d="M4 7V4H20V7" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
      <path d="M12 4V20" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
      <path d="M8 20H16" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
    </svg>
  );
}

export function DownloadIcon({ size = 20, className }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" className={className}>
      <path d="M12 3V15M12 15L7 10M12 15L17 10" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
      <path d="M3 15V19C3 20.1046 3.89543 21 5 21H19C20.1046 21 21 20.1046 21 19V15" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
    </svg>
  );
}

export function CopyIcon({ size = 20, className }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" className={className}>
      <rect x="9" y="9" width="12" height="12" rx="2" stroke="currentColor" strokeWidth="2"/>
      <path d="M5 15V5C5 3.89543 5.89543 3 7 3H15" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
    </svg>
  );
}

export function CheckIcon({ size = 20, className }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" className={className}>
      <path d="M5 12L10 17L20 7" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
    </svg>
  );
}

export const RecommendationIcons: Record<string, React.FC<IconProps>> = {
  BTTS: BttsIcon,
  OVER_GOALS: OverGoalsIcon,
  UNDER_GOALS: UnderGoalsIcon,
  BOOKING_POINTS: BookingPointsIcon,
  VALUE_BET: ValueBetIcon,
  WINNING_FORM_MISMATCH: FormMismatchIcon,
  LOSING_FORM_MISMATCH: LosingFormIcon,
  OVER_CORNERS: OverCornersIcon,
  UNDER_CORNERS: UnderCornersIcon,
  CLEAN_SHEET: CleanSheetIcon,
  FIRST_HALF_GOALS: FirstHalfIcon,
  SECOND_HALF_GOALS: SecondHalfIcon,
  MATCH_RESULT: MatchResultIcon,
  HOME_AWAY_SPECIALIST: HomeAwayIcon,
  DRAW: DrawIcon,
  DOUBLE_CHANCE: DoubleChanceIcon,
  RESULT_BTTS: ResultBttsIcon,
  TOP_VS_BOTTOM: TopVsBottomIcon,
};
