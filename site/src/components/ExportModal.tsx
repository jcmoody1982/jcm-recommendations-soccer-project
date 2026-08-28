import { useState, useRef, useEffect } from 'react';
import html2canvas from 'html2canvas';
import type { Recommendation } from '../types';
import {
  EARLY_KICKOFF_STRIP,
  EARLY_KICKOFF_WARNING,
  isEarlyKickoffUk,
} from '../utils/kickoff';
import {
  formatExportPickLabels,
  formatExportSelectionLine,
} from '../utils/exportPickLabels';
import { EarlyKickoffBadge } from './EarlyKickoffWarning';
import styles from './ExportModal.module.css';

interface ExportModalProps {
  isOpen: boolean;
  onClose: () => void;
  recommendations: Recommendation[];
}

type ExportFormat = 'image' | 'text';

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

function calculateCombinedOdds(recommendations: Recommendation[]): string {
  const validOdds = recommendations
    .map(r => r.odds)
    .filter((odds): odds is number => odds !== null && odds > 0);
  
  if (validOdds.length === 0) return '-';
  
  const combined = validOdds.reduce((acc, odds) => acc * odds, 1);
  return combined.toFixed(2);
}

function generateTextExport(recommendations: Recommendation[]): string {
  const lines = ['🎯 AccaBaccaGlory Picks', ''];
  
  recommendations.forEach((rec) => {
    const labels = formatExportPickLabels(rec);
    const early = isEarlyKickoffUk(rec.matchDateUnix);
    const leagueSuffix = labels.leagueLabel ? ` · ${labels.leagueLabel}` : '';
    lines.push(`📅 ${formatDate(rec.matchDateUnix)}${leagueSuffix}${early ? ' ⚠️ EARLY KO' : ''}`);
    if (early) {
      lines.push(`⚠️ ${EARLY_KICKOFF_WARNING}`);
    }
    lines.push(`⚽ ${labels.fixtureLabel}`);
    const confStr = rec.confidence === 'STRONG' ? '🔥' : '⚡';
    lines.push(`${confStr} ${formatExportSelectionLine(rec, labels)}`);
    lines.push('');
  });
  
  const combinedOdds = calculateCombinedOdds(recommendations);
  if (combinedOdds !== '-') {
    lines.push(`💰 Combined Odds: ${combinedOdds}`);
    lines.push('');
  }
  
  lines.push(`Generated: ${new Date().toLocaleDateString('en-GB')} ${new Date().toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' })}`);
  lines.push('accabaccaglory.com');
  
  return lines.join('\n');
}

export function ExportModal({ isOpen, onClose, recommendations }: ExportModalProps) {
  const [format, setFormat] = useState<ExportFormat>('image');
  const [isExporting, setIsExporting] = useState(false);
  const [copied, setCopied] = useState(false);
  const previewRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!isOpen) {
      setCopied(false);
    }
  }, [isOpen]);

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
                  <img src="/logo.png" alt="" className={styles.exportLogo} />
                  <span className={styles.exportBrand}>AccaBaccaGlory</span>
                </div>
                
                <div className={styles.exportPicks}>
                  {recommendations.map((rec, index) => {
                    const labels = formatExportPickLabels(rec);
                    const isEarlyKickoff = isEarlyKickoffUk(rec.matchDateUnix);
                    return (
                      <div
                        key={`${rec.fixtureId}-${rec.type}-${index}`}
                        className={`${styles.exportPick} ${isEarlyKickoff ? styles.exportPickEarly : ''}`}
                      >
                        <div className={styles.exportPickHeader}>
                          {rec.leagueImage && (
                            <div className={styles.exportLeagueIcon}>
                              <img src={rec.leagueImage} alt="" />
                            </div>
                          )}
                          <div className={styles.exportPickInfo}>
                            <span
                              className={`${styles.exportDate} ${isEarlyKickoff ? styles.exportDateEarly : ''}`}
                              title={isEarlyKickoff ? EARLY_KICKOFF_WARNING : undefined}
                            >
                              <span>{formatDate(rec.matchDateUnix)}</span>
                              {labels.leagueLabel && (
                                <span className={styles.exportLeagueName}>{labels.leagueLabel}</span>
                              )}
                              {isEarlyKickoff && <EarlyKickoffBadge />}
                            </span>
                            <span className={styles.exportFixture}>{labels.fixtureLabel}</span>
                            {isEarlyKickoff && (
                              <span className={styles.exportEarlyNote}>{EARLY_KICKOFF_STRIP}</span>
                            )}
                          </div>
                        </div>
                        <div className={styles.exportPickSelection}>
                          <span className={styles.exportConfidence}>
                            {rec.confidence === 'STRONG' ? '🔥' : '⚡'}
                          </span>
                          <div className={styles.exportMarketBlock}>
                            <span className={styles.exportMarketType}>{labels.marketTypeLabel}</span>
                            <span className={styles.exportMarket}>{labels.selectionLabel}</span>
                          </div>
                          {rec.odds && (
                            <span className={styles.exportOdds}>@ {rec.odds.toFixed(2)}</span>
                          )}
                        </div>
                      </div>
                    );
                  })}
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
            <button
              className={styles.exportButton}
              onClick={handleTextExport}
            >
              {copied ? '✓ Copied!' : '📋 Copy to Clipboard'}
            </button>
          )}
        </footer>
      </div>
    </div>
  );
}
