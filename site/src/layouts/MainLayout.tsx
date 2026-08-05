import { useState, useEffect, useCallback } from 'react';
import { Outlet, Link, useLocation } from 'react-router-dom';
import { useTheme } from '../contexts/ThemeContext';
import { useShortlist } from '../contexts/ShortlistContext';
import { SettingsDropdown } from '../components/SettingsDropdown';
import styles from './MainLayout.module.css';

const navItems = [
  { path: '/recommendations', label: 'Recommendations' },
  { path: '/shortlist', label: 'Shortlist', showBadge: true },
  { path: '/results', label: 'Results' },
  { path: '/fixtures', label: 'Fixtures' },
];

type Theme = 'light' | 'dark' | 'system';

const THEME_OPTIONS: { value: Theme; label: string; icon: string }[] = [
  { value: 'light', label: 'Light', icon: '☀️' },
  { value: 'dark', label: 'Dark', icon: '🌙' },
  { value: 'system', label: 'System', icon: '💻' },
];

export default function MainLayout() {
  const location = useLocation();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const { theme, setTheme } = useTheme();
  const { shortlistCount } = useShortlist();

  const toggleMobileMenu = () => {
    setMobileMenuOpen(prev => !prev);
  };

  const closeMobileMenu = useCallback(() => {
    setMobileMenuOpen(false);
  }, []);

  useEffect(() => {
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && mobileMenuOpen) {
        closeMobileMenu();
      }
    };

    if (mobileMenuOpen) {
      document.addEventListener('keydown', handleEscape);
      document.body.style.overflow = 'hidden';
    }

    return () => {
      document.removeEventListener('keydown', handleEscape);
      document.body.style.overflow = '';
    };
  }, [mobileMenuOpen, closeMobileMenu]);

  return (
    <div className={styles.layout}>
      <header className={styles.header}>
        <div className={styles.logo}>
          <Link to="/recommendations">
            <img src="/logo.png" alt="AccaBaccaGlory" className={styles.logoImage} />
            <span className={`${styles.logoText} brand-display`}>AccaBaccaGlory</span>
          </Link>
        </div>
        
        <nav className={styles.nav}>
          {navItems.map((item) => (
            <Link
              key={item.path}
              to={item.path}
              className={`${styles.navLink} ${
                location.pathname === item.path ? styles.active : ''
              }`}
            >
              {item.label}
              {item.showBadge && shortlistCount > 0 && (
                <span className={styles.badge}>{shortlistCount}</span>
              )}
            </Link>
          ))}
        </nav>

        <div className={styles.headerActions}>
          <SettingsDropdown />
        </div>

        <button 
          className={styles.hamburger} 
          onClick={toggleMobileMenu}
          aria-label="Toggle menu"
          aria-expanded={mobileMenuOpen}
        >
          <span className={`${styles.hamburgerLine} ${mobileMenuOpen ? styles.open : ''}`}></span>
          <span className={`${styles.hamburgerLine} ${mobileMenuOpen ? styles.open : ''}`}></span>
          <span className={`${styles.hamburgerLine} ${mobileMenuOpen ? styles.open : ''}`}></span>
        </button>

        {mobileMenuOpen && (
          <>
            <div 
              className={styles.backdrop} 
              onClick={closeMobileMenu}
              aria-hidden="true"
            />
            <div className={styles.mobileMenu}>
              <nav className={styles.mobileNav}>
                {navItems.map((item) => (
                  <Link
                    key={item.path}
                    to={item.path}
                    className={`${styles.mobileNavLink} ${
                      location.pathname === item.path ? styles.active : ''
                    }`}
                    onClick={closeMobileMenu}
                  >
                    {item.label}
                    {item.showBadge && shortlistCount > 0 && (
                      <span className={styles.badge}>{shortlistCount}</span>
                    )}
                  </Link>
                ))}
              </nav>
              
              <div className={styles.mobileSettings}>
                <span className={styles.mobileSettingsLabel}>Theme</span>
                <div className={styles.mobileThemeOptions}>
                  {THEME_OPTIONS.map((option) => (
                    <button
                      key={option.value}
                      className={`${styles.mobileThemeOption} ${theme === option.value ? styles.active : ''}`}
                      onClick={() => setTheme(option.value)}
                    >
                      <span>{option.icon}</span>
                      <span>{option.label}</span>
                    </button>
                  ))}
                </div>
              </div>
            </div>
          </>
        )}
      </header>

      <main className={styles.main}>
        <Outlet />
      </main>
      <footer className={styles.footer}>
        <p>&copy; 2026 AccaBaccaGlory. All rights reserved.</p>
      </footer>
    </div>
  );
}
