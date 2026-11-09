"use client";

import { StatementItem, BalanceInfo, AccountLookupResponse } from './types';

const STORAGE_KEY = 'nexus_bank_ledger_state_v1';

// Mock de base conforme design original
const INITIAL_BALANCE: BalanceInfo = {
  availableCents: 1542050, // R$ 15.420,50
  blockedCents: 0,
  dailyLimitCents: 5000000, // R$ 50.000,00
  usedTodayCents: 1542000, // R$ 15.420,00
  monthlyVariationPercent: 34.2,
  asOfSequence: 142,
  updatedAt: '2026-10-31T18:45:12.000Z',
};

const INITIAL_STATEMENT: StatementItem[] = [
  {
    id: 'tx-77e8a9',
    sequence: 142,
    transactionId: 'tx-77e8a9-nexus-prod',
    journalId: 'jrn-990142',
    timestamp: '2026-10-31T18:45:12.000Z',
    category: 'TRANSFER',
    type: 'TRANSFER_IN',
    description: 'Recebimento Pix Chave CNPJ',
    counterpartyName: 'Atacado Alfa Brasil Ltda',
    counterpartyDocumentMasked: '***.908.412-**',
    counterpartyAccount: 'Ag 0001 · C/C 45892-1',
    nature: 'CREDIT',
    amountCents: 450000, // R$ 4.500,00
    balanceAfterCents: 1542050,
    idempotencyKey: 'c9b24e60-84a1-4321-9e22-094fa817b120',
    debitAccount: 'ACC:0001-45892-1 (Alfa)',
    creditAccount: 'ACC:0001-100234-7 (Carlos Silva)',
    hashSignature: 'sha256:d8a9f4...b0128e',
  },
  {
    id: 'tx-44c3f2',
    sequence: 141,
    transactionId: 'tx-44c3f2-nexus-prod',
    journalId: 'jrn-990141',
    timestamp: '2026-10-30T14:12:05.000Z',
    category: 'TRANSFER',
    type: 'TRANSFER_OUT',
    description: 'Liquidação Fornecedor Energia S/A',
    counterpartyName: 'Energia Solar Sul S.A.',
    counterpartyDocumentMasked: '***.102.889-**',
    counterpartyAccount: 'Ag 0003 · C/C 99014-2',
    nature: 'DEBIT',
    amountCents: 125000, // R$ 1.250,00
    balanceAfterCents: 1092050,
    idempotencyKey: 'b4a1890e-11f2-491c-b290-33df019a8421',
    debitAccount: 'ACC:0001-100234-7 (Carlos Silva)',
    creditAccount: 'ACC:0003-99014-2 (Energia)',
    hashSignature: 'sha256:99f1a0...cb881a',
  },
  {
    id: 'tx-rej-140',
    sequence: 140,
    transactionId: 'tx-rej-140-nexus-prod',
    timestamp: '2026-10-29T11:02:44.000Z',
    category: 'FAILED',
    type: 'REJECTED',
    description: 'Transferência Não Realizada',
    counterpartyName: 'Global Investments Holding',
    counterpartyDocumentMasked: '***.441.770-**',
    counterpartyAccount: 'Ag 0001 · C/C 87123-0',
    nature: 'NONE',
    amountCents: 3500000, // R$ 35.000,00
    balanceAfterCents: 1217050,
    idempotencyKey: 'fe1290aa-9901-447a-8742-1200fa88bb71',
    failureReason: 'Saldo insuficiente em conta no momento da execução.',
  },
  {
    id: 'tx-11b099',
    sequence: 139,
    transactionId: 'tx-11b099-nexus-prod',
    journalId: 'jrn-990139',
    timestamp: '2026-10-28T09:18:22.000Z',
    category: 'DEPOSIT',
    type: 'DEPOSIT',
    description: 'Depósito em Espécie ATM Seguro',
    counterpartyName: 'Terminal Autoatendimento 04',
    counterpartyDocumentMasked: 'SYS-CASH-04',
    counterpartyAccount: 'Agência Centro 0001',
    nature: 'CREDIT',
    amountCents: 200000, // R$ 2.000,00
    balanceAfterCents: 1217050,
    idempotencyKey: '09da128f-7712-4231-aa10-098eefa20188',
    debitAccount: 'SYS:CASH_SHARD_04',
    creditAccount: 'ACC:0001-100234-7 (Carlos Silva)',
    hashSignature: 'sha256:1a84f0...bc0144',
  },
  {
    id: 'tx-08f321',
    sequence: 138,
    transactionId: 'tx-08f321-nexus-prod',
    journalId: 'jrn-990138',
    timestamp: '2026-10-27T16:40:11.000Z',
    category: 'WITHDRAWAL',
    type: 'WITHDRAWAL',
    description: 'Saque em Caixa Eletrônico',
    counterpartyName: 'Terminal Autoatendimento 24h',
    counterpartyDocumentMasked: 'SYS-CASH-11',
    counterpartyAccount: 'Terminal ATM Nexus',
    nature: 'DEBIT',
    amountCents: 40000, // R$ 400,00
    balanceAfterCents: 1017050,
    idempotencyKey: '7f8c41ea-9a82-416b-a25b-de095cc1a89f',
    debitAccount: 'ACC:0001-100234-7 (Carlos Silva)',
    creditAccount: 'SYS:CASH_SHARD_11',
    hashSignature: 'sha256:77bc09...ff912a',
  },
  {
    id: 'tx-05a812',
    sequence: 137,
    transactionId: 'tx-05a812-nexus-prod',
    journalId: 'jrn-990137',
    timestamp: '2026-10-26T11:30:00.000Z',
    category: 'DEPOSIT',
    type: 'DEPOSIT',
    description: 'Depósito em Espécie',
    counterpartyName: 'Terminal Autoatendimento',
    counterpartyDocumentMasked: 'SYS-CASH-01',
    counterpartyAccount: 'Agência Centro 0001',
    nature: 'CREDIT',
    amountCents: 500000, // R$ 5.000,00
    balanceAfterCents: 1057050,
    idempotencyKey: '11aa9944-bc81-4200-9122-38472901a88b',
    debitAccount: 'SYS:CASH_SHARD_01',
    creditAccount: 'ACC:0001-100234-7 (Carlos Silva)',
    hashSignature: 'sha256:55aa33...11cc99',
  },
  {
    id: 'tx-03e190',
    sequence: 136,
    transactionId: 'tx-03e190-nexus-prod',
    journalId: 'jrn-990136',
    timestamp: '2026-10-25T14:22:18.000Z',
    category: 'TRANSFER',
    type: 'TRANSFER_IN',
    description: 'Transferência Recebida TED/Pix',
    counterpartyName: 'Mariana Souza Costa',
    counterpartyDocumentMasked: '***.331.092-**',
    counterpartyAccount: 'Ag 0001 · C/C 78441-9',
    nature: 'CREDIT',
    amountCents: 185000, // R$ 1.850,00
    balanceAfterCents: 557050,
    idempotencyKey: '88bb2211-1902-411a-b331-4471902ba811',
    debitAccount: 'ACC:0001-78441-9 (Mariana)',
    creditAccount: 'ACC:0001-100234-7 (Carlos Silva)',
    hashSignature: 'sha256:44aa11...88dd22',
  },
  {
    id: 'tx-01a001',
    sequence: 135,
    transactionId: 'tx-01a001-nexus-prod',
    journalId: 'jrn-990135',
    timestamp: '2026-10-24T08:00:00.000Z',
    category: 'DEPOSIT',
    type: 'DEPOSIT',
    description: 'Aporte Inicial de Capital',
    counterpartyName: 'Aporte de Capital em Conta',
    counterpartyDocumentMasked: 'BACEN-CLEARING',
    counterpartyAccount: 'Clearing Central Bacen',
    nature: 'CREDIT',
    amountCents: 372050, // R$ 3.720,50
    balanceAfterCents: 372050,
    idempotencyKey: '00aa1122-3344-5566-7788-99aabbccdde0',
    debitAccount: 'SYS:BACEN_RESERVE',
    creditAccount: 'ACC:0001-100234-7 (Carlos Silva)',
    hashSignature: 'sha256:001122...334455',
  },
];

export interface BankingState {
  account: {
    agency: string;
    accountNumber: string;
    digit: string;
    holderName: string;
    companyName: string;
    cpfMasked: string;
    status: 'ACTIVE' | 'PENDING' | 'BLOCKED';
  };
  balance: BalanceInfo;
  statement: StatementItem[];
}

let memoryState: BankingState | null = null;

function getServerSnapshotState(): BankingState {
  return {
    account: {
      agency: '0001',
      accountNumber: '100234',
      digit: '7',
      holderName: 'Carlos Eduardo Silva',
      companyName: 'Carlos Eduardo Silva ME',
      cpfMasked: '***.482.908-**',
      status: 'ACTIVE',
    },
    balance: INITIAL_BALANCE,
    statement: INITIAL_STATEMENT,
  };
}

function loadState(): BankingState {
  if (typeof window === 'undefined') {
    return getServerSnapshotState();
  }

  if (memoryState) {
    return memoryState;
  }

  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) {
      memoryState = JSON.parse(raw);
      return memoryState!;
    }
  } catch (e) {
    console.error('Falha ao ler state do localStorage', e);
  }

  memoryState = getServerSnapshotState();

  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(memoryState));
  } catch {
    // Ignora
  }

  return memoryState;
}

function saveState(state: BankingState) {
  memoryState = state;
  if (typeof window === 'undefined') return;
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    // Dispara custom event para atualizar listeners na mesma aba
    window.dispatchEvent(new Event('nexus_banking_state_change'));
  } catch (e) {
    console.error('Falha ao salvar state no localStorage', e);
  }
}

export function generateUUID(): string {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

/**
 * Operações do Store Reativo
 */
export const mockBankingStore = {
  subscribe(callback: () => void): () => void {
    if (typeof window === 'undefined') return () => {};
    window.addEventListener('nexus_banking_state_change', callback);
    window.addEventListener('storage', callback);
    return () => {
      window.removeEventListener('nexus_banking_state_change', callback);
      window.removeEventListener('storage', callback);
    };
  },

  getServerSnapshot(): BankingState {
    return getServerSnapshotState();
  },

  getState(): BankingState {
    return loadState();
  },

  reset(): void {
    if (typeof window === 'undefined') return;
    localStorage.removeItem(STORAGE_KEY);
    saveState({
      account: {
        agency: '0001',
        accountNumber: '100234',
        digit: '7',
        holderName: 'Carlos Eduardo Silva',
        companyName: 'Carlos Eduardo Silva ME',
        cpfMasked: '***.482.908-**',
        status: 'ACTIVE',
      },
      balance: INITIAL_BALANCE,
      statement: INITIAL_STATEMENT,
    });
  },

  lookupAccount(agency: string, number: string): AccountLookupResponse {
    const cleanNum = number.replace(/\D/g, '');
    if (cleanNum === '123456' || cleanNum === '1234567') {
      return {
        accountId: 'acc-908123-nexus',
        agency: agency || '0001',
        number: '123456-7',
        holderNameMasked: 'Jo** Si*** dos Sa****',
        documentMasked: '***.781.904-**',
        bankName: 'Nexus Bank (341)',
        accountType: 'Conta Corrente',
      };
    }
    if (cleanNum === '45892' || cleanNum === '458921') {
      return {
        accountId: 'acc-458921-nexus',
        agency: agency || '0001',
        number: '45892-1',
        holderNameMasked: 'Atacado Alfa Brasil Ltda',
        documentMasked: '18.***.***/0001',
        bankName: 'Nexus Bank (341)',
        accountType: 'Conta Corrente Empresarial',
      };
    }
    return {
      accountId: `acc-${cleanNum || 'dest'}-nexus`,
      agency: agency || '0001',
      number: number.includes('-') ? number : `${number.slice(0, -1)}-${number.slice(-1) || '0'}`,
      holderNameMasked: 'Ma**** Fer***** de Oli*****',
      documentMasked: '***.248.910-**',
      bankName: 'Nexus Bank (341)',
      accountType: 'Conta Corrente',
    };
  },

  executeTransfer(params: {
    destinationAgency: string;
    destinationAccount: string;
    amountCents: number;
    description: string;
    idempotencyKey: string;
  }): StatementItem {
    const state = loadState();

    // 1. Verificação de Idempotência: se a operação já foi executada, retorna o item existente sem duplo débito
    if (params.idempotencyKey) {
      const existing = state.statement.find((item) => item.idempotencyKey === params.idempotencyKey);
      if (existing) {
        return existing;
      }
    }

    // 2. Validação estrita de limites e integridade numérica
    if (!Number.isFinite(params.amountCents) || !Number.isInteger(params.amountCents) || params.amountCents <= 0) {
      throw new Error('O valor da transferência deve ser um número inteiro positivo em centavos.');
    }

    if (params.amountCents > 5000000) {
      throw new Error('O valor excede o limite regulatório máximo de R$ 50.000,00 por transferência.');
    }

    // 3. Regra TR-02: Prevenção de transferência para a própria conta de origem
    const cleanDestAgency = params.destinationAgency.replace(/\D/g, '');
    const cleanDestAccount = params.destinationAccount.replace(/\D/g, '');
    const cleanOrigAgency = state.account.agency.replace(/\D/g, '');
    const cleanOrigAccount = state.account.accountNumber.replace(/\D/g, '');

    if (cleanDestAgency === cleanOrigAgency && cleanDestAccount === cleanOrigAccount) {
      throw new Error('Não é permitido realizar transferências para a mesma conta de origem.');
    }

    if (params.amountCents > state.balance.availableCents) {
      throw new Error('Saldo insuficiente para realizar a transferência.');
    }

    const availableLimit = state.balance.dailyLimitCents - state.balance.usedTodayCents;
    if (params.amountCents > availableLimit) {
      throw new Error(`Limite diário operacional excedido. Disponível: R$ ${(availableLimit / 100).toFixed(2)}`);
    }

    const lookup = this.lookupAccount(params.destinationAgency, params.destinationAccount);
    const newSeq = state.balance.asOfSequence + 1;
    const newBalance = state.balance.availableCents - params.amountCents;

    const newItem: StatementItem = {
      id: `tx-${generateUUID().substring(0, 8)}`,
      sequence: newSeq,
      transactionId: `tx-${generateUUID().substring(0, 8)}-nexus-prod`,
      journalId: `jrn-${newSeq}`,
      timestamp: new Date().toISOString(),
      category: 'TRANSFER',
      type: 'TRANSFER_OUT',
      description: params.description || 'Transferência entre Contas Nexus',
      counterpartyName: lookup.holderNameMasked,
      counterpartyDocumentMasked: lookup.documentMasked || '***.***.***-**',
      counterpartyAccount: `Ag ${lookup.agency} · C/C ${lookup.number}`,
      nature: 'DEBIT',
      amountCents: params.amountCents,
      balanceAfterCents: newBalance,
      idempotencyKey: params.idempotencyKey,
      debitAccount: `ACC:0001-${state.account.accountNumber}-${state.account.digit} (${state.account.holderName})`,
      creditAccount: `ACC:${lookup.agency}-${lookup.number} (${lookup.holderNameMasked})`,
      hashSignature: `sha256:${generateUUID().replace(/-/g, '').substring(0, 32)}`,
    };

    const updatedState: BankingState = {
      ...state,
      balance: {
        ...state.balance,
        availableCents: newBalance,
        usedTodayCents: state.balance.usedTodayCents + params.amountCents,
        asOfSequence: newSeq,
        updatedAt: new Date().toISOString(),
      },
      statement: [newItem, ...state.statement],
    };

    saveState(updatedState);
    return newItem;
  },

  executeDeposit(params: {
    amountCents: number;
    idempotencyKey: string;
    terminalId?: string;
  }): StatementItem {
    const state = loadState();

    // 1. Verificação de Idempotência
    if (params.idempotencyKey) {
      const existing = state.statement.find((item) => item.idempotencyKey === params.idempotencyKey);
      if (existing) {
        return existing;
      }
    }

    // 2. Validação numérica
    if (!Number.isFinite(params.amountCents) || !Number.isInteger(params.amountCents) || params.amountCents <= 0) {
      throw new Error('O valor do depósito deve ser um número inteiro positivo em centavos.');
    }

    if (params.amountCents > 5000000) {
      throw new Error('O valor do depósito excede o limite máximo permitido de R$ 50.000,00 por operação.');
    }

    const newSeq = state.balance.asOfSequence + 1;
    const newBalance = state.balance.availableCents + params.amountCents;
    const terminal = params.terminalId || 'Terminal Autoatendimento 04';

    const newItem: StatementItem = {
      id: `tx-${generateUUID().substring(0, 8)}`,
      sequence: newSeq,
      transactionId: `tx-${generateUUID().substring(0, 8)}-nexus-prod`,
      journalId: `jrn-${newSeq}`,
      timestamp: new Date().toISOString(),
      category: 'DEPOSIT',
      type: 'DEPOSIT',
      description: 'Depósito em Espécie ATM Seguro',
      counterpartyName: terminal,
      counterpartyDocumentMasked: 'SYS-CASH-CLEARING',
      counterpartyAccount: 'Agência Centro 0001',
      nature: 'CREDIT',
      amountCents: params.amountCents,
      balanceAfterCents: newBalance,
      idempotencyKey: params.idempotencyKey,
      debitAccount: 'SYS:CASH_SHARD_04',
      creditAccount: `ACC:0001-${state.account.accountNumber}-${state.account.digit} (${state.account.holderName})`,
      hashSignature: `sha256:${generateUUID().replace(/-/g, '').substring(0, 32)}`,
    };

    const updatedState: BankingState = {
      ...state,
      balance: {
        ...state.balance,
        availableCents: newBalance,
        asOfSequence: newSeq,
        updatedAt: new Date().toISOString(),
      },
      statement: [newItem, ...state.statement],
    };

    saveState(updatedState);
    return newItem;
  },

  executeWithdraw(params: {
    amountCents: number;
    idempotencyKey: string;
    terminalId?: string;
  }): StatementItem {
    const state = loadState();

    // 1. Verificação de Idempotência
    if (params.idempotencyKey) {
      const existing = state.statement.find((item) => item.idempotencyKey === params.idempotencyKey);
      if (existing) {
        return existing;
      }
    }

    // 2. Validação numérica
    if (!Number.isFinite(params.amountCents) || !Number.isInteger(params.amountCents) || params.amountCents <= 0) {
      throw new Error('O valor do saque deve ser um número inteiro positivo em centavos.');
    }

    if (params.amountCents > 5000000) {
      throw new Error('O valor do saque excede o limite máximo permitido de R$ 50.000,00 por operação.');
    }

    if (params.amountCents > state.balance.availableCents) {
      throw new Error('Saldo insuficiente para realizar o saque.');
    }

    const newSeq = state.balance.asOfSequence + 1;
    const newBalance = state.balance.availableCents - params.amountCents;
    const terminal = params.terminalId || 'Terminal ATM 24h Nexus';

    const newItem: StatementItem = {
      id: `tx-${generateUUID().substring(0, 8)}`,
      sequence: newSeq,
      transactionId: `tx-${generateUUID().substring(0, 8)}-nexus-prod`,
      journalId: `jrn-${newSeq}`,
      timestamp: new Date().toISOString(),
      category: 'WITHDRAWAL',
      type: 'WITHDRAWAL',
      description: 'Saque em Caixa Eletrônico',
      counterpartyName: terminal,
      counterpartyDocumentMasked: 'SYS-CASH-11',
      counterpartyAccount: 'Terminal Autoatendimento',
      nature: 'DEBIT',
      amountCents: params.amountCents,
      balanceAfterCents: newBalance,
      idempotencyKey: params.idempotencyKey,
      debitAccount: `ACC:0001-${state.account.accountNumber}-${state.account.digit} (${state.account.holderName})`,
      creditAccount: 'SYS:CASH_SHARD_11',
      hashSignature: `sha256:${generateUUID().replace(/-/g, '').substring(0, 32)}`,
    };

    const updatedState: BankingState = {
      ...state,
      balance: {
        ...state.balance,
        availableCents: newBalance,
        asOfSequence: newSeq,
        updatedAt: new Date().toISOString(),
      },
      statement: [newItem, ...state.statement],
    };

    saveState(updatedState);
    return newItem;
  },
};
