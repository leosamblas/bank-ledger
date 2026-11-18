"use client";

import React, { useState, useMemo } from 'react';
import Link from 'next/link';
import { useBanking } from '@/hooks/useBanking';
import { StatementItem, TransactionCategory } from '@/services/types';
import { formatCentsToBrl } from '@/utils/currency';
import { formatDateTimeBr } from '@/utils/date';
import {
  exportStatementToCsv,
  exportStatementToOfx,
  getTransactionTypeLabel as getLabelFromUtil,
  getCanonicalProtocol,
} from '@/utils/exportStatement';
import { CopyButton } from '@/components/ui/CopyButton';
import { StatementRowSkeleton } from '@/components/ui/Skeleton';

/**
 * Humanized Portuguese transaction labels:
 * TRANSFER_OUT: "Transferência enviada"
 * TRANSFER_IN: "Transferência recebida"
 * DEPOSIT: "Depósito em conta corrente"
 * WITHDRAWAL: "Saque em terminal"
 */
export function getTransactionTypeLabel(type: string): string {
  return getLabelFromUtil(type);
}

/**
 * Formata a exibição clara da contraparte em cada linha do extrato:
 * Se DÉBITO: "Para: " + counterpartyName + (counterpartyAccount ? ` (${counterpartyAccount})` : '')
 * Se CRÉDITO: "De: " + counterpartyName + (counterpartyAccount ? ` (${counterpartyAccount})` : '')
 */
export function formatCounterpartyRow(item: {
  nature: string;
  counterpartyName?: string;
  counterpartyAccount?: string;
}): string {
  const prefix = item.nature === 'DEBIT' ? 'Para: ' : 'De: ';
  const name = item.counterpartyName || 'Não informado';
  const accountPart = item.counterpartyAccount ? ` (${item.counterpartyAccount})` : '';
  return `${prefix}${name}${accountPart}`;
}

/**
 * Retorna o número de protocolo canônico bancário (item.protocolNumber || 'PRT-...')
 */
export function getCanonicalProtocolNumber(item: {
  protocolNumber?: string;
  sequence?: number;
  timestamp?: string;
}): string {
  return getCanonicalProtocol(item as Partial<StatementItem>);
}

/**
 * Implementação padrão de SHA-256 (FIPS 180-4) para geração síncrona de hash criptográfico
 */
function sha256Hex(ascii: string): string {
  function r(v: number, a: number): number {
    return (v >>> a) | (v << (32 - a));
  }
  const P = Math.pow;
  const M = P(2, 32);
  let l = ascii.length;
  let i: number;
  let j: number;
  let res = '';
  const w: number[] = [];
  const bL = l * 8;
  let h: number[] = [];
  const k: number[] = [];
  let p = 0;
  const comp: Record<number, boolean> = {};

  for (let c = 2; p < 64; c++) {
    if (!comp[c]) {
      for (i = 0; i < 313; i += c) comp[i] = true;
      h[p] = (P(c, 0.5) * M) | 0;
      k[p++] = (P(c, 1 / 3) * M) | 0;
    }
  }

  ascii += '\x80';
  while ((ascii.length % 64) - 56) ascii += '\x00';
  for (i = 0; i < ascii.length; i++) {
    j = ascii.charCodeAt(i);
    w[i >> 2] |= j << (((3 - i) % 4) * 8);
  }
  w[w.length] = (bL / M) | 0;
  w[w.length] = bL;

  for (j = 0; j < w.length; ) {
    const s = w.slice(j, (j += 16));
    const oH = h.slice(0);
    for (i = 0; i < 64; i++) {
      const a = h[0];
      const e = h[4];
      const w15 = s[i - 15];
      const w2 = s[i - 2];
      const t1 =
        (h[7] +
          (r(e, 6) ^ r(e, 11) ^ r(e, 25)) +
          ((e & h[5]) ^ (~e & h[6])) +
          k[i] +
          (s[i] =
            i < 16
              ? s[i]
              : (s[i - 16] +
                  (r(w15, 7) ^ r(w15, 18) ^ (w15 >>> 3)) +
                  s[i - 7] +
                  (r(w2, 17) ^ r(w2, 19) ^ (w2 >>> 10))) |
                0)) |
        0;
      const t2 =
        ((r(a, 2) ^ r(a, 13) ^ r(a, 22)) +
          ((a & h[1]) ^ (a & h[2]) ^ (h[1] & h[2]))) |
        0;
      h = [(t1 + t2) | 0, a, h[1], h[2], (h[3] + t1) | 0, e, h[5], h[6]];
    }
    for (i = 0; i < 8; i++) h[i] = (h[i] + oH[i]) | 0;
  }

  for (i = 0; i < 8; i++) {
    for (j = 3; j >= 0; j--) {
      const b = (h[i] >> (8 * j)) & 255;
      res += (b < 16 ? '0' : '') + b.toString(16);
    }
  }
  return res;
}

/**
 * Gera hash SHA-256 de autenticação eletrônica determinístico para auditoria
 */
export function getReceiptAuthHash(item: Partial<StatementItem>): string {
  if (item.hashSignature) return item.hashSignature.toUpperCase();
  const seed = `${item.sequence ?? 0}-${item.idempotencyKey ?? ''}-${item.timestamp ?? ''}-${item.amountCents ?? 0}`;
  return sha256Hex(seed).toUpperCase();
}

export default function StatementPage() {
  const { balance, statement, account } = useBanking();

  const [selectedCategory, setSelectedCategory] = useState<TransactionCategory>('ALL');
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedReceipt, setSelectedReceipt] = useState<StatementItem | null>(null);
  const [consolidatedModalOpen, setConsolidatedModalOpen] = useState(false);
  const [exportModalOpen, setExportModalOpen] = useState(false);
  const [exportFormat, setExportFormat] = useState<'pdf' | 'csv' | 'ofx'>('pdf');
  const [exportAlert, setExportAlert] = useState('');

  // Contadores para chips rápidos
  const counts = useMemo(() => {
    let credit = 0;
    let debit = 0;
    let failed = 0;

    statement.forEach((tx) => {
      if (tx.category === 'FAILED') failed++;
      else if (tx.nature === 'CREDIT') credit++;
      else if (tx.nature === 'DEBIT') debit++;
    });

    return {
      all: statement.length,
      credit,
      debit,
      failed,
    };
  }, [statement]);

  // Cálculos de totais no período
  const totals = useMemo(() => {
    let creditTotal = 0;
    let debitTotal = 0;

    statement.forEach((tx) => {
      if (tx.category !== 'FAILED') {
        if (tx.nature === 'CREDIT') creditTotal += tx.amountCents;
        if (tx.nature === 'DEBIT') debitTotal += tx.amountCents;
      }
    });

    return { creditTotal, debitTotal };
  }, [statement]);

  // Filtros aplicados
  const filteredStatement = useMemo(() => {
    return statement.filter((tx) => {
      let matchesCategory = true;
      if (selectedCategory === 'CREDIT') matchesCategory = tx.nature === 'CREDIT';
      else if (selectedCategory === 'DEBIT') matchesCategory = tx.nature === 'DEBIT';
      else if (selectedCategory === 'TRANSFER') matchesCategory = tx.category === 'TRANSFER';
      else if (selectedCategory === 'DEPOSIT') matchesCategory = tx.category === 'DEPOSIT';
      else if (selectedCategory === 'WITHDRAWAL') matchesCategory = tx.category === 'WITHDRAWAL';
      else if (selectedCategory === 'FAILED') matchesCategory = tx.category === 'FAILED';

      if (!matchesCategory) return false;

      if (!searchTerm.trim()) return true;

      const q = searchTerm.toLowerCase();
      const matchText =
        tx.description.toLowerCase().includes(q) ||
        tx.counterpartyName.toLowerCase().includes(q) ||
        tx.counterpartyAccount.toLowerCase().includes(q) ||
        tx.transactionId.toLowerCase().includes(q) ||
        tx.sequence.toString().includes(q);

      return matchText;
    });
  }, [statement, selectedCategory, searchTerm]);

  const handleExport = () => {
    setExportModalOpen(false);

    if (exportFormat === 'csv') {
      exportStatementToCsv(filteredStatement, account);
      setExportAlert('Extrato em formato CSV baixado com sucesso!');
    } else if (exportFormat === 'ofx') {
      exportStatementToOfx(filteredStatement, account);
      setExportAlert('Arquivo OFX para conciliação contábil baixado com sucesso!');
    } else {
      if (typeof window !== 'undefined') {
        window.print();
      }
      setExportAlert('Comprovante enviado para a fila de impressão / PDF.');
    }

    setTimeout(() => setExportAlert(''), 4000);
  };

  return (
    <div className="w-full px-4 sm:px-6 lg:px-8 max-w-[1600px] mx-auto flex flex-col gap-6">
      {/* Print isolation stylesheet */}
      <style>{`
        @media print {
          body * {
            visibility: hidden;
          }
          #printable-receipt-modal, #printable-receipt-modal *,
          #printable-consolidated-modal, #printable-consolidated-modal * {
            visibility: visible;
          }
          #printable-receipt-modal, #printable-consolidated-modal {
            position: absolute;
            left: 0;
            top: 0;
            width: 100%;
            margin: 0;
            padding: 24px;
            background: white !important;
            color: #111827 !important;
            box-shadow: none !important;
            border: none !important;
            z-index: 99999;
          }
          .no-print, .print\\:hidden {
            display: none !important;
          }
        }
      `}</style>

      {/* Breadcrumb Trail */}
      <nav aria-label="Navegação estrutural" className="flex items-center gap-2 text-body-sm text-on-surface-variant no-print">
        <Link
          href="/dashboard"
          className="hover:text-on-surface inline-flex items-center gap-1.5 transition-colors font-medium text-secondary group no-underline"
        >
          <span className="material-symbols-outlined text-[17px] text-secondary group-hover:text-on-surface transition-colors shrink-0">
            dashboard
          </span>
          <span className="group-hover:underline">Visão Geral</span>
        </Link>
        <span className="material-symbols-outlined text-[14px] text-surface-container-highest">chevron_right</span>
        <span className="text-on-surface font-semibold">Extrato Bancário Completo</span>
      </nav>

      {/* Header com Ações */}
      <div className="w-full bg-surface-container-lowest p-5 md:p-6 rounded-2xl shadow-sm border border-surface-container flex flex-col md:flex-row items-start md:items-center justify-between gap-4 no-print">
        <div>
          <div className="flex items-center gap-2.5">
            <h1 className="font-headline-lg text-headline-lg text-on-surface font-bold tracking-tight">
              Extrato Bancário Completo
            </h1>
            <span className="px-2.5 py-0.5 rounded-lg bg-emerald-500/10 text-emerald-800 border border-emerald-500/20 font-label-caps text-label-caps font-semibold">
              EXTRATO OFICIAL
            </span>
          </div>
          <p className="font-body-md text-body-md text-on-surface-variant mt-1">
            Histórico completo e detalhado de todas as entradas, saídas e movimentações da sua conta.
          </p>
        </div>

        <div className="flex items-center gap-3 w-full md:w-auto">
          <button
            onClick={() => setExportModalOpen(true)}
            className="w-full md:w-auto px-4 py-2.5 rounded-xl bg-surface-container-high hover:bg-surface-variant text-on-surface font-headline-sm text-body-md font-semibold transition-colors flex items-center justify-center gap-2 shadow-xs cursor-pointer"
            type="button"
          >
            <span className="material-symbols-outlined text-[18px]">download</span>
            Exportar Relatório
          </button>
        </div>
      </div>

      {exportAlert && (
        <div className="p-3 bg-emerald-500/10 border border-emerald-500/30 rounded-xl text-emerald-800 text-body-md flex items-center gap-2 animate-fadeIn no-print">
          <span className="material-symbols-outlined text-[18px]">check_circle</span>
          <span>{exportAlert}</span>
        </div>
      )}

      {/* Summary Stats Strip */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 no-print">
        <div className="bg-surface-container-lowest p-5 rounded-2xl border border-surface-container shadow-xs flex flex-col justify-between gap-1">
          <span className="font-label-caps text-label-caps uppercase text-on-surface-variant font-semibold">
            Saldo Disponível Atual
          </span>
          <span className="font-headline-md text-headline-md font-bold text-on-surface">
            {formatCentsToBrl(balance.availableCents)}
          </span>
          <span className="text-[12px] text-emerald-700 flex items-center gap-1">
            <span className="material-symbols-outlined text-[14px]">verified</span>
            Saldo Atualizado em Tempo Real
          </span>
        </div>

        <div className="bg-surface-container-lowest p-5 rounded-2xl border border-surface-container shadow-xs flex flex-col justify-between gap-1">
          <span className="font-label-caps text-label-caps uppercase text-on-surface-variant font-semibold">
            Total de Créditos no Período
          </span>
          <span className="font-headline-md text-headline-md font-bold text-emerald-700">
            +{formatCentsToBrl(totals.creditTotal)}
          </span>
          <span className="text-[12px] text-on-surface-variant">
            {counts.credit} operações de entrada
          </span>
        </div>

        <div className="bg-surface-container-lowest p-5 rounded-2xl border border-surface-container shadow-xs flex flex-col justify-between gap-1">
          <span className="font-label-caps text-label-caps uppercase text-on-surface-variant font-semibold">
            Total de Débitos no Período
          </span>
          <span className="font-headline-md text-headline-md font-bold text-error">
            -{formatCentsToBrl(totals.debitTotal)}
          </span>
          <span className="text-[12px] text-on-surface-variant">
            {counts.debit} operações de saída
          </span>
        </div>

        <div className="bg-surface-container-lowest p-5 rounded-2xl border border-surface-container shadow-xs flex flex-col justify-between gap-1">
          <span className="font-label-caps text-label-caps uppercase text-on-surface-variant font-semibold">
            Status da Conta
          </span>
          <span className="font-code-md text-code-md font-bold text-on-surface">
            Balanço Verificado
          </span>
          <span className="text-[12px] text-emerald-700 flex items-center gap-1 font-semibold">
            <span className="material-symbols-outlined text-[14px]">check_circle</span>
            Todas as operações conciliadas
          </span>
        </div>
      </div>

      {/* Filters & Operational Toolbar */}
      <div className="bg-surface-container-lowest p-5 rounded-2xl shadow-sm border border-surface-container flex flex-col gap-4 no-print">
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {/* Janela Temporal */}
          <div className="flex flex-col gap-1.5">
            <label className="font-label-caps text-label-caps uppercase text-on-surface-variant font-semibold">
              Janela Temporal (ISO 8601)
            </label>
            <div className="relative flex items-center">
              <span className="material-symbols-outlined absolute left-3 text-[18px] text-on-surface-variant pointer-events-none">
                calendar_today
              </span>
              <input
                className="w-full pl-9 pr-3 py-2 bg-surface-container-low text-on-surface font-code-sm text-code-sm rounded-xl border border-transparent focus:border-secondary focus:outline-none"
                readOnly
                type="text"
                value="Últimos 30 dias (Período Atual)"
              />
            </div>
          </div>

          {/* Tipo Movimentação Filter */}
          <div className="flex flex-col gap-1.5">
            <label className="font-label-caps text-label-caps uppercase text-on-surface-variant font-semibold" htmlFor="typeSelect">
              Tipo de Movimentação
            </label>
            <div className="relative flex items-center">
              <span className="material-symbols-outlined absolute left-3 text-[18px] text-on-surface-variant pointer-events-none">
                swap_vert
              </span>
              <select
                id="typeSelect"
                value={selectedCategory}
                onChange={(e) => setSelectedCategory(e.target.value as TransactionCategory)}
                className="w-full pl-9 pr-8 py-2 bg-surface-container-low text-on-surface font-body-md text-body-md rounded-xl border border-transparent focus:border-secondary focus:outline-none cursor-pointer appearance-none"
              >
                <option value="ALL">Todas as operações ({counts.all})</option>
                <option value="CREDIT">Apenas Créditos ({counts.credit})</option>
                <option value="DEBIT">Apenas Débitos ({counts.debit})</option>
                <option value="TRANSFER">Transferências (IN/OUT)</option>
                <option value="DEPOSIT">Depósitos em Espécie</option>
                <option value="WITHDRAWAL">Saques Terminal</option>
                <option value="FAILED">Apenas Falhas / Bloqueios ({counts.failed})</option>
              </select>
              <span className="material-symbols-outlined absolute right-2.5 pointer-events-none text-[18px] text-on-surface-variant">
                expand_more
              </span>
            </div>
          </div>

          {/* Search Bar */}
          <div className="flex flex-col gap-1.5 lg:col-span-2">
            <label className="font-label-caps text-label-caps uppercase text-on-surface-variant font-semibold" htmlFor="searchInput">
              Buscar por Favorecido ou Descrição
            </label>
            <div className="relative flex items-center">
              <span className="material-symbols-outlined absolute left-3 text-[18px] text-on-surface-variant pointer-events-none">
                search
              </span>
              <input
                id="searchInput"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                placeholder="Buscar por nome, favorecido ou descrição..."
                type="text"
                className="w-full pl-9 pr-9 py-2 bg-surface-container-low text-on-surface font-body-md text-body-md rounded-xl border border-transparent focus:border-secondary focus:outline-none"
              />
              {searchTerm && (
                <button
                  type="button"
                  onClick={() => setSearchTerm('')}
                  className="absolute right-2.5 text-on-surface-variant hover:text-on-surface cursor-pointer"
                  aria-label="Limpar busca"
                >
                  <span className="material-symbols-outlined text-[16px]">close</span>
                </button>
              )}
            </div>
          </div>
        </div>

        {/* Quick Status Badges Filter Bar */}
        <div className="flex items-center gap-2 flex-wrap pt-1 border-t border-surface-container">
          <span className="font-label-caps text-label-caps uppercase text-on-surface-variant font-semibold mr-1">
            Filtro Rápido:
          </span>
          <button
            type="button"
            onClick={() => setSelectedCategory('ALL')}
            className={`px-3 py-1 rounded-lg font-code-sm text-code-sm font-semibold transition-colors cursor-pointer ${
              selectedCategory === 'ALL'
                ? 'bg-secondary text-on-secondary'
                : 'bg-surface-container text-on-surface hover:bg-surface-container-high'
            }`}
          >
            Todos ({counts.all})
          </button>
          <button
            type="button"
            onClick={() => setSelectedCategory('CREDIT')}
            className={`px-3 py-1 rounded-lg font-code-sm text-code-sm font-semibold transition-colors cursor-pointer ${
              selectedCategory === 'CREDIT'
                ? 'bg-emerald-700 text-white'
                : 'bg-emerald-500/10 text-emerald-800 hover:bg-emerald-500/20'
            }`}
          >
            + Créditos ({counts.credit})
          </button>
          <button
            type="button"
            onClick={() => setSelectedCategory('DEBIT')}
            className={`px-3 py-1 rounded-lg font-code-sm text-code-sm font-semibold transition-colors cursor-pointer ${
              selectedCategory === 'DEBIT'
                ? 'bg-error text-white'
                : 'bg-error-container/40 text-error hover:bg-error-container'
            }`}
          >
            - Débitos ({counts.debit})
          </button>
          <button
            type="button"
            onClick={() => setSelectedCategory('FAILED')}
            className={`px-3 py-1 rounded-lg font-code-sm text-code-sm font-semibold transition-colors cursor-pointer ${
              selectedCategory === 'FAILED'
                ? 'bg-zinc-800 text-white'
                : 'bg-surface-container text-on-surface-variant hover:bg-surface-container-high'
            }`}
          >
            Falhas ({counts.failed})
          </button>

          <div className="ml-auto hidden xl:flex items-center gap-2 font-code-sm text-code-sm text-on-surface-variant">
            <span className="material-symbols-outlined text-[16px] text-emerald-600">verified_user</span>
            <span>Conciliação Automática Ativa</span>
          </div>
        </div>
      </div>

      {/* Statement Primary Table & Responsive Card Rows */}
      {statement.length === 0 && account.holderName === 'Carregando...' ? (
        <div className="flex flex-col gap-3 no-print">
          <StatementRowSkeleton />
          <StatementRowSkeleton />
          <StatementRowSkeleton />
        </div>
      ) : (
        <div className="bg-surface-container-lowest rounded-2xl shadow-sm border border-surface-container overflow-hidden flex flex-col no-print">
          {/* Table View (Desktop & Tablet Lg) */}
          <div className="hidden lg:block overflow-x-auto w-full">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-surface-container-low font-label-caps text-label-caps uppercase text-on-surface-variant border-b border-surface-container">
                  <th className="py-3 px-4 font-semibold">Data / Hora (UTC-3)</th>
                  <th className="py-3 px-3 font-semibold text-center">Nº Registro</th>
                  <th className="py-3 px-4 font-semibold">Tipo &amp; Descrição</th>
                  <th className="py-3 px-4 font-semibold">Contraparte / Identificação</th>
                  <th className="py-3 px-3 font-semibold text-center">Natureza</th>
                  <th className="py-3 px-4 font-semibold text-right">Valor (R$)</th>
                  <th className="py-3 px-4 font-semibold text-right">Saldo Resultante</th>
                  <th className="py-3 px-4 font-semibold text-center">Ações</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-surface-container-low">
                {filteredStatement.length === 0 ? (
                  <tr>
                    <td colSpan={8} className="py-12 text-center text-on-surface-variant font-body-md">
                      <span className="material-symbols-outlined text-4xl block mb-2 text-surface-container-highest">
                        receipt_long
                      </span>
                      Nenhuma movimentação encontrada com os filtros selecionados.
                    </td>
                  </tr>
                ) : (
                  filteredStatement.map((tx) => {
                    const isCredit = tx.nature === 'CREDIT';
                    const isFailed = tx.category === 'FAILED';
                    const { date, time } = formatDateTimeBr(tx.timestamp);
                    const humanizedType = getTransactionTypeLabel(tx.type);
                    const counterpartyDisplay = formatCounterpartyRow(tx);

                    return (
                      <tr
                        key={tx.id}
                        className={`hover:bg-surface-container-low transition-colors group ${
                          isFailed ? 'bg-error-container/10' : ''
                        }`}
                      >
                        <td className="py-3 px-4 font-code-sm text-code-sm text-on-surface whitespace-nowrap">
                          {date} <span className="text-on-surface-variant">{time}</span>
                        </td>
                        <td className="py-3 px-3 font-code-sm text-code-sm text-center">
                          <span className="px-2 py-0.5 rounded bg-surface-container font-bold text-on-secondary-container">
                            #{tx.sequence}
                          </span>
                        </td>
                        <td className="py-3 px-4">
                          <div className="flex items-center gap-2">
                            <span
                              className={`w-2 h-2 rounded-full ${
                                isFailed
                                  ? 'bg-error'
                                  : isCredit
                                  ? 'bg-emerald-500'
                                  : 'bg-error'
                              }`}
                            ></span>
                            <div className="flex flex-col">
                              <span className="font-code-md text-code-md font-semibold text-on-surface">
                                {humanizedType}
                              </span>
                              <span className="font-body-sm text-body-sm text-on-surface-variant">
                                {tx.description}
                              </span>
                            </div>
                          </div>
                        </td>
                        <td className="py-3 px-4">
                          <div className="flex flex-col">
                            <span className="font-body-md text-body-md font-medium text-on-surface">
                              {counterpartyDisplay}
                            </span>
                            <span className="font-code-sm text-code-sm text-on-surface-variant">
                              {tx.counterpartyDocumentMasked || tx.counterpartyDocument || ''}
                            </span>
                          </div>
                        </td>
                        <td className="py-3 px-3 text-center">
                          <span
                            className={`px-2 py-0.5 rounded font-label-caps text-label-caps uppercase font-semibold ${
                              isFailed
                                ? 'bg-error-container text-on-error-container'
                                : isCredit
                                ? 'bg-emerald-500/10 text-emerald-800'
                                : 'bg-error-container text-on-error-container'
                            }`}
                          >
                            {isFailed ? 'REJEITADO' : isCredit ? 'CRÉDITO (+)' : 'DÉBITO (-)'}
                          </span>
                        </td>
                        <td
                          className={`py-3 px-4 text-right font-code-md text-code-md font-bold whitespace-nowrap ${
                            isFailed
                              ? 'text-on-surface-variant line-through'
                              : isCredit
                              ? 'text-emerald-700'
                              : 'text-error'
                          }`}
                        >
                          {isCredit ? '+' : isFailed ? '' : '-'}
                          {formatCentsToBrl(tx.amountCents)}
                        </td>
                        <td className="py-3 px-4 text-right font-code-md text-code-md font-semibold text-on-surface whitespace-nowrap">
                          {isFailed ? 'Saldo inalterado' : formatCentsToBrl(tx.balanceAfterCents)}
                        </td>
                        <td className="py-3 px-4 text-center">
                          <button
                            type="button"
                            onClick={() => setSelectedReceipt(tx)}
                            className="p-1.5 rounded-lg bg-surface-container text-on-surface-variant hover:text-on-surface hover:bg-surface-container-high transition-colors inline-flex items-center justify-center cursor-pointer"
                            title="Ver Comprovante Detalhado"
                          >
                            <span className="material-symbols-outlined text-[18px]">receipt_long</span>
                          </button>
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>

          {/* Mobile Card List View */}
          <div className="lg:hidden divide-y divide-surface-container">
            {filteredStatement.length === 0 ? (
              <div className="p-8 text-center text-on-surface-variant font-body-md">
                <span className="material-symbols-outlined text-4xl block mb-2 text-surface-container-highest">
                  receipt_long
                </span>
                Nenhuma movimentação encontrada com os filtros selecionados.
              </div>
            ) : (
              filteredStatement.map((tx) => {
                const isCredit = tx.nature === 'CREDIT';
                const isFailed = tx.category === 'FAILED';
                const { date, time } = formatDateTimeBr(tx.timestamp);
                const humanizedType = getTransactionTypeLabel(tx.type);
                const counterpartyDisplay = formatCounterpartyRow(tx);

                return (
                  <div key={tx.id} className="p-4 flex flex-col gap-2">
                    <div className="flex items-center justify-between">
                      <span className="font-code-sm text-xs font-bold text-secondary">#{tx.sequence}</span>
                      <span className="font-code-sm text-xs text-on-surface-variant">{date} {time}</span>
                    </div>
                    <div className="flex items-start justify-between gap-2">
                      <div>
                        <span className="font-semibold text-sm text-on-surface block">{humanizedType}</span>
                        <span className="text-xs text-on-surface-variant block">{tx.description}</span>
                        <span className="text-xs text-on-surface font-medium block mt-0.5">{counterpartyDisplay}</span>
                      </div>
                      <span
                        className={`font-code-md text-sm font-bold whitespace-nowrap ${
                          isFailed ? 'text-on-surface-variant line-through' : isCredit ? 'text-emerald-700' : 'text-error'
                        }`}
                      >
                        {isCredit ? '+' : isFailed ? '' : '-'}{formatCentsToBrl(tx.amountCents)}
                      </span>
                    </div>
                    <div className="flex items-center justify-between pt-1 border-t border-surface-container/60">
                      <span className="text-xs text-on-surface-variant">
                        Saldo após: <strong>{formatCentsToBrl(tx.balanceAfterCents)}</strong>
                      </span>
                      <button
                        type="button"
                        onClick={() => setSelectedReceipt(tx)}
                        className="text-xs text-secondary font-semibold flex items-center gap-1 cursor-pointer"
                      >
                        <span className="material-symbols-outlined text-[15px]">receipt_long</span>
                        Comprovante
                      </button>
                    </div>
                  </div>
                );
              })
            )}
          </div>

          {/* Pagination & Controls Bar */}
          <div className="p-4 bg-surface-container-low flex flex-col md:flex-row items-center justify-between gap-4 border-t border-surface-container">
            <div className="flex flex-col sm:flex-row sm:items-center gap-2 w-full md:w-auto">
              <span className="font-body-sm text-body-sm text-on-surface-variant">
                Exibindo <strong className="text-on-surface font-semibold">{filteredStatement.length}</strong> de{' '}
                <strong className="text-on-surface font-semibold">{statement.length}</strong> movimentações
              </span>
              <span className="hidden sm:inline text-on-surface-variant font-code-sm text-code-sm">·</span>
              <span className="text-xs text-on-surface-variant font-medium">Página 1 de 1</span>
            </div>

            <div className="flex items-center gap-2 w-full sm:w-auto justify-between sm:justify-end">
              <button
                className="px-4 py-2 bg-surface-container text-on-surface-variant opacity-50 cursor-not-allowed font-code-sm text-code-sm font-semibold rounded-xl flex items-center gap-1"
                disabled
                type="button"
              >
                <span className="material-symbols-outlined text-[16px]">chevron_left</span>
                <span>Anterior</span>
              </button>
              <button
                className="px-4 py-2 bg-surface-container text-on-surface-variant opacity-50 cursor-not-allowed font-code-sm text-code-sm font-semibold rounded-xl flex items-center gap-1"
                disabled
                type="button"
              >
                <span>Próxima</span>
                <span className="material-symbols-outlined text-[16px]">chevron_right</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Notice Footer Callout */}
      <div className="bg-surface-container-lowest p-5 rounded-2xl shadow-sm border border-surface-container flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 no-print">
        <div className="flex items-center gap-3">
          <span className="p-2 rounded-xl bg-surface-container text-secondary flex items-center justify-center">
            <span className="material-symbols-outlined text-[24px]">verified_user</span>
          </span>
          <div className="flex flex-col">
            <span className="font-headline-sm text-headline-sm text-on-surface font-semibold">
              Segurança e Conformidade Bancária
            </span>
            <p className="font-body-sm text-body-sm text-on-surface-variant">
              Todos os registros são certificados e auditáveis conforme normas do Banco Central do Brasil.
            </p>
          </div>
        </div>
        <button
          className="px-4 py-2 rounded-xl bg-surface-container-high text-on-surface hover:bg-surface-variant font-headline-sm text-[13px] font-semibold whitespace-nowrap transition-colors flex items-center gap-1.5 cursor-pointer"
          onClick={() => setConsolidatedModalOpen(true)}
          type="button"
        >
          <span className="material-symbols-outlined text-[18px]">description</span>
          <span>Comprovante Consolidado</span>
        </button>
      </div>

      {/* ========================================================================= */}
      {/* Modal 1: Comprovante de Transação Oficial com Impressão Limpa (@media print) */}
      {/* ========================================================================= */}
      {selectedReceipt && (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-xs flex items-center justify-center p-4 animate-fadeIn overflow-y-auto">
          <div
            id="printable-receipt-modal"
            className="bg-surface-container-lowest w-full max-w-xl rounded-2xl p-6 sm:p-8 shadow-2xl border border-surface-container flex flex-col gap-5 my-auto"
          >
            {/* Header Oficial Nexus Bank */}
            <div className="flex items-start justify-between border-b border-surface-container pb-4">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-primary text-on-primary flex items-center justify-center font-bold text-lg">
                  NB
                </div>
                <div>
                  <h3 className="font-headline-md text-base sm:text-lg text-on-surface font-bold tracking-tight">
                    Nexus Bank S.A. - Banco 001
                  </h3>
                  <p className="text-xs text-on-surface-variant">
                    Comprovante Oficial de Operação Financeira
                  </p>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setSelectedReceipt(null)}
                className="p-1.5 rounded-lg hover:bg-surface-container text-on-surface-variant cursor-pointer no-print"
                aria-label="Fechar comprovante"
              >
                <span className="material-symbols-outlined text-[20px]">close</span>
              </button>
            </div>

            {/* Protocolo Canônico e Dados da Transação */}
            <div className="space-y-3 font-body-sm">
              <div className="p-3.5 rounded-xl bg-surface-container-low border border-surface-container flex flex-col sm:flex-row sm:items-center justify-between gap-1">
                <span className="text-on-surface-variant font-medium">Protocolo Canônico:</span>
                <div className="flex items-center gap-2">
                  <span className="font-mono font-bold text-secondary text-sm">
                    {getCanonicalProtocolNumber(selectedReceipt)}
                  </span>
                  <div className="no-print">
                    <CopyButton
                      textToCopy={getCanonicalProtocolNumber(selectedReceipt)}
                      label="Protocolo"
                    />
                  </div>
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs">
                <div className="p-2.5 rounded-lg bg-surface-container-low">
                  <span className="text-on-surface-variant block">Data da Operação:</span>
                  <span className="font-mono font-semibold text-on-surface text-sm">
                    {formatDateTimeBr(selectedReceipt.timestamp).date}
                  </span>
                </div>
                <div className="p-2.5 rounded-lg bg-surface-container-low">
                  <span className="text-on-surface-variant block">Hora Exata (Brasília):</span>
                  <span className="font-mono font-semibold text-on-surface text-sm">
                    {formatDateTimeBr(selectedReceipt.timestamp).time}
                  </span>
                </div>
              </div>

              <div className="flex justify-between items-center p-3 rounded-lg bg-surface-container-low">
                <span className="text-on-surface-variant">Tipo de Operação:</span>
                <span className="font-semibold text-on-surface">
                  {getTransactionTypeLabel(selectedReceipt.type)}
                </span>
              </div>

              <div className="flex justify-between items-center p-3 rounded-lg bg-surface-container-low">
                <span className="text-on-surface-variant">Descrição / Motivo:</span>
                <span className="font-medium text-on-surface text-right">
                  {selectedReceipt.description}
                </span>
              </div>

              {/* Origem (Pagador) e Destino (Favorecido) */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-1">
                {/* Dados de Origem (Pagador) */}
                <div className="p-3.5 rounded-xl bg-surface-container border border-surface-container-high flex flex-col gap-1.5">
                  <span className="font-label-caps text-[11px] uppercase text-secondary font-bold tracking-wider">
                    Origem (Pagador)
                  </span>
                  <div className="text-xs space-y-1 font-mono">
                    <div className="flex justify-between">
                      <span className="text-on-surface-variant">Nome:</span>
                      <span className="font-semibold text-on-surface text-right truncate max-w-[150px]">
                        {selectedReceipt.nature === 'DEBIT'
                          ? account.holderName
                          : selectedReceipt.type === 'DEPOSIT'
                          ? 'Depósito em Espécie'
                          : selectedReceipt.counterpartyName || 'Origem Externa'}
                      </span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-on-surface-variant">CPF / Doc:</span>
                      <span className="text-on-surface">
                        {selectedReceipt.nature === 'DEBIT'
                          ? account.cpfMasked
                          : selectedReceipt.type === 'DEPOSIT'
                          ? '-'
                          : selectedReceipt.counterpartyDocument || selectedReceipt.counterpartyDocumentMasked || 'Não informado'}
                      </span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-on-surface-variant">Agência:</span>
                      <span className="text-on-surface">
                        {selectedReceipt.nature === 'DEBIT'
                          ? account.agency
                          : selectedReceipt.debitAccount?.split('/')[0] || account.agency}
                      </span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-on-surface-variant">Conta:</span>
                      <span className="text-on-surface">
                        {selectedReceipt.nature === 'DEBIT'
                          ? `${account.accountNumber}-${account.digit}`
                          : selectedReceipt.counterpartyAccount || selectedReceipt.debitAccount || 'Conta Externa'}
                      </span>
                    </div>
                  </div>
                </div>

                {/* Dados do Destino (Favorecido) */}
                <div className="p-3.5 rounded-xl bg-surface-container border border-surface-container-high flex flex-col gap-1.5">
                  <span className="font-label-caps text-[11px] uppercase text-secondary font-bold tracking-wider">
                    Destino (Favorecido)
                  </span>
                  <div className="text-xs space-y-1 font-mono">
                    <div className="flex justify-between">
                      <span className="text-on-surface-variant">Nome:</span>
                      <span className="font-semibold text-on-surface text-right truncate max-w-[150px]">
                        {selectedReceipt.nature === 'CREDIT'
                          ? account.holderName
                          : selectedReceipt.type === 'WITHDRAWAL'
                          ? 'Terminal de Autoatendimento'
                          : selectedReceipt.counterpartyName || 'Favorecido'}
                      </span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-on-surface-variant">CPF / Doc:</span>
                      <span className="text-on-surface">
                        {selectedReceipt.nature === 'CREDIT'
                          ? account.cpfMasked
                          : selectedReceipt.type === 'WITHDRAWAL'
                          ? '-'
                          : selectedReceipt.counterpartyDocument || selectedReceipt.counterpartyDocumentMasked || 'Não informado'}
                      </span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-on-surface-variant">Agência:</span>
                      <span className="text-on-surface">
                        {selectedReceipt.nature === 'CREDIT'
                          ? account.agency
                          : selectedReceipt.creditAccount?.split('/')[0] || '0001'}
                      </span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-on-surface-variant">Conta:</span>
                      <span className="text-on-surface">
                        {selectedReceipt.nature === 'CREDIT'
                          ? `${account.accountNumber}-${account.digit}`
                          : selectedReceipt.counterpartyAccount || selectedReceipt.creditAccount || 'Não informada'}
                      </span>
                    </div>
                  </div>
                </div>
              </div>

              {/* Valores e Saldo Após */}
              <div className="p-3.5 rounded-xl bg-surface-container-low border border-surface-container flex items-center justify-between">
                <div>
                  <span className="text-xs text-on-surface-variant block">Valor da Operação:</span>
                  <span
                    className={`font-mono font-bold text-lg ${
                      selectedReceipt.nature === 'CREDIT' ? 'text-emerald-700' : 'text-error'
                    }`}
                  >
                    {selectedReceipt.nature === 'CREDIT' ? '+' : '-'}
                    {formatCentsToBrl(selectedReceipt.amountCents)}
                  </span>
                </div>
                <div className="text-right">
                  <span className="text-xs text-on-surface-variant block">Saldo Resultante Após:</span>
                  <span className="font-mono font-bold text-base text-on-surface">
                    {formatCentsToBrl(selectedReceipt.balanceAfterCents)}
                  </span>
                </div>
              </div>

              {/* Autenticação Eletrônica (Hash SHA-256) */}
              <div className="p-3 rounded-xl bg-surface-container-low/70 border border-surface-container space-y-1 text-xs">
                <span className="font-label-caps text-[11px] uppercase text-on-surface-variant font-bold block">
                  Autenticação Eletrônica (Hash SHA-256)
                </span>
                <p className="font-mono text-[11px] text-on-surface break-all select-all">
                  {getReceiptAuthHash(selectedReceipt)}
                </p>
                <div className="flex items-center gap-1.5 text-[10px] text-emerald-700 pt-0.5">
                  <span className="material-symbols-outlined text-[14px]">verified</span>
                  <span>Operação autenticada e liquidada nos termos da regulação do Banco Central do Brasil.</span>
                </div>
              </div>
            </div>

            {/* Ações do Modal */}
            <div className="flex gap-2.5 pt-3 border-t border-surface-container no-print">
              <button
                type="button"
                onClick={() => {
                  if (typeof window !== 'undefined') {
                    window.print();
                  }
                }}
                className="flex-1 py-2.5 rounded-xl bg-primary text-on-primary font-semibold text-sm hover:opacity-90 transition-all cursor-pointer flex items-center justify-center gap-2 shadow-xs"
              >
                <span className="material-symbols-outlined text-[18px]">print</span>
                Imprimir Comprovante
              </button>
              <button
                type="button"
                onClick={() => setSelectedReceipt(null)}
                className="py-2.5 px-5 rounded-xl bg-surface-container text-on-surface font-semibold text-sm hover:bg-surface-container-high transition-colors cursor-pointer"
              >
                Fechar
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ========================================================================= */}
      {/* Modal 2: Comprovante Consolidado Periódico (@media print)                 */}
      {/* ========================================================================= */}
      {consolidatedModalOpen && (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-xs flex items-center justify-center p-4 animate-fadeIn overflow-y-auto">
          <div
            id="printable-consolidated-modal"
            className="bg-surface-container-lowest w-full max-w-2xl rounded-2xl p-6 sm:p-8 shadow-2xl border border-surface-container flex flex-col gap-5 my-auto"
          >
            {/* Header Relatório Consolidado */}
            <div className="flex items-start justify-between border-b border-surface-container pb-4">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-primary text-on-primary flex items-center justify-center font-bold text-lg">
                  NB
                </div>
                <div>
                  <h3 className="font-headline-md text-base sm:text-lg text-on-surface font-bold tracking-tight">
                    Nexus Bank S.A. - Banco 001
                  </h3>
                  <p className="text-xs text-on-surface-variant font-medium">
                    Relatório Consolidado de Extrato Bancário
                  </p>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setConsolidatedModalOpen(false)}
                className="p-1.5 rounded-lg hover:bg-surface-container text-on-surface-variant cursor-pointer no-print"
                aria-label="Fechar relatório consolidado"
              >
                <span className="material-symbols-outlined text-[20px]">close</span>
              </button>
            </div>

            {/* Cabeçalho do Titular e Período */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
              <div className="p-3 rounded-xl bg-surface-container-low border border-surface-container space-y-1">
                <span className="text-on-surface-variant font-medium block">Titular da Conta:</span>
                <span className="font-bold text-on-surface text-sm block">{account.holderName}</span>
                <span className="font-mono text-on-surface-variant block">
                  Agência: {account.agency} · Conta: {account.accountNumber}-{account.digit}
                </span>
                <span className="font-mono text-on-surface-variant block">CPF: {account.cpfMasked}</span>
              </div>

              <div className="p-3 rounded-xl bg-surface-container-low border border-surface-container space-y-1">
                <span className="text-on-surface-variant font-medium block">Período Avaliado:</span>
                <span className="font-bold text-on-surface text-sm block">Últimos 30 dias (Período Atual)</span>
                <span className="text-on-surface-variant block">Data de Emissão:</span>
                <span className="font-mono text-on-surface block">{formatDateTimeBr(new Date().toISOString()).full}</span>
              </div>
            </div>

            {/* Resumo Contábil do Período */}
            <div className="grid grid-cols-3 gap-2 sm:gap-3 text-center">
              <div className="p-3 rounded-xl bg-emerald-500/10 border border-emerald-500/20">
                <span className="text-[11px] font-semibold text-emerald-800 uppercase block">Total de Entradas</span>
                <span className="font-mono font-bold text-sm sm:text-base text-emerald-700 block mt-1">
                  +{formatCentsToBrl(totals.creditTotal)}
                </span>
                <span className="text-[10px] text-on-surface-variant">{counts.credit} operações</span>
              </div>

              <div className="p-3 rounded-xl bg-error-container/20 border border-error-container/40">
                <span className="text-[11px] font-semibold text-error uppercase block">Total de Saídas</span>
                <span className="font-mono font-bold text-sm sm:text-base text-error block mt-1">
                  -{formatCentsToBrl(totals.debitTotal)}
                </span>
                <span className="text-[10px] text-on-surface-variant">{counts.debit} operações</span>
              </div>

              <div className="p-3 rounded-xl bg-surface-container-low border border-surface-container">
                <span className="text-[11px] font-semibold text-on-surface-variant uppercase block">Saldo do Período</span>
                <span
                  className={`font-mono font-bold text-sm sm:text-base block mt-1 ${
                    totals.creditTotal >= totals.debitTotal ? 'text-emerald-700' : 'text-error'
                  }`}
                >
                  {totals.creditTotal >= totals.debitTotal ? '+' : '-'}
                  {formatCentsToBrl(Math.abs(totals.creditTotal - totals.debitTotal))}
                </span>
                <span className="text-[10px] text-on-surface-variant">Balanço líquido</span>
              </div>
            </div>

            {/* Tabela Sintética de Lançamentos */}
            <div className="border border-surface-container rounded-xl overflow-hidden max-h-56 overflow-y-auto">
              <table className="w-full text-left text-xs border-collapse">
                <thead className="bg-surface-container-low font-label-caps uppercase text-on-surface-variant border-b border-surface-container sticky top-0">
                  <tr>
                    <th className="p-2 font-semibold">Data</th>
                    <th className="p-2 font-semibold">Protocolo</th>
                    <th className="p-2 font-semibold">Operação</th>
                    <th className="p-2 font-semibold">Contraparte</th>
                    <th className="p-2 font-semibold text-right">Valor</th>
                    <th className="p-2 font-semibold text-right">Saldo</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-surface-container-low font-mono">
                  {filteredStatement.slice(0, 15).map((item) => (
                    <tr key={item.id} className="hover:bg-surface-container-low">
                      <td className="p-2 whitespace-nowrap">{formatDateTimeBr(item.timestamp).date}</td>
                      <td className="p-2 text-secondary whitespace-nowrap">{getCanonicalProtocolNumber(item)}</td>
                      <td className="p-2 font-sans">{getTransactionTypeLabel(item.type)}</td>
                      <td className="p-2 font-sans truncate max-w-[120px]">{item.counterpartyName}</td>
                      <td
                        className={`p-2 text-right whitespace-nowrap font-bold ${
                          item.nature === 'CREDIT' ? 'text-emerald-700' : 'text-error'
                        }`}
                      >
                        {item.nature === 'CREDIT' ? '+' : '-'}
                        {formatCentsToBrl(item.amountCents)}
                      </td>
                      <td className="p-2 text-right whitespace-nowrap text-on-surface">
                        {formatCentsToBrl(item.balanceAfterCents)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="p-3 rounded-xl bg-surface-container-low/70 border border-surface-container text-xs text-on-surface-variant space-y-1">
              <span className="font-semibold block text-on-surface">Certificação e Conciliação Patrimonial</span>
              <p className="text-[11px]">
                Este documento consolida o histórico de transações e a posição de liquidez da conta para fins de
                comprovação fiscal, contábil e auditoria nos termos do Banco Central do Brasil.
              </p>
            </div>

            {/* Ações do Modal Consolidado */}
            <div className="flex gap-2.5 pt-3 border-t border-surface-container no-print">
              <button
                type="button"
                onClick={() => {
                  if (typeof window !== 'undefined') {
                    window.print();
                  }
                }}
                className="flex-1 py-2.5 rounded-xl bg-primary text-on-primary font-semibold text-sm hover:opacity-90 transition-all cursor-pointer flex items-center justify-center gap-2 shadow-xs"
              >
                <span className="material-symbols-outlined text-[18px]">print</span>
                Imprimir Relatório Consolidado
              </button>
              <button
                type="button"
                onClick={() => setConsolidatedModalOpen(false)}
                className="py-2.5 px-5 rounded-xl bg-surface-container text-on-surface font-semibold text-sm hover:bg-surface-container-high transition-colors cursor-pointer"
              >
                Fechar
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ========================================================================= */}
      {/* Modal 3: Exportar Extrato (CSV / OFX / PDF)                              */}
      {/* ========================================================================= */}
      {exportModalOpen && (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-xs flex items-center justify-center p-4 animate-fadeIn no-print">
          <div className="bg-surface-container-lowest w-full max-w-md rounded-2xl p-6 shadow-2xl border border-surface-container flex flex-col gap-4">
            <div className="flex items-center justify-between border-b border-surface-container pb-3">
              <h3 className="font-headline-md text-headline-md text-on-surface font-bold">
                Exportar Extrato
              </h3>
              <button
                type="button"
                onClick={() => setExportModalOpen(false)}
                className="p-1 rounded-lg hover:bg-surface-container text-on-surface-variant cursor-pointer"
                aria-label="Fechar exportação"
              >
                <span className="material-symbols-outlined">close</span>
              </button>
            </div>

            <p className="text-body-sm text-on-surface-variant">
              Selecione o formato desejado para download do relatório de conciliação:
            </p>

            <div className="space-y-2">
              <label className="flex items-center gap-3 p-3 rounded-xl bg-surface-container-low cursor-pointer border border-transparent hover:border-secondary transition-all">
                <input
                  type="radio"
                  name="exportFmt"
                  value="csv"
                  checked={exportFormat === 'csv'}
                  onChange={() => setExportFormat('csv')}
                  className="accent-primary w-4 h-4 cursor-pointer"
                />
                <div>
                  <span className="font-semibold text-sm block text-on-surface">CSV Estruturado (Excel BR)</span>
                  <span className="text-xs text-on-surface-variant">Delimitador ponto e vírgula com BOM UTF-8</span>
                </div>
              </label>

              <label className="flex items-center gap-3 p-3 rounded-xl bg-surface-container-low cursor-pointer border border-transparent hover:border-secondary transition-all">
                <input
                  type="radio"
                  name="exportFmt"
                  value="ofx"
                  checked={exportFormat === 'ofx'}
                  onChange={() => setExportFormat('ofx')}
                  className="accent-primary w-4 h-4 cursor-pointer"
                />
                <div>
                  <span className="font-semibold text-sm block text-on-surface">OFX (Open Financial Exchange)</span>
                  <span className="text-xs text-on-surface-variant">Padrão contábil para sistemas ERP e contabilidade</span>
                </div>
              </label>

              <label className="flex items-center gap-3 p-3 rounded-xl bg-surface-container-low cursor-pointer border border-transparent hover:border-secondary transition-all">
                <input
                  type="radio"
                  name="exportFmt"
                  value="pdf"
                  checked={exportFormat === 'pdf'}
                  onChange={() => setExportFormat('pdf')}
                  className="accent-primary w-4 h-4 cursor-pointer"
                />
                <div>
                  <span className="font-semibold text-sm block text-on-surface">PDF / Impressão Consolidada</span>
                  <span className="text-xs text-on-surface-variant">Relatório executivo formatado para impressão A4</span>
                </div>
              </label>
            </div>

            <div className="flex gap-2 pt-2 border-t border-surface-container">
              <button
                type="button"
                onClick={handleExport}
                className="flex-1 py-2.5 rounded-xl bg-primary text-on-primary font-semibold text-sm hover:opacity-90 transition-all cursor-pointer"
              >
                Confirmar e Baixar
              </button>
              <button
                type="button"
                onClick={() => setExportModalOpen(false)}
                className="py-2.5 px-4 rounded-xl bg-surface-container text-on-surface font-semibold text-sm hover:bg-surface-container-high transition-colors cursor-pointer"
              >
                Cancelar
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
