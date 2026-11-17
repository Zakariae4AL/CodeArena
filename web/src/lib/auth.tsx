import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { api, setUnauthorizedHandler, tokenStore } from './api';
import type { TokenResponse, User } from './types';

interface AuthState {
  user: User | null;
  ready: boolean;
  login: (username: string, password: string) => Promise<User>;
  register: (username: string, email: string, password: string) => Promise<User>;
  logout: () => void;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [user, setUser] = useState<User | null>(null);
  const [ready, setReady] = useState(() => tokenStore.get() === null);

  const logout = useCallback(() => {
    tokenStore.set(null);
    setUser(null);
    queryClient.removeQueries({ queryKey: ['me'] });
    queryClient.removeQueries({ queryKey: ['submissions'] });
  }, [queryClient]);

  useEffect(() => setUnauthorizedHandler(logout), [logout]);

  // Restore the session from a stored token.
  useEffect(() => {
    if (!tokenStore.get()) return;
    api<User>('/auth/me')
      .then(setUser)
      .catch(() => tokenStore.set(null))
      .finally(() => setReady(true));
  }, []);

  const start = useCallback((res: TokenResponse) => {
    tokenStore.set(res.token);
    setUser(res.user);
    return res.user;
  }, []);

  const value = useMemo<AuthState>(
    () => ({
      user,
      ready,
      logout,
      login: (username, password) =>
        api<TokenResponse>('/auth/login', { method: 'POST', json: { username, password } }).then(start),
      register: (username, email, password) =>
        api<TokenResponse>('/auth/register', { method: 'POST', json: { username, email, password } }).then(start),
    }),
    [user, ready, logout, start],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
