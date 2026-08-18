import { type FormEvent, useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import styles from './Login.module.css';

export default function Login() {
  const { authenticated, authEnabled, loading, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const from = (location.state as { from?: string } | null)?.from || '/recommendations';

  if (!loading && (!authEnabled || authenticated)) {
    return <Navigate to="/recommendations" replace />;
  }

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(password);
      navigate(from, { replace: true });
    } catch (err) {
      const message = err instanceof Error ? err.message : '';
      if (message === 'Invalid password' || message.toLowerCase().includes('invalid')) {
        setError('That password does not unlock the beta. Try again.');
      } else {
        setError('Cannot reach the API. Is the backend running on port 8080?');
      }
      setPassword('');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className={styles.page}>
      <div className={styles.glow} aria-hidden="true" />
      <div className={styles.compose}>
        <img src="/logo.png" alt="" className={styles.logo} />
        <h1 className={`${styles.title} brand-display`}>
          AccaBacca<span className={styles.glory}>Glory</span>
        </h1>
        <span className={styles.titleRule} aria-hidden="true" />
        <p className={styles.tagline}>Private beta — enter the access password to continue.</p>

        <form className={styles.form} onSubmit={handleSubmit}>
          <label className={styles.label} htmlFor="beta-password">
            Access password
          </label>
          <input
            id="beta-password"
            className={styles.input}
            type="password"
            name="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="••••••••"
            required
            disabled={submitting || loading}
            autoFocus
          />
          {error && <p className={styles.error} role="alert">{error}</p>}
          <button className={styles.submit} type="submit" disabled={submitting || loading || !password}>
            {submitting ? 'Unlocking…' : 'Enter beta'}
          </button>
        </form>
      </div>
    </div>
  );
}
