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

export function useBanking() {
  const [account, setAccount] = useState<BankingAccountInfo>(INITIAL_ACCOUNT);
  const [balance, setBalance] = useState<BalanceInfo>(INITIAL_BALANCE);
  const [statement, setStatement] = useState<StatementItem[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const fetchBankingData = useCallback(async (): Promise<void> => {
    try {
      const accountData = await accountService.getCurrentUserAccount();
      if (!accountData || !accountData.id) {
        setIsLoading(false);
        return;
      }

      const mappedAccount: BankingAccountInfo = {
        id: accountData.id,
        agency: accountData.agency || '0001',
        accountNumber: accountData.number,
        digit: accountData.digit,
        holderName: accountData.holderName || 'Cliente Nexus',
        companyName: 'Nexus Bank S.A.',
        cpf: accountData.cpf || '529.982.247-25',
        cpfMasked: accountData.cpf || '***.***.***-**',
        birthDate: accountData.birthDate || '1988-05-14',
        email: accountData.email || 'carlos.silva@nexusbank.com.br',
        phone: accountData.phone || '(11) 98765-4321',
        address: accountData.address,
        status:
          accountData.status === 'BLOCKED'
            ? 'BLOCKED'
            : accountData.status === 'PENDING_ACTIVATION'
            ? 'PENDING'
            : 'ACTIVE',
      };
      setAccount(mappedAccount);

      const [balanceData, statementData] = await Promise.all([
        queryService.getBalance(accountData.id).catch((err) => {
          console.warn('[useBanking] Erro ao consultar saldo:', err);
          return INITIAL_BALANCE;
        }),
        queryService.getStatement(accountData.id).catch((err) => {
          console.warn('[useBanking] Erro ao consultar extrato:', err);
          return [] as StatementItem[];
        }),
      ]);

      setBalance(balanceData);
      setStatement(statementData);
      setError(null);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Falha ao sincronizar dados bancários.';
      setError(msg);
      console.error('[useBanking] Falha no refresh:', err);
    } finally {
      setIsLoading(false);
    }
  }, []);

  const refresh = useCallback(async (): Promise<void> => {
    setIsLoading(true);
    await fetchBankingData();
  }, [fetchBankingData]);

  useEffect(() => {
    let active = true;
    void (async () => {
      try {
        const accountData = await accountService.getCurrentUserAccount();
        if (!active || !accountData?.id) {
          if (active) setIsLoading(false);
          return;
        }

        const mappedAccount: BankingAccountInfo = {
          id: accountData.id,
          agency: accountData.agency || '0001',
          accountNumber: accountData.number,
          digit: accountData.digit,
          holderName: accountData.holderName || 'Cliente Nexus',
          companyName: 'Nexus Bank S.A.',
          cpf: accountData.cpf || '529.982.247-25',
          cpfMasked: accountData.cpf || '***.***.***-**',
          birthDate: accountData.birthDate || '1988-05-14',
          email: accountData.email || 'carlos.silva@nexusbank.com.br',
          phone: accountData.phone || '(11) 98765-4321',
          address: accountData.address,
          status:
            accountData.status === 'BLOCKED'
              ? 'BLOCKED'
              : accountData.status === 'PENDING_ACTIVATION'
              ? 'PENDING'
              : 'ACTIVE',
        };
        if (active) setAccount(mappedAccount);

        const [balanceData, statementData] = await Promise.all([
          queryService.getBalance(accountData.id).catch((err) => {
            console.warn('[useBanking] Erro ao consultar saldo:', err);
            return INITIAL_BALANCE;
          }),
          queryService.getStatement(accountData.id).catch((err) => {
            console.warn('[useBanking] Erro ao consultar extrato:', err);
            return [] as StatementItem[];
          }),
        ]);

        if (active) {
          setBalance(balanceData);
          setStatement(statementData);
          setError(null);
        }
      } catch (err: unknown) {
        if (active) {
          const msg = err instanceof Error ? err.message : 'Falha ao sincronizar dados bancários.';
          setError(msg);
          console.error('[useBanking] Falha no carregamento inicial:', err);
        }
      } finally {
        if (active) setIsLoading(false);
      }
    })();

    return () => {
      active = false;
    };
  }, []);

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
      await refresh();
      return resolvedTx;
    },
    [refresh]
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
      await refresh();
      return resolvedTx;
    },
    [refresh]
  );

  const transfer = useCallback(
    async (params: {
      destinationAccountId?: string;
      destinationAgency?: string;
      destinationAccount?: string;
      amountCents: number;
      description?: string;
      idempotencyKey?: string;
    }): Promise<TransactionResponse> => {
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
        params.description
      );
      const resolvedTx = await transactionService.pollTransactionUntilResolved(initialTx.id);
      await refresh();
      return resolvedTx;
    },
    [refresh]
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
