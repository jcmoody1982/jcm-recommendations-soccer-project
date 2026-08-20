import { useState, useRef, useEffect, useMemo } from 'react';
import html2canvas from 'html2canvas';
import type { Recommendation, RecommendationType } from '../types';
import {
  EARLY_KICKOFF_STRIP,
  EARLY_KICKOFF_WARNING,
  isEarlyKickoffUk,
} from '../utils/kickoff';
import { SECTION_CONFIG, SECTION_ORDER } from '../utils/recommendationSections';
import { exportableImageSrc, loadImageAsDataUrl } from '../utils/exportImageSrc';
import { EarlyKickoffBadge } from './EarlyKickoffWarning';
import { MarketIcon } from './MarketIcon';
import styles from './ExportModal.module.css';

interface ExportModalProps {
  isOpen: boolean;
  onClose: () => void;
  recommendations: Recommendation[];
}

type ExportFormat = 'image' | 'text';

interface ExportGroup {
  type: RecommendationType;
  title: string;
  scoreLabel: string;
  scoreUnit: string;
  picks: Recommendation[];
}

function formatDate(unix: number): string {
  const date = new Date(unix * 1000);
  return date.toLocaleDateString('en-GB', {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function formatScore(rec: Recommendation, scoreUnit: string): string {
  return `${Number(rec.score || 0).toFixed(0)}${scoreUnit}`;
}

function calculateCombinedOdds(recommendations: Recommendation[]): string {
  const validOdds = recommendations
    .map((r) => r.odds)
    .filter((odds): odds is number => odds !== null && odds > 0);

  if (validOdds.length === 0) return '-';

  const combined = validOdds.reduce((acc, odds) => acc * odds, 1);
  return combined.toFixed(2);
}

function groupRecommendations(recommendations: Recommendation[]): ExportGroup[] {
  const byType = recommendations.reduce(
    (acc, rec) => {
      const type = rec.type as RecommendationType;
      if (!acc[type]) acc[type] = [];
      acc[type].push(rec);
      return acc;
    },
    {} as Partial<Record<RecommendationType, Recommendation[]>>,
  );

  const orderedTypes = [
    ...SECTION_ORDER.filter((type) => byType[type]?.length),
    ...Object.keys(byType)
      .filter((type) => !(SECTION_ORDER as string[]).includes(type))
      .map((type) => type as RecommendationType),
  ];

  return orderedTypes.map((type) => {
    const config = SECTION_CONFIG[type];
    return {
      type,
      title: config?.title ?? String(type).replaceAll('_', ' '),
      scoreLabel: config?.scoreLabel ?? 'Score',
      scoreUnit: config?.scoreUnit ?? '%',
      picks: byType[type] ?? [],
    };
  });
}

function generateTextExport(recommendations: Recommendation[]): string {
  const lines = ['🎯 AccaBaccaGlory Picks', ''];
  const groups = groupRecommendations(recommendations);

  for (const group of groups) {
    lines.push(`▸ ${group.title}`);
    lines.push('');

    for (const rec of group.picks) {
      const early = isEarlyKickoffUk(rec.matchDateUnix);
      lines.push(`📅 ${formatDate(rec.matchDateUnix)}${early ? ' ⚠️ EARLY KO' : ''}`);
      if (early) {
        lines.push(`⚠️ ${EARLY_KICKOFF_WARNING}`);
      }
      if (rec.leagueName) {
        lines.push(`🏆 ${rec.leagueName}`);
      }
      lines.push(`⚽ ${rec.homeTeamName} vs ${rec.awayTeamName}`);
      const oddsStr = rec.odds ? ` @ ${rec.odds.toFixed(2)}` : '';
      const confStr = rec.confidence === 'STRONG' ? '🔥' : '⚡';
      lines.push(`${confStr} ${rec.market}${oddsStr}`);
      lines.push(`📊 ${group.scoreLabel}: ${formatScore(rec, group.scoreUnit)}`);
      lines.push('');
    }
  }

  const combinedOdds = calculateCombinedOdds(recommendations);
  if (combinedOdds !== '-') {
    lines.push(`💰 Combined Odds: ${combinedOdds}`);
    lines.push('');
  }

  lines.push(
    `Generated: ${new Date().toLocaleDateString('en-GB')} ${new Date().toLocaleTimeString('en-GB', {
      hour: '2-digit',
      minute: '2-digit',
    })}`,
  );
  lines.push('accabaccaglory.com');

  return lines.join('\n');
}

export function ExportModal({ isOpen, onClose, recommendations }: ExportModalProps) {
  const [format, setFormat] = useState<ExportFormat>('image');
  const [isExporting, setIsExporting] = useState(false);
  const [copied, setCopied] = useState(false);
  const [brandLogoSrc, setBrandLogoSrc] = useState('/logo.png');
  const [leagueLogoMap, setLeagueLogoMap] = useState<Record<string, string>>({});
  const previewRef = useRef<HTMLDivElement>(null);

  const groups = useMemo(() => groupRecommendations(recommendations), [recommendations]);

  useEffect(() => {
    if (!isOpen) {
      setCopied(false);
      return;
    }

    let cancelled = false;

    (async () => {
      const brandData = await loadImageAsDataUrl('/logo.png');
      if (!cancelled && brandData) {
        setBrandLogoSrc(brandData);
      }

      const uniqueLeagueUrls = [
        ...new Set(
          recommendations
            .map((r) => r.leagueImage)
            .filter((url): url is string => Boolean(url)),
        ),
      ];

      const entries = await Promise.all(
        uniqueLeagueUrls.map(async (url) => {
          const proxied = exportableImageSrc(url);
          if (!proxied) return null;
          const dataUrl = await loadImageAsDataUrl(proxied);
          return dataUrl ? ([url, dataUrl] as const) : null;
        }),
      );

      if (cancelled) return;

      const next: Record<string, string> = {};
      for (const entry of entries) {
        if (entry) next[entry[0]] = entry[1];
      }
      setLeagueLogoMap(next);
    })();

    return () => {
      cancelled = true;
    };
  }, [isOpen, recommendations]);

  useEffect(() => {
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    if (isOpen) {
      document.addEventListener('keydown', handleEscape);
      document.body.style.overflow = 'hidden';
    }
    return () => {
      document.removeEventListener('keydown', handleEscape);
      document.body.style.overflow = '';
    };
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  const handleImageExport = async () => {
    if (!previewRef.current) return;

    setIsExporting(true);
    try {
      const canvas = await html2canvas(previewRef.current, {
        backgroundColor: '#ffffff',
        scale: 2,
        logging: false,
        useCORS: true,
        allowTaint: false,
      });

      const link = document.createElement('a');
      link.download = `accabaccaglory-picks-${Date.now()}.png`;
      link.href = canvas.toDataURL('image/png');
      link.click();
    } catch (error) {
      console.error('Failed to export image:', error);
    } finally {
      setIsExporting(false);
    }
  };

  const handleTextExport = async () => {
    const text = generateTextExport(recommendations);
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch (error) {
      console.error('Failed to copy to clipboard:', error);
    }
  };

  const combinedOdds = calculateCombinedOdds(recommendations);

  return (
    <div className={styles.overlay} onClick={onClose}>
      <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
        <header className={styles.header}>
          <h2 className={styles.title}>Export Shortlist</h2>
          <button className={styles.closeButton} onClick={onClose} aria-label="Close">
            ✕
          </button>
        </header>

        <div className={styles.formatSelector}>
          <button
            className={`${styles.formatOption} ${format === 'image' ? styles.active : ''}`}
            onClick={() => setFormat('image')}
          >
            🖼️ Image
          </button>
          <button
            className={`${styles.formatOption} ${format === 'text' ? styles.active : ''}`}
            onClick={() => setFormat('text')}
          >
            📝 Text
          </button>
        </div>

        <div className={styles.previewContainer}>
          {format === 'image' ? (
            <div className={styles.imagePreviewWrapper}>
              <div ref={previewRef} className={styles.imagePreview}>
                <div className={styles.exportHeader}>
                  <img src={brandLogoSrc} alt="" className={styles.exportLogo} />
                  <span className={styles.exportBrand}>AccaBaccaGlory</span>
                </div>

                <div className={styles.exportPicks}>
                  {groups.map((group) => (
                    <div key={group.type} className={styles.exportGroup}>
                      <div className={styles.exportGroupHeader}>
                        <MarketIcon
                          type={group.type}
                          title={group.title}
                          className={styles.exportGroupIcon}
                        />
                        <span className={styles.exportGroupTitle}>{group.title}</span>
                      </div>

                      {group.picks.map((rec, index) => {
                        const isEarlyKickoff = isEarlyKickoffUk(rec.matchDateUnix);
                        const leagueLogo =
                          (rec.leagueImage && leagueLogoMap[rec.leagueImage]) ||
                          exportableImageSrc(rec.leagueImage);

                        return (
                          <div
                            key={`${rec.fixtureId}-${rec.type}-${index}`}
                            className={`${styles.exportPick} ${isEarlyKickoff ? styles.exportPickEarly : ''}`}
                          >
                            <div className={styles.exportPickHeader}>
                              {leagueLogo ? (
                                <div className={styles.exportLeagueIcon}>
                                  <img src={leagueLogo} alt="" crossOrigin="anonymous" />
                                </div>
                              ) : (
                                <div className={styles.exportLeaguePlaceholder} aria-hidden>
                                  ⚽
                                </div>
                              )}
                              <div className={styles.exportPickInfo}>
                                {rec.leagueName && (
                                  <span className={styles.exportLeagueName}>{rec.leagueName}</span>
                                )}
                                <span
                                  className={`${styles.exportDate} ${isEarlyKickoff ? styles.exportDateEarly : ''}`}
                                  title={isEarlyKickoff ? EARLY_KICKOFF_WARNING : undefined}
                                >
                                  <span>{formatDate(rec.matchDateUnix)}</span>
                                  {isEarlyKickoff && <EarlyKickoffBadge />}
                                </span>
                                <span className={styles.exportFixture}>
                                  {rec.homeTeamName} vs {rec.awayTeamName}
                                </span>
                                {isEarlyKickoff && (
                                  <span className={styles.exportEarlyNote}>{EARLY_KICKOFF_STRIP}</span>
                                )}
                              </div>
                            </div>
                            <div className={styles.exportPickMeta}>
                              <div className={styles.exportPickSelection}>
                                <span className={styles.exportConfidence}>
                                  {rec.confidence === 'STRONG' ? '🔥' : '⚡'}
                                </span>
                                <span className={styles.exportMarket}>{rec.market}</span>
                                {rec.odds && (
                                  <span className={styles.exportOdds}>@ {rec.odds.toFixed(2)}</span>
                                )}
                              </div>
                              <span
                                className={styles.exportScore}
                                title={group.scoreLabel}
                              >
                                {formatScore(rec, group.scoreUnit)}
                              </span>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  ))}
                </div>

                <div className={styles.exportFooter}>
                  {combinedOdds !== '-' && (
                    <div className={styles.exportCombined}>
                      Combined Odds: <strong>{combinedOdds}</strong>
                    </div>
                  )}
                  <div className={styles.exportWatermark}>accabaccaglory.com</div>
                </div>
              </div>
            </div>
          ) : (
            <div className={styles.textPreview}>
              <pre>{generateTextExport(recommendations)}</pre>
            </div>
          )}
        </div>

        <footer className={styles.footer}>
          {format === 'image' ? (
            <button
              className={styles.exportButton}
              onClick={handleImageExport}
              disabled={isExporting}
            >
              {isExporting ? 'Generating...' : '📥 Download Image'}
            </button>
          ) : (
            <button className={styles.exportButton} onClick={handleTextExport}>
              {copied ? '✓ Copied!' : '📋 Copy to Clipboard'}
            </button>
          )}
        </footer>
      </div>
    </div>
  );
}
