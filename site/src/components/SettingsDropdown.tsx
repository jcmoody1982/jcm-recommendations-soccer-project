import { useState, useRef, useEffect } from 'react';
import { useTheme } from '../contexts/ThemeContext';
import styles from './SettingsDropdown.module.css';

type Theme = 'light' | 'dark' | 'system';

const THEME_OPTIONS: { value: Theme; label: string; icon: string }[] = [
  { value: 'light', label: 'Light', icon: '☀️' },
  { value: 'dark', label: 'Dark', icon: '🌙' },
  { value: 'system', label: 'System', icon: '💻' },
];

export function SettingsDropdown() {
  const [isOpen, setIsOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const { theme, setTheme } = useTheme();

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    }

    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside);
    }

    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [isOpen]);

  const handleThemeChange = (newTheme: Theme) => {
    setTheme(newTheme);
  };

  return (
    <div className={styles.container} ref={dropdownRef}>
      <button
        className={styles.settingsButton}
        onClick={() => setIsOpen(!isOpen)}
        aria-label="Settings"
        aria-expanded={isOpen}
      >
        <span className={styles.icon}>⚙️</span>
      </button>

      {isOpen && (
        <div className={styles.dropdown}>
          <div className={styles.section}>
            <h4 className={styles.sectionTitle}>Theme</h4>
            <div className={styles.themeOptions}>
              {THEME_OPTIONS.map((option) => (
                <button
                  key={option.value}
                  className={`${styles.themeOption} ${theme === option.value ? styles.active : ''}`}
                  onClick={() => handleThemeChange(option.value)}
                >
                  <span className={styles.themeIcon}>{option.icon}</span>
                  <span className={styles.themeLabel}>{option.label}</span>
                </button>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
