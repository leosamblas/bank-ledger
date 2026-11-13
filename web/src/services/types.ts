/**
 * Tipagens de Contratos da API Nexus Bank (Baseado em api-contracts/account-service.yaml,
 * transaction-service.yaml, query-service.yaml e ledger-service.yaml)
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
  holderName?: string;
}

export interface AccountLookupResponse {
  accountId: string;
  agency: string;
  number: string;
  holderNameMasked: string;
  documentMasked?: string;
  bankName?: string;
  accountType?: string;
}

export interface UpdateStatusRequest {
  status: 'ACTIVE' | 'BLOCKED' | 'CLOSED';
  reason?: string;
}

export type ApiResult<T> =
  | { success: true; data: T; isMock: boolean }
  | { success: false; error: ProblemDetail; isMock: boolean };

// ==========================================
// Tipos do Core Bancário e Ledger
// ==========================================

export type TransactionNature = 'CREDIT' | 'DEBIT' | 'NONE';

export type TransactionCategory =
  | 'ALL'
  | 'CREDIT'
  | 'DEBIT'
  | 'TRANSFER'
  | 'DEPOSIT'
  | 'WITHDRAWAL'
  | 'FAILED';

export type TransactionType =
  | 'TRANSFER_IN'
  | 'TRANSFER_OUT'
  | 'DEPOSIT'
  | 'WITHDRAWAL'
  | 'REJECTED';

export type TransactionStatus = 'PENDING' | 'COMPLETED' | 'FAILED';

export interface StatementItem {
  id: string;
  sequence: number;
  transactionId: string;
  journalId?: string;
  timestamp: string; // ISO 8601 UTC
  category: 'TRANSFER' | 'DEPOSIT' | 'WITHDRAWAL' | 'FAILED';
  type: TransactionType;
  description: string;
  counterpartyName: string;
  counterpartyDocumentMasked: string;
  counterpartyAccount: string;
  nature: TransactionNature;
  amountCents: number; // Sempre positivo, natureza define crédito/débito
  balanceAfterCents: number;
  idempotencyKey: string;
  failureReason?: string;
  debitAccount?: string;
  creditAccount?: string;
  hashSignature?: string;
}

export interface BankingAccountInfo {
  agency: string;
  accountNumber: string;
  digit: string;
  holderName: string;
  companyName: string;
  cpfMasked: string;
  status: 'ACTIVE' | 'BLOCKED' | 'PENDING';
}

export interface BalanceInfo {
  availableCents: number;
  blockedCents: number;
  dailyLimitCents: number;
  usedTodayCents: number;
  monthlyVariationPercent: number;
  asOfSequence: number;
  updatedAt: string;
}

export interface TransferRequest {
  destinationAgency: string;
  destinationAccount: string;
  amountCents: number;
  description: string;
  idempotencyKey: string;
}

export interface DepositRequest {
  amountCents: number;
  idempotencyKey: string;
  terminalId?: string;
  description?: string;
}

export interface WithdrawRequest {
  amountCents: number;
  idempotencyKey: string;
  terminalId?: string;
  description?: string;
}

export interface TransactionApiDepositRequest {
  amount: number;
  description?: string;
}

export interface TransactionApiWithdrawalRequest {
  amount: number;
  description?: string;
}

export type WithdrawalRequest = TransactionApiWithdrawalRequest;

export interface TransactionResponse {
  id: string;
  type: 'DEPOSIT' | 'WITHDRAWAL' | 'TRANSFER';
  status: 'PENDING' | 'COMPLETED' | 'FAILED';
  sourceAccountId?: string | null;
  destinationAccountId?: string | null;
  amount: number; // centavos
  currency: string;
  failureCode?: string | null;
  createdAt: string;
  completedAt?: string | null;
}
