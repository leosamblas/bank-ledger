import { request, ApiError, IS_MOCK_ENABLED } from './apiClient';
import {
  TransactionResponse,
  TransactionApiDepositRequest,
  TransactionApiWithdrawalRequest,
} from './types';
import { mockBankingStore, generateUUID } from './mockBankingStore';

interface MockTransactionRecord {
  idempotencyKey?: string;
  response: TransactionResponse;
  resolveAt: number;
  targetStatus: 'COMPLETED' | 'FAILED';
  failureCode?: string | null;
}

const MOCK_TRANSACTIONS_STORAGE_KEY = 'nexus_mock_transactions_v1';
const inMemoryTransactions = new Map<string, MockTransactionRecord>();

function loadMockTransactions(): Map<string, MockTransactionRecord> {
  if (typeof window === 'undefined') {
    return inMemoryTransactions;
  }
  try {
    const raw = localStorage.getItem(MOCK_TRANSACTIONS_STORAGE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw) as Record<string, MockTransactionRecord>;
      return new Map<string, MockTransactionRecord>(Object.entries(parsed));
    }
  } catch (e) {
    console.error('Falha ao ler transações mock do localStorage', e);
  }
  return inMemoryTransactions;
}

function persistMockTransaction(record: MockTransactionRecord): void {
  inMemoryTransactions.set(record.response.id, record);
  if (typeof window === 'undefined') return;
  try {
    const map = loadMockTransactions();
    map.set(record.response.id, record);
    // Limita histórico a 50 transações
    if (map.size > 50) {
      const keys = Array.from(map.keys());
      for (let i = 0; i < keys.length - 50; i++) {
        map.delete(keys[i]);
      }
    }
    const obj = Object.fromEntries(map.entries());
    localStorage.setItem(MOCK_TRANSACTIONS_STORAGE_KEY, JSON.stringify(obj));
  } catch (e) {
    console.error('Falha ao salvar transação mock no localStorage', e);
  }
}

function findMockTransactionByIdempotency(
  idempotencyKey: string
): MockTransactionRecord | undefined {
  const map = loadMockTransactions();
  for (const record of map.values()) {
    if (record.idempotencyKey === idempotencyKey) {
      return record;
    }
  }
  return undefined;
}

function findMockTransactionById(
  id: string
): MockTransactionRecord | undefined {
  const map = loadMockTransactions();
  return map.get(id);
}

/**
 * Cliente de Operações Financeiras e Transações do Nexus Bank
 */
export const transactionService = {
  /**
   * Inicia uma operação assíncrona de depósito.
   *
   * @param amountCents Valor em centavos (inteiro positivo)
   * @param idempotencyKey Chave UUID de idempotência única por operação
   * @param description Descrição ou identificador do terminal
   * @returns Promessa com a resposta da transação (inicialmente PENDING)
   */
  async createDeposit(
    amountCents: number,
    idempotencyKey: string,
    description?: string
  ): Promise<TransactionResponse> {
    if (IS_MOCK_ENABLED) {
      return this.mockCreateDeposit(amountCents, idempotencyKey, description);
    }

    try {
      const body: TransactionApiDepositRequest = {
        amount: amountCents,
        ...(description ? { description } : {}),
      };

      const response = await request<TransactionResponse>(
        '/transactions/deposits',
        {
          method: 'POST',
          headers: {
            'Idempotency-Key': idempotencyKey,
          },
          body: JSON.stringify(body),
        }
      );

      return response;
    } catch (error) {
      // Fallback gracioso se o backend ainda estiver offline
      if (error instanceof TypeError && error.message.includes('fetch')) {
        console.warn(
          '[TransactionService] Backend inacessível. Processando depósito via mock local.'
        );
        return this.mockCreateDeposit(amountCents, idempotencyKey, description);
      }
      throw error;
    }
  },

  /**
   * Inicia uma operação assíncrona de saque.
   *
   * @param amountCents Valor em centavos (inteiro positivo)
   * @param idempotencyKey Chave UUID de idempotência única por operação
   * @param description Descrição ou identificador do terminal
   * @returns Promessa com a resposta da transação (inicialmente PENDING)
   */
  async createWithdrawal(
    amountCents: number,
    idempotencyKey: string,
    description?: string
  ): Promise<TransactionResponse> {
    if (IS_MOCK_ENABLED) {
      return this.mockCreateWithdrawal(amountCents, idempotencyKey, description);
    }

    try {
      const body: TransactionApiWithdrawalRequest = {
        amount: amountCents,
        ...(description ? { description } : {}),
      };

      const response = await request<TransactionResponse>(
        '/transactions/withdrawals',
        {
          method: 'POST',
          headers: {
            'Idempotency-Key': idempotencyKey,
          },
          body: JSON.stringify(body),
        }
      );

      return response;
    } catch (error) {
      // Fallback gracioso se o backend ainda estiver offline
      if (error instanceof TypeError && error.message.includes('fetch')) {
        console.warn(
          '[TransactionService] Backend inacessível. Processando saque via mock local.'
        );
        return this.mockCreateWithdrawal(
          amountCents,
          idempotencyKey,
          description
        );
      }
      throw error;
    }
  },

  /**
   * Consulta o estado atual de uma transação por ID.
   *
   * @param transactionId ID UUID da transação
   * @returns Estado atual da transação
   */
  async getTransactionById(
    transactionId: string
  ): Promise<TransactionResponse> {
    if (IS_MOCK_ENABLED) {
      return this.mockGetTransactionById(transactionId);
    }

    try {
      return await request<TransactionResponse>(
        `/transactions/${transactionId}`,
        {
          method: 'GET',
        }
      );
    } catch (error) {
      if (error instanceof TypeError && error.message.includes('fetch')) {
        console.warn(
          '[TransactionService] Backend inacessível. Consultando transação via mock local.'
        );
        return this.mockGetTransactionById(transactionId);
      }
      throw error;
    }
  },

  /**
   * Realiza polling de consulta do status de uma transação até que ela
   * atinja o estado COMPLETED ou FAILED, ou até o limite de tentativas.
   *
   * @param transactionId ID da transação a monitorar
   * @param intervalMs Intervalo entre consultas (padrão: 350ms)
   * @param maxAttempts Máximo de tentativas de polling (padrão: 15)
   * @returns Transação no estado final resolvido ou no último estado após timeout
   */
  async pollTransactionUntilResolved(
    transactionId: string,
    intervalMs = 350,
    maxAttempts = 15
  ): Promise<TransactionResponse> {
    let attempts = 0;

    while (attempts < maxAttempts) {
      const tx = await this.getTransactionById(transactionId);

      if (tx.status === 'COMPLETED' || tx.status === 'FAILED') {
        return tx;
      }

      attempts++;
      if (attempts >= maxAttempts) {
        return tx;
      }

      await new Promise((resolve) => setTimeout(resolve, intervalMs));
    }

    return this.getTransactionById(transactionId);
  },

  /**
   * Mock realista para depósito
   */
  async mockCreateDeposit(
    amountCents: number,
    idempotencyKey: string,
    description?: string
  ): Promise<TransactionResponse> {
    await new Promise((resolve) => setTimeout(resolve, 120));

    // 1. Idempotência
    const existing = findMockTransactionByIdempotency(idempotencyKey);
    if (existing) {
      return { ...existing.response };
    }

    // 2. Validações numéricas
    if (
      !Number.isFinite(amountCents) ||
      !Number.isInteger(amountCents) ||
      amountCents <= 0
    ) {
      throw new ApiError({
        status: 400,
        title: 'Requisição Inválida',
        detail:
          'O valor do depósito deve ser um número inteiro positivo em centavos.',
      });
    }

    if (amountCents > 5000000) {
      throw new ApiError({
        status: 400,
        title: 'Limite Excedido',
        detail:
          'O valor do depósito excede o limite máximo permitido de R$ 50.000,00 por operação.',
      });
    }

    // Mutação atômica do mock store
    mockBankingStore.executeDeposit({
      amountCents,
      idempotencyKey,
      terminalId: description,
    });

    const state = mockBankingStore.getState();
    const txId = generateUUID();
    const now = new Date().toISOString();

    const txResponse: TransactionResponse = {
      id: txId,
      type: 'DEPOSIT',
      status: 'PENDING',
      sourceAccountId: null,
      destinationAccountId: state.account.accountNumber || '100234',
      amount: amountCents,
      currency: 'BRL',
      failureCode: null,
      createdAt: now,
      completedAt: null,
    };

    persistMockTransaction({
      idempotencyKey,
      response: txResponse,
      resolveAt: Date.now() + 400,
      targetStatus: 'COMPLETED',
    });

    return txResponse;
  },

  /**
   * Mock realista para saque
   */
  async mockCreateWithdrawal(
    amountCents: number,
    idempotencyKey: string,
    description?: string
  ): Promise<TransactionResponse> {
    await new Promise((resolve) => setTimeout(resolve, 120));

    // 1. Idempotência
    const existing = findMockTransactionByIdempotency(idempotencyKey);
    if (existing) {
      return { ...existing.response };
    }

    // 2. Validações numéricas
    if (
      !Number.isFinite(amountCents) ||
      !Number.isInteger(amountCents) ||
      amountCents <= 0
    ) {
      throw new ApiError({
        status: 400,
        title: 'Requisição Inválida',
        detail:
          'O valor do saque deve ser um número inteiro positivo em centavos.',
      });
    }

    if (amountCents > 5000000) {
      throw new ApiError({
        status: 400,
        title: 'Limite Excedido',
        detail:
          'O valor do saque excede o limite máximo permitido de R$ 50.000,00 por operação.',
      });
    }

    const state = mockBankingStore.getState();
    const hasInsufficientFunds = amountCents > state.balance.availableCents;

    if (!hasInsufficientFunds) {
      mockBankingStore.executeWithdraw({
        amountCents,
        idempotencyKey,
        terminalId: description,
      });
    }

    const txId = generateUUID();
    const now = new Date().toISOString();

    const txResponse: TransactionResponse = {
      id: txId,
      type: 'WITHDRAWAL',
      status: 'PENDING',
      sourceAccountId: state.account.accountNumber || '100234',
      destinationAccountId: null,
      amount: amountCents,
      currency: 'BRL',
      failureCode: null,
      createdAt: now,
      completedAt: null,
    };

    persistMockTransaction({
      idempotencyKey,
      response: txResponse,
      resolveAt: Date.now() + 400,
      targetStatus: hasInsufficientFunds ? 'FAILED' : 'COMPLETED',
      failureCode: hasInsufficientFunds ? 'INSUFFICIENT_FUNDS' : null,
    });

    return txResponse;
  },

  /**
   * Mock realista para consulta de transação
   */
  async mockGetTransactionById(
    transactionId: string
  ): Promise<TransactionResponse> {
    await new Promise((resolve) => setTimeout(resolve, 50));

    const record = findMockTransactionById(transactionId);
    if (record) {
      if (
        record.response.status === 'PENDING' &&
        Date.now() >= record.resolveAt
      ) {
        record.response = {
          ...record.response,
          status: record.targetStatus,
          failureCode: record.failureCode || null,
          completedAt: new Date().toISOString(),
        };
        persistMockTransaction(record);
      }
      return { ...record.response };
    }

    // Fallback: consulta no statement do mockBankingStore
    const state = mockBankingStore.getState();
    const item = state.statement.find(
      (s) =>
        s.id === transactionId ||
        s.transactionId === transactionId ||
        s.idempotencyKey === transactionId
    );

    if (item) {
      const isDeposit = item.category === 'DEPOSIT' || item.type === 'DEPOSIT';
      const isWithdrawal =
        item.category === 'WITHDRAWAL' || item.type === 'WITHDRAWAL';
      const isFailed = item.category === 'FAILED' || item.type === 'REJECTED';

      return {
        id: item.transactionId || item.id,
        type: isDeposit ? 'DEPOSIT' : isWithdrawal ? 'WITHDRAWAL' : 'TRANSFER',
        status: isFailed ? 'FAILED' : 'COMPLETED',
        sourceAccountId: isWithdrawal ? state.account.accountNumber : null,
        destinationAccountId: isDeposit ? state.account.accountNumber : null,
        amount: item.amountCents,
        currency: 'BRL',
        failureCode: isFailed
          ? item.failureReason
            ? 'INSUFFICIENT_FUNDS'
            : 'FAILED'
          : null,
        createdAt: item.timestamp,
        completedAt: item.timestamp,
      };
    }

    throw new ApiError({
      status: 404,
      title: 'Transação Não Encontrada',
      detail: `Nenhuma transação com o ID "${transactionId}" foi localizada no sistema.`,
    });
  },
};
