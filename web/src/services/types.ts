/**
 * Tipagens de Contratos da API Nexus Bank (Baseado em api-contracts/account-service.yaml)
 */

export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
}

export interface LoginRequest {
  cpf: string;
  password: string;
}

export interface TokenResponse {
  token: string;
  expiresIn: number;
  tokenType?: string;
  user?: {
    id: string;
    fullName: string;
    cpf: string;
    email: string;
  };
}

export interface AccountCreationRequest {
  fullName: string;
  cpf: string;
  birthDate: string; // formato YYYY-MM-DD
  email: string;
  phone: string;
  password: string;
}

export interface AccountResponse {
  id: string;
  customerId: string;
  agency: string;
  number: string;
  digit: string;
  status: 'ACTIVE' | 'PENDING_ACTIVATION' | 'BLOCKED' | 'CLOSED';
  openedAt: string;
}

export interface AccountLookupResponse {
  accountId: string;
  agency: string;
  number: string;
  holderNameMasked: string;
}

export interface UpdateStatusRequest {
  status: 'ACTIVE' | 'BLOCKED' | 'CLOSED';
  reason?: string;
}

export type ApiResult<T> =
  | { success: true; data: T; isMock: boolean }
  | { success: false; error: ProblemDetail; isMock: boolean };
