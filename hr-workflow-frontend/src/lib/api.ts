import axios, { AxiosError, InternalAxiosRequestConfig } from "axios";
import { clearAuth, getRefreshToken, getToken, setAuth } from "@/lib/auth";

const baseURL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";

const api = axios.create({
  baseURL,
  headers: {
    "Content-Type": "application/json",
  },
});

api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = getToken();
  if (token && config.headers) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Concurrent 401s share one refresh call; the refresh token is single-use on the server.
let refreshing: Promise<string> | null = null;

async function refreshAccessToken(): Promise<string> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) throw new Error("No refresh token");
  // Plain axios: the refresh call must not go through this interceptor.
  const { data } = await axios.post(`${baseURL}/users/refresh`, { refreshToken });
  setAuth(data.token, data.user, data.refreshToken);
  return data.token;
}

const isAuthEndpoint = (url?: string) =>
  !!url && ["/users/login", "/users/refresh", "/users/logout"].some((path) => url.includes(path));

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined;

    // 401 = missing/expired access token: renew it once and replay the request.
    // 403 means "not allowed to do this" and must not log the user out.
    if (error.response?.status === 401 && original && !original._retried && !isAuthEndpoint(original.url)) {
      original._retried = true;
      try {
        refreshing ??= refreshAccessToken().finally(() => {
          refreshing = null;
        });
        const token = await refreshing;
        original.headers.Authorization = `Bearer ${token}`;
        return api(original);
      } catch {
        // Refresh token expired or revoked: fall through to sign-out.
      }
    }

    if (error.response?.status === 401 && !isAuthEndpoint(original?.url)) {
      clearAuth();
      if (typeof window !== "undefined" && !window.location.pathname.includes("/login")) {
        window.location.href = "/login";
      }
    }
    return Promise.reject(error);
  },
);

/** Revokes the refresh token on the server, then forgets the session locally. */
export async function logout() {
  const refreshToken = getRefreshToken();
  try {
    if (refreshToken) await api.post("/users/logout", { refreshToken });
  } catch {
    // The session is cleared locally either way.
  } finally {
    clearAuth();
  }
}

export default api;
