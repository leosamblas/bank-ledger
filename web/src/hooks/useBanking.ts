"use client";

import { useState, useEffect, useCallback } from 'react';
import {
  BankingAccountInfo,
  BalanceInfo,
  StatementItem,
  TransactionResponse,
  AccountLookupResponse,
  AccountLookupRequest,
  UpdateProfileRequest,
  AccountResponse,
} from '@/services/types';
import { accountService } from '@/services/accountService';
import { queryService } from '@/services/queryService';
import { transactionService } from '@/services/transactionService';
import { generateUUID } from '@/utils/uuid';
import { maskCpf } from '@/utils/cpf';

export interface TransferParams {
  destinationAccountId?: string;
  destinationAgency?: string;
  destinationAccount?: string;
  amountCents: number;
  description?: string;
  idempotencyKey?: string;
  destinationHolderName?: string;
  destinationDocument?: string;
  destinationAccountFormatted?: string;
}

interface OptimisticEntry extends StatementItem {
  createdAtMs: number;
}

const INITIAL_ACCOUNT: BankingAccountInfo = {
  id: '',
  agency: '0001',
  accountNumber: '------',
  digit: '-',
  holderName: 'Carregando...',
  companyName: 'Nexus Bank S.A.',
  cpfMasked: '***.***.***-**',
  status: 'ACTIVE',
};

const INITIAL_BALANCE: BalanceInfo = {
  availableCents: 0,
  blockedCents: 0,
  dailyLimitCents: 5000000,
  usedTodayCents: 0,
  monthlyVariationPercent: 0.0,
  asOfSequence: 0,
  updatedAt: new Date().toISOString(),
};

// Module-level reactive store shared across all components and pages
let globalAccount: BankingAccountInfo = INITIAL_ACCOUNT;
let globalBalance: BalanceInfo = INITIAL_BALANCE;
let globalStatement: StatementItem[] = [];
let globalLoading: boolean = true;
let globalError: string | null = null;
let optimisticEntries: OptimisticEntry[] = [];
let hasInitialLoaded = false;
const listeners = new Set<() => void>();

function notifyListeners() {
  listeners.forEach((listener) => {
    try {
      listener();
    } catch (e) {
      console.error('[useBanking] listener error:', e);
    }
  });
}

export function useBanking() {
  const [account, setAccount] = useState<BankingAccountInfo>(globalAccount);
  const [balance, setBalance] = useState<BalanceInfo>(globalBalance);
  const [statement, setStatement] = useState<StatementItem[]>(globalStatement);
  const [isLoading, setIsLoading] = useState<boolean>(globalLoading);
  const [error, setError] = useState<string | null>(globalError);

  useEffect(() => {
    const handleChange = () => {
      setAccount(globalAccount);
      setBalance(globalBalance);
      setStatement(globalStatement);
      setIsLoading(globalLoading);
      setError(globalError);
    };

    listeners.add(handleChange);
    handleChange();

    return () => {
      listeners.delete(handleChange);
    };
  }, []);

  const fetchBankingData = useCallback(async (): Promise<void> => {
    try {
      const accountData = await accountService.getCurrentUserAccount();
      if (!accountData || !accountData.id) {
        globalLoading = false;
        notifyListeners();
        return;
      }

      const mappedAccount: BankingAccountInfo = {
        id: accountData.id,
        agency: accountData.agency || '0001',
        accountNumber: accountData.number,
        digit: accountData.digit,
        holderName: accountData.holderName || 'Cliente Nexus',
        companyName: 'Nexus Bank S.A.',
        cpf: accountData.cpf || '',
        cpfMasked: accountData.cpf ? maskCpf(accountData.cpf) : '***.***.***-**',
        birthDate: accountData.birthDate || '',
        email: accountData.email || '',
        phone: accountData.phone || '',
        address: accountData.address,
        status:
          accountData.status === 'BLOCKED'
            ? 'BLOCKED'
            : accountData.status === 'PENDING_ACTIVATION'
            ? 'PENDING'
            : 'ACTIVE',
      };

      const [balanceData, statementData] = await Promise.all([
        queryService.getBalance(accountData.id).catch((err) => {
          console.warn('[useBanking] Erro ao consultar saldo:', err);
          return globalBalance;
        }),
        queryService.getStatement(accountData.id).catch((err) => {
          console.warn('[useBanking] Erro ao consultar extrato:', err);
          return [] as StatementItem[];
        }),
      ]);

      const nowMs = Date.now();
      optimisticEntries = optimisticEntries.filter((e) => nowMs - e.createdAtMs < 120_000);

      // Reconcile optimistic entries against server statementData
      optimisticEntries = optimisticEntries.filter(
        (opt) =>
          !statementData.some(
            (srv) =>
              srv.id === opt.id ||
              srv.transactionId === opt.transactionId ||
              (opt.protocolNumber && srv.protocolNumber === opt.protocolNumber)
          )
      );

      const pendingDebits = optimisticEntries
        .filter((e) => e.nature === 'DEBIT' && e.category === 'TRANSFER')
        .reduce((sum, e) => sum + e.amountCents, 0);

      const reconciledUsedToday = Math.max(balanceData.usedTodayCents, pendingDebits);
      const reconciledBalance: BalanceInfo = {
        ...balanceData,
        usedTodayCents: reconciledUsedToday,
        availableLimitCents: Math.max(0, balanceData.dailyLimitCents - reconciledUsedToday),
      };

      const mergedStatement = [...optimisticEntries, ...statementData];

      globalAccount = mappedAccount;
      globalBalance = reconciledBalance;
      globalStatement = mergedStatement;
      globalError = null;
      globalLoading = false;
      hasInitialLoaded = true;
      notifyListeners();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Falha ao sincronizar dados bancários.';
      globalError = msg;
      globalLoading = false;
      notifyListeners();
      console.error('[useBanking] Falha no refresh:', err);
    }
  }, []);

  const refresh = useCallback(async (): Promise<void> => {
    globalLoading = true;
    notifyListeners();
    await fetchBankingData();
  }, [fetchBankingData]);

  useEffect(() => {
    if (!hasInitialLoaded) {
      void fetchBankingData();
    }
  }, [fetchBankingData]);

  const deposit = useCallback(
    async (params: {
      amountCents: number;
      idempotencyKey?: string;
      terminalId?: string;
    }): Promise<TransactionResponse> => {
      const key = params.idempotencyKey || generateUUID();
      const initialTx = await transactionService.createDeposit(
        params.amountCents,
        key,
        params.terminalId
      );
      const resolvedTx = await transactionService.pollTransactionUntilResolved(initialTx.id);

      if (resolvedTx.status === 'COMPLETED') {
        const newBalanceCents = globalBalance.availableCents + params.amountCents;
        globalBalance = {
          ...globalBalance,
          balance: newBalanceCents,
          availableCents: newBalanceCents,
          updatedAt: new Date().toISOString(),
        };
        notifyListeners();
        setTimeout(() => {
          void fetchBankingData();
        }, 300);
      } else {
        await refresh();
      }

      return resolvedTx;
    },
    [fetchBankingData, refresh]
  );

  const withdraw = useCallback(
    async (params: {
      amountCents: number;
      idempotencyKey?: string;
      terminalId?: string;
    }): Promise<TransactionResponse> => {
      const key = params.idempotencyKey || generateUUID();
      const initialTx = await transactionService.createWithdrawal(
        params.amountCents,
        key,
        params.terminalId
      );
      const resolvedTx = await transactionService.pollTransactionUntilResolved(initialTx.id);

      if (resolvedTx.status === 'COMPLETED') {
        const newBalanceCents = Math.max(0, globalBalance.availableCents - params.amountCents);
        globalBalance = {
          ...globalBalance,
          balance: newBalanceCents,
          availableCents: newBalanceCents,
          updatedAt: new Date().toISOString(),
        };
        notifyListeners();
        setTimeout(() => {
          void fetchBankingData();
        }, 300);
      } else {
        await refresh();
      }

      return resolvedTx;
    },
    [fetchBankingData, refresh]
  );

  const transfer = useCallback(
    async (params: TransferParams): Promise<TransactionResponse> => {
      let destinationAccountId = params.destinationAccountId;

      if (!destinationAccountId && params.destinationAgency && params.destinationAccount) {
        const lookupResult = await accountService.lookupAccount(
          params.destinationAgency,
          params.destinationAccount
        );
        destinationAccountId = lookupResult.accountId;
      }

      if (!destinationAccountId) {
        throw new Error('Conta de destino não informada ou não localizada.');
      }

      const key = params.idempotencyKey || generateUUID();
      const initialTx = await transactionService.createTransfer(
        destinationAccountId,
        params.amountCents,
        key,
        params.description,
        {
          destinationHolderName: params.destinationHolderName,
          destinationDocument: params.destinationDocument,
          destinationAccount: params.destinationAccountFormatted || params.destinationAccount,
        }
      );
      const resolvedTx = await transactionService.pollTransactionUntilResolved(initialTx.id);

      if (resolvedTx.status === 'COMPLETED') {
        const protocol = resolvedTx.id || generateUUID();

        const newUsedToday = globalBalance.usedTodayCents + params.amountCents;
        const newAvailableLimit = Math.max(0, globalBalance.dailyLimitCents - newUsedToday);
        const newBalanceCents = Math.max(0, globalBalance.availableCents - params.amountCents);

        globalBalance = {
          ...globalBalance,
          balance: newBalanceCents,
          availableCents: newBalanceCents,
          usedTodayCents: newUsedToday,
          availableLimitCents: newAvailableLimit,
          updatedAt: new Date().toISOString(),
        };

        const optimisticItem: OptimisticEntry = {
          id: resolvedTx.id,
          sequence: (globalBalance.asOfSequence || 0) + 1,
          transactionId: resolvedTx.id,
          timestamp: resolvedTx.completedAt || resolvedTx.createdAt || new Date().toISOString(),
          category: 'TRANSFER',
          type: 'TRANSFER_OUT',
          description: params.description || 'Transferência enviada',
          counterpartyName: params.destinationHolderName || 'Destinatário',
          counterpartyDocument: params.destinationDocument || '',
          counterpartyDocumentMasked: params.destinationDocument || '',
          counterpartyAccount: params.destinationAccountFormatted || params.destinationAccount || '',
          nature: 'DEBIT',
          amountCents: resolvedTx.amount || params.amountCents,
          balanceAfterCents: newBalanceCents,
          idempotencyKey: key,
          protocolNumber: protocol,
          createdAtMs: Date.now(),
        };

        optimisticEntries = [optimisticItem, ...optimisticEntries.filter((e) => e.id !== optimisticItem.id)];
        globalStatement = [optimisticItem, ...globalStatement.filter((e) => e.id !== optimisticItem.id && e.transactionId !== optimisticItem.transactionId)];
        notifyListeners();

        // Give Kafka consumer a moment before refreshing from query-service
        setTimeout(() => {
          void fetchBankingData();
        }, 300);
      } else {
        await refresh();
      }

      return resolvedTx;
    },
    [fetchBankingData, refresh]
  );

  const lookup = useCallback(
    async (
      arg1:
        | string
        | {
            cpf?: string;
            agency?: string;
            number?: string;
            accountNumber?: string;
            searchType?: 'ACCOUNT' | 'CPF';
          },
      arg2?: string
    ): Promise<AccountLookupResponse> => {
      if (typeof arg1 === 'string') {
        return accountService.lookupAccount(arg1, arg2);
      }
      return accountService.lookupAccount({
        searchType: arg1.searchType || (arg1.cpf ? 'CPF' : 'ACCOUNT'),
        cpf: arg1.cpf,
        agency: arg1.agency,
        accountNumber: arg1.accountNumber || arg1.number,
      });
    },
    []
  );

  const updateProfile = useCallback(
    async (data: UpdateProfileRequest): Promise<AccountResponse> => {
      const updated = await accountService.updateProfile(data);
      await refresh();
      return updated;
    },
    [refresh]
  );

  const resetData = useCallback(async (): Promise<void> => {
    await refresh();
  }, [refresh]);

  return {
    account,
    balance,
    statement,
    isLoading,
    error,
    refresh,
    deposit,
    withdraw,
    transfer,
    lookup,
    updateProfile,
    resetData,
  };
}
