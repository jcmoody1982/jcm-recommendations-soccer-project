import { Outlet, Link, useLocation } from 'react-router-dom';
import styles from './MainLayout.module.css';

const navItems = [
  { path: '/', label: 'Dashboard' },
  { path: '/recommendations', label: 'Recommendations' },
  { path: '/fixtures', label: 'Fixtures' },
];

export default function MainLayout() {
  const location = useLocation();

  return (
    <div className={styles.layout}>
      <header className={styles.header}>
        <div className={styles.logo}>
          <Link to="/">Soccer Recommendations</Link>
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
            </Link>
          ))}
        </nav>
      </header>
      <main className={styles.main}>
        <Outlet />
      </main>
      <footer className={styles.footer}>
        <p>&copy; 2026 Soccer Recommendations. All rights reserved.</p>
      </footer>
    </div>
  );
}
