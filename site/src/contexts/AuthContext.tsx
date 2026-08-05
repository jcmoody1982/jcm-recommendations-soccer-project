import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { authService, type AuthMe } from '../services/api';

interface AuthContextType {
  authenticated: boolean;
  authEnabled: boolean;
  role: string | null;
  loading: boolean;
  login: (password: string) => Promise<void>;
  logout: () => Promise<void>;
  refresh: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<AuthMe | null>(null);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    try {
      const next = await authService.me();
      setMe(next);
    } catch {
      setMe({ authenticated: false, authEnabled: true });
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const login = useCallback(async (password: string) => {
    const result = await authService.login(password.trim());
    if (!result.authenticated) {
      throw new Error(result.error || 'Invalid password');
    }
    setMe({
      authenticated: true,
      role: result.role,
      authEnabled: result.authEnabled ?? true,
    });
  }, []);

  const logout = useCallback(async () => {
    await authService.logout();
    setMe({ authenticated: false, authEnabled: me?.authEnabled ?? true });
  }, [me?.authEnabled]);

  const value = useMemo<AuthContextType>(() => ({
    authenticated: Boolean(me?.authenticated),
    authEnabled: me?.authEnabled !== false,
    role: me?.role ?? null,
    loading,
    login,
    logout,
    refresh,
  }), [me, loading, login, logout, refresh]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
