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
  refreshToken?: string;
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

export interface CustomerAddress {
  postalCode?: string;
  street?: string;
  number?: string;
  complement?: string;
  neighborhood?: string;
  city?: string;
  state?: string;
}

export interface UpdateProfileRequest {
  phone?: string;
  email?: string;
  postalCode?: string;
  street?: string;
  number?: string;
  complement?: string;
  neighborhood?: string;
  city?: string;
  state?: string;
  address?: CustomerAddress;
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
  address?: CustomerAddress;
  dailyTransferLimitCents?: number;
  email?: string;
  phone?: string;
  cpf?: string;
  birthDate?: string;
}

export interface AccountLookupRequest {
  searchType: 'ACCOUNT' | 'CPF';
  cpf?: string;
  agency?: string;
  accountNumber?: string;
}

export interface AccountLookupResponse {
  accountId: string;
  branch?: string;
  agency?: string;
  number?: string;
  accountNumber?: string;
  checkDigit?: string;
  digit?: string;
  holderName: string;
  holderNameMasked?: string;
  cpf?: string;
  bankCode?: string;
  bankName?: string;
  documentMasked?: string;
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
  counterpartyDocument?: string;
  counterpartyDocumentMasked: string;
  counterpartyAccount: string;
  nature: TransactionNature;
  amountCents: number; // Sempre positivo, natureza define crédito/débito
  balanceAfterCents: number;
  idempotencyKey: string;
  protocolNumber?: string;
  failureReason?: string;
  debitAccount?: string;
  creditAccount?: string;
  hashSignature?: string;
}

export type StatementEntry = Partial<StatementItem>;

export interface BankingAccountInfo {
  id: string;
  agency: string;
  accountNumber: string;
  digit: string;
  holderName: string;
  companyName: string;
  cpfMasked: string;
  status: 'ACTIVE' | 'BLOCKED' | 'PENDING';
  cpf?: string;
  birthDate?: string;
  email?: string;
  phone?: string;
  address?: CustomerAddress;
}

export interface BalanceInfo {
  availableCents: number;
  blockedCents: number;
  dailyLimitCents: number;
  usedTodayCents: number;
  availableLimitCents?: number;
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
