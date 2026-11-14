import { request } from './apiClient';
import { BalanceInfo, StatementItem } from './types';

export interface StatementQueryOptions {
  from?: string;
  to?: string;
  size?: number;
}

interface RawStatementItemResponse {
  id?: string;
  sequence?: number;
  transactionId?: string;
  journalId?: string;
  timestamp?: string;
  postedAt?: string;
  category?: 'TRANSFER' | 'DEPOSIT' | 'WITHDRAWAL' | 'FAILED';
  type?: StatementItem['type'];
  description?: string;
  counterpartyName?: string;
  counterpartyDocumentMasked?: string;
  counterpartyAccount?: string;
  nature?: StatementItem['nature'];
  direction?: StatementItem['nature'];
  amountCents?: number;
  amount?: number;
  balanceAfterCents?: number;
  balanceAfter?: number;
  idempotencyKey?: string;
  failureReason?: string;
  debitAccount?: string;
  creditAccount?: string;
  hashSignature?: string;
}

/**
 * Serviço de Consultas CQRS (Saldo e Extrato em tempo real)
 */
export const queryService = {
  /**
   * Consulta o saldo atualizado e limites da conta
   */
  async getBalance(accountId: string): Promise<BalanceInfo> {
    const raw = await request<BalanceInfo & { balance?: number; currency?: string }>(
      `/queries/accounts/${accountId}/balance`,
      { method: 'GET' }
    );

    return {
      availableCents: raw.availableCents ?? raw.balance ?? 0,
      blockedCents: raw.blockedCents ?? 0,
      dailyLimitCents: raw.dailyLimitCents ?? 5000000,
      usedTodayCents: raw.usedTodayCents ?? 0,
      monthlyVariationPercent: raw.monthlyVariationPercent ?? 0.0,
      asOfSequence: raw.asOfSequence ?? 0,
      updatedAt: raw.updatedAt ?? new Date().toISOString(),
    };
  },

  /**
   * Consulta o histórico ordenado de transações da conta (extrato)
   */
  async getStatement(
    accountId: string,
    options?: StatementQueryOptions
  ): Promise<StatementItem[]> {
    const params = new URLSearchParams();
    if (options?.from) params.set('from', options.from);
    if (options?.to) params.set('to', options.to);
    if (options?.size) params.set('size', String(options.size));

    const queryString = params.toString() ? `?${params.toString()}` : '';

    const response = await request<
      | { items?: RawStatementItemResponse[] }
      | RawStatementItemResponse[]
    >(`/queries/accounts/${accountId}/statement${queryString}`, {
      method: 'GET',
    });

    const rawList: RawStatementItemResponse[] = Array.isArray(response)
      ? response
      : (response && Array.isArray(response.items) ? response.items : []);

    return rawList.map((item: RawStatementItemResponse) => ({
      id: item.id || item.transactionId || '',
      sequence: item.sequence ?? 0,
      transactionId: item.transactionId || item.id || '',
      journalId: item.journalId,
      timestamp: item.timestamp || item.postedAt || new Date().toISOString(),
      category: item.category || (item.nature === 'DEBIT' ? 'TRANSFER' : 'DEPOSIT'),
      type: item.type || (item.nature === 'DEBIT' ? 'TRANSFER_OUT' : 'TRANSFER_IN'),
      description: item.description || '',
      counterpartyName: item.counterpartyName || '',
      counterpartyDocumentMasked: item.counterpartyDocumentMasked || '',
      counterpartyAccount: item.counterpartyAccount || '',
      nature: item.nature || item.direction || 'CREDIT',
      amountCents: item.amountCents ?? item.amount ?? 0,
      balanceAfterCents: item.balanceAfterCents ?? item.balanceAfter ?? 0,
      idempotencyKey: item.idempotencyKey || '',
      failureReason: item.failureReason,
      debitAccount: item.debitAccount,
      creditAccount: item.creditAccount,
      hashSignature: item.hashSignature,
    }));
  },
};
