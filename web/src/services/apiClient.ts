import { ProblemDetail } from './types';

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api/v1';

// Ativa modo mock se configurado explicitamente ou se a API real estiver inacessível
export const IS_MOCK_ENABLED =
  process.env.NEXT_PUBLIC_USE_MOCK !== 'false';

const TOKEN_KEY = 'nexus_auth_token';
const REFRESH_TOKEN_KEY = 'nexus_auth_refresh_token';

export const tokenStorage = {
  get: (): string | null => {
    if (typeof window === 'undefined') return null;
    try {
      return sessionStorage.getItem(TOKEN_KEY) || localStorage.getItem(TOKEN_KEY);
    } catch {
      return null;
    }
  },
  getRefreshToken: (): string | null => {
    if (typeof window === 'undefined') return null;
    try {
      return sessionStorage.getItem(REFRESH_TOKEN_KEY) || localStorage.getItem(REFRESH_TOKEN_KEY);
    } catch {
      return null;
    }
  },
  set: (token: string, refreshToken?: string, persistent = true): void => {
    if (typeof window === 'undefined') return;
    try {
      sessionStorage.setItem(TOKEN_KEY, token);
      if (refreshToken) {
        sessionStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
      }
      if (persistent) {
        localStorage.setItem(TOKEN_KEY, token);
        if (refreshToken) {
          localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
        }
      }
    } catch {
      // Ignora falhas de storage em ambientes restritos
    }
  },
  clear: (): void => {
    if (typeof window === 'undefined') return;
    try {
      sessionStorage.removeItem(TOKEN_KEY);
      sessionStorage.removeItem(REFRESH_TOKEN_KEY);
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(REFRESH_TOKEN_KEY);
    } catch {
      // Ignora
    }
  },
};

export class ApiError extends Error {
  public problem: ProblemDetail;

  constructor(problem: ProblemDetail) {
    super(problem.detail || problem.title || 'Erro na requisição');
    this.name = 'ApiError';
    this.problem = problem;
  }
}

/**
 * Utilitário de requisição HTTP resiliente com suporte a fallback e auto-refresh de sessão
 */
export async function request<T>(
  endpoint: string,
  options: RequestInit = {}
): Promise<T> {
  const token = tokenStorage.get();

  const headers: HeadersInit = {
    'Content-Type': 'application/json',
    Accept: 'application/json, application/problem+json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(options.headers || {}),
  };

  const url = `${API_BASE_URL}${endpoint}`;

  const response = await fetch(url, {
    ...options,
    headers,
  });

  // Se a requisição falhar com 401 e houver refresh token (e não for rota de login/refresh), tenta renovar a sessão
  if (response.status === 401 && !endpoint.includes('/auth/login') && !endpoint.includes('/auth/refresh')) {
    const refreshToken = tokenStorage.getRefreshToken();
    if (refreshToken) {
      try {
        const refreshResponse = await fetch(`${API_BASE_URL}/auth/refresh`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Accept: 'application/json, application/problem+json',
          },
          body: JSON.stringify({ refreshToken }),
        });

        if (refreshResponse.ok) {
          const refreshData = await refreshResponse.json();
          if (refreshData.token) {
            tokenStorage.set(refreshData.token, refreshData.refreshToken);

            // Re-executa requisição original com o novo token
            const retryHeaders: HeadersInit = {
              ...headers,
              Authorization: `Bearer ${refreshData.token}`,
            };

            const retryResponse = await fetch(url, {
              ...options,
              headers: retryHeaders,
            });

            if (retryResponse.ok) {
              if (retryResponse.status === 204) {
                return {} as T;
              }
              return retryResponse.json() as Promise<T>;
            }
          }
        } else {
          tokenStorage.clear();
        }
      } catch {
        tokenStorage.clear();
      }
    }
  }

  if (!response.ok) {
    let problem: ProblemDetail;
    try {
      problem = await response.json();
    } catch {
      problem = {
        status: response.status,
        title: response.statusText || 'Erro inesperado',
        detail: `A requisição falhou com status HTTP ${response.status}`,
      };
    }
    throw new ApiError(problem);
  }

  // Tratativa para status 204 No Content
  if (response.status === 204) {
    return {} as T;
  }

  return response.json() as Promise<T>;
}
