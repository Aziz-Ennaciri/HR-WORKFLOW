"use client";

import { useEffect, useState } from "react";
import { useRouter, usePathname } from "next/navigation";

// ─── Storage helpers ────────────────────────────────────────────
// Use sessionStorage so sessions die when the browser/tab closes

export function getToken(): string | null {
  if (typeof window === "undefined") return null;
  return sessionStorage.getItem("token");
}

export function getUser(): any | null {
  if (typeof window === "undefined") return null;
  const raw = sessionStorage.getItem("user");
  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

export function getRefreshToken(): string | null {
  if (typeof window === "undefined") return null;
  return sessionStorage.getItem("refreshToken");
}

/** Stores a token pair after login or refresh. The access token is short-lived (minutes). */
export function setAuth(token: string, user: any, refreshToken?: string) {
  sessionStorage.setItem("token", token);
  sessionStorage.setItem("user", JSON.stringify(user));
  if (refreshToken) sessionStorage.setItem("refreshToken", refreshToken);
}

/** Updates the cached profile without touching the tokens. */
export function setUser(user: any) {
  sessionStorage.setItem("user", JSON.stringify(user));
}

export function clearAuth() {
  sessionStorage.removeItem("token");
  sessionStorage.removeItem("refreshToken");
  sessionStorage.removeItem("user");
  sessionStorage.removeItem("runningExecutions");
}

// ─── JWT expiry check ───────────────────────────────────────────
// Decode JWT payload and check if it's expired

function isTokenExpired(token: string): boolean {
  try {
    const payload = JSON.parse(atob(token.split(".")[1]));
    const exp = payload.exp * 1000; // convert to ms
    return Date.now() >= exp;
  } catch {
    return true;
  }
}

/**
 * True while the user has a usable session: a valid access token, or a refresh token the API
 * client can exchange for a new one. The server has the final say on the next request.
 */
export function isAuthenticated(): boolean {
  const token = getToken();
  if (token && !isTokenExpired(token)) return true;
  if (getRefreshToken()) return true;
  clearAuth();
  return false;
}

// ─── Auth guard hook ────────────────────────────────────────────
// Use this in any protected page to redirect to login if not authenticated

const PUBLIC_PATHS = ["/", "/login", "/register"];

export function useAuthGuard() {
  const router = useRouter();
  const pathname = usePathname();
  const [checked, setChecked] = useState(false);

  useEffect(() => {
    if (PUBLIC_PATHS.includes(pathname)) {
      setChecked(true);
      return;
    }

    if (!isAuthenticated()) {
      router.replace("/login");
    } else {
      setChecked(true);
    }
  }, [pathname, router]);

  return checked;
}
