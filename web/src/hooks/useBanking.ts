"use client";

import { useSyncExternalStore, useCallback } from 'react';
import {
  mockBankingStore,
  generateUUID,
} from '@/services/mockBankingStore';
import {
  StatementItem,
  AccountLookupResponse,
} from '@/services/types';

export function useBanking() {
  const state = useSyncExternalStore(
    mockBankingStore.subscribe,
    mockBankingStore.getState,
    mockBankingStore.getServerSnapshot
  );

  const transfer = useCallback(
    (params: {
      destinationAgency: string;
      destinationAccount: string;
      amountCents: number;
      description: string;
      idempotencyKey?: string;
    }): StatementItem => {
      const key = params.idempotencyKey || generateUUID();
      return mockBankingStore.executeTransfer({
        ...params,
        idempotencyKey: key,
      });
    },
    []
  );

  const deposit = useCallback(
    (params: {
      amountCents: number;
      idempotencyKey?: string;
      terminalId?: string;
    }): StatementItem => {
      const key = params.idempotencyKey || generateUUID();
      return mockBankingStore.executeDeposit({
        ...params,
        idempotencyKey: key,
      });
    },
    []
  );

  const withdraw = useCallback(
    (params: {
      amountCents: number;
      idempotencyKey?: string;
      terminalId?: string;
    }): StatementItem => {
      const key = params.idempotencyKey || generateUUID();
      return mockBankingStore.executeWithdraw({
        ...params,
        idempotencyKey: key,
      });
    },
    []
  );

  const lookup = useCallback(
    (agency: string, account: string): AccountLookupResponse => {
      return mockBankingStore.lookupAccount(agency, account);
    },
    []
  );

  const resetData = useCallback(() => {
    mockBankingStore.reset();
  }, []);

  return {
    account: state.account,
    balance: state.balance,
    statement: state.statement,
    transfer,
    deposit,
    withdraw,
    lookup,
    resetData,
  };
}
