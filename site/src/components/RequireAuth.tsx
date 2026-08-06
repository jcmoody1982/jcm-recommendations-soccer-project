import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';

export default function RequireAuth({ children }: { children: React.ReactNode }) {
  const { authenticated, authEnabled, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return (
      <div style={{ minHeight: '40vh', display: 'grid', placeItems: 'center', color: 'var(--text-secondary)' }}>
        Checking access…
      </div>
    );
  }

  if (authEnabled && !authenticated) {
    return <Navigate to="/" replace state={{ from: location.pathname }} />;
  }

  return children;
}
