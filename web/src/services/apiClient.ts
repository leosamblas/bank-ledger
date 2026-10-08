import { ProblemDetail } from './types';

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8081/api/v1';

// Ativa modo mock se configurado explicitamente ou se a API real estiver inacessível
export const IS_MOCK_ENABLED =
  process.env.NEXT_PUBLIC_USE_MOCK !== 'false';

const TOKEN_KEY = 'nexus_auth_token';

export const tokenStorage = {
  get: (): string | null => {
    if (typeof window === 'undefined') return null;
    try {
      return localStorage.getItem(TOKEN_KEY);
    } catch {
      return null;
    }
  },
  set: (token: string): void => {
    if (typeof window === 'undefined') return;
    try {
      localStorage.setItem(TOKEN_KEY, token);
    } catch {
      // Ignora falhas de storage em ambientes restritos
    }
  },
  clear: (): void => {
    if (typeof window === 'undefined') return;
    try {
      localStorage.removeItem(TOKEN_KEY);
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
 * Utilitário de requisição HTTP resiliente com suporte a fallback
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
