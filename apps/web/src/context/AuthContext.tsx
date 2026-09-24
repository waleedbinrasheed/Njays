"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { apiFetch, ApiError, getAccessToken, getRefreshToken, storeTokens, clearTokens } from "@/lib/api";
import type { MeResponse } from "@/lib/types";

interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  user: MeResponse;
}

interface AuthContextValue {
  user: MeResponse | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (fullName: string, email: string, password: string, branchId: number | null) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<MeResponse | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function init() {
      if (!getAccessToken()) {
        setLoading(false);
        return;
      }
      try {
        const me = await apiFetch<MeResponse>("/auth/me");
        setUser(me);
      } catch (err) {
        if (!(err instanceof ApiError)) console.error("Failed to load current session", err);
        clearTokens();
        setUser(null);
      } finally {
        setLoading(false);
      }
    }
    init();
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const res = await apiFetch<TokenResponse>("/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    });
    storeTokens(res.accessToken, res.refreshToken);
    setUser(res.user);
  }, []);

  const register = useCallback(
    async (fullName: string, email: string, password: string, branchId: number | null) => {
      const res = await apiFetch<TokenResponse>("/auth/register", {
        method: "POST",
        body: JSON.stringify({ fullName, email, password, branchId }),
      });
      storeTokens(res.accessToken, res.refreshToken);
      setUser(res.user);
    },
    []
  );

  const logout = useCallback(async () => {
    const refreshToken = getRefreshToken();
    try {
      if (refreshToken) {
        await apiFetch("/auth/logout", { method: "POST", body: JSON.stringify({ refreshToken }) });
      }
    } finally {
      clearTokens();
      setUser(null);
    }
  }, []);

  const value = useMemo(() => ({ user, loading, login, register, logout }), [user, loading, login, register, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within an AuthProvider");
  return ctx;
}
