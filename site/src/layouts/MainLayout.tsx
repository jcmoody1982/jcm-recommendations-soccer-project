import { Outlet, Link, useLocation } from 'react-router-dom';
import { SettingsDropdown } from '../components/SettingsDropdown';
import styles from './MainLayout.module.css';

const navItems = [
  { path: '/', label: 'Home', icon: null },
  { path: '/recommendations', label: 'Recommendations', icon: null },
  { path: '/fixtures', label: 'Fixtures', icon: null },
];

export default function MainLayout() {
  const location = useLocation();

  return (
    <div className={styles.layout}>
      <header className={styles.header}>
        <div className={styles.logo}>
          <Link to="/">AccaBaccaGlory</Link>
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
              {item.icon && <span className={styles.navIcon}>{item.icon}</span>}
              {item.label}
            </Link>
          ))}
        </nav>
        <div className={styles.headerActions}>
          <SettingsDropdown />
        </div>
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
