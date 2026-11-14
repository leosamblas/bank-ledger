import { request } from './apiClient';
import {
  TransactionResponse,
  TransactionApiDepositRequest,
  TransactionApiWithdrawalRequest,
} from './types';

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
    const body: TransactionApiDepositRequest = {
      amount: amountCents,
      ...(description ? { description } : {}),
    };

    return request<TransactionResponse>('/transactions/deposits', {
      method: 'POST',
      headers: {
        'Idempotency-Key': idempotencyKey,
      },
      body: JSON.stringify(body),
    });
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
    const body: TransactionApiWithdrawalRequest = {
      amount: amountCents,
      ...(description ? { description } : {}),
    };

    return request<TransactionResponse>('/transactions/withdrawals', {
      method: 'POST',
      headers: {
        'Idempotency-Key': idempotencyKey,
      },
      body: JSON.stringify(body),
    });
  },

  /**
   * Inicia uma transferência entre contas correntes do Nexus Bank.
   *
   * @param destinationAccountId ID UUID da conta de destino
   * @param amountCents Valor em centavos (inteiro positivo)
   * @param idempotencyKey Chave UUID de idempotência única por operação
   * @param description Descrição opcional da transferência
   * @returns Promessa com a resposta da transação (inicialmente PENDING)
   */
  async createTransfer(
    destinationAccountId: string,
    amountCents: number,
    idempotencyKey: string,
    description?: string
  ): Promise<TransactionResponse> {
    return request<TransactionResponse>('/transactions/transfers', {
      method: 'POST',
      headers: {
        'Idempotency-Key': idempotencyKey,
      },
      body: JSON.stringify({
        destinationAccountId,
        amount: amountCents,
        ...(description ? { description } : {}),
      }),
    });
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
    return request<TransactionResponse>(`/transactions/${transactionId}`, {
      method: 'GET',
    });
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
};
