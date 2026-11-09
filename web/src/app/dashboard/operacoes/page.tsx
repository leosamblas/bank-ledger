"use client";

import React, { useState, Suspense } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { useBanking } from '@/hooks/useBanking';
import { formatCentsToBrl, parseBrlToCents } from '@/utils/currency';
import { generateUUID } from '@/services/mockBankingStore';
import { StatementItem } from '@/services/types';

function OperacoesContent() {
  const searchParams = useSearchParams();
  const initialTab = searchParams.get('tab') === 'withdraw' ? 'withdraw' : 'deposit';

  const { balance, deposit, withdraw } = useBanking();

  const [activeTab, setActiveTab] = useState<'deposit' | 'withdraw'>(initialTab);
  const [amountStr, setAmountStr] = useState('500,00');
  const [idempotencyKey, setIdempotencyKey] = useState('7f8c41ea-9a82-416b-a25b-de095cc1a89f');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [successItem, setSuccessItem] = useState<StatementItem | null>(null);

  const amountCents = parseBrlToCents(amountStr);
  const isDeposit = activeTab === 'deposit';

  const projectedBalanceCents = isDeposit
    ? balance.availableCents + amountCents
    : balance.availableCents - amountCents;

  const isOverBalance = !isDeposit && amountCents > balance.availableCents;

  const handleAmountChip = (centsToAdd: number) => {
    const nextCents = amountCents + centsToAdd;
    setAmountStr((nextCents / 100).toFixed(2).replace('.', ','));
  };

  const handleMaxWithdraw = () => {
    setAmountStr((balance.availableCents / 100).toFixed(2).replace('.', ','));
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage('');

    if (amountCents <= 0) {
      setErrorMessage('Informe um valor maior que zero.');
      return;
    }

    if (!isDeposit && isOverBalance) {
      setErrorMessage('Saldo insuficiente para efetuar este saque.');
      return;
    }

    if (amountCents > 5000000) {
      setErrorMessage('Valor máximo por operação de caixa é de R$ 50.000,00.');
      return;
    }

    setIsSubmitting(true);

    setTimeout(() => {
      try {
        let item: StatementItem;
        if (isDeposit) {
          item = deposit({
            amountCents,
            idempotencyKey,
            terminalId: 'Terminal Autoatendimento 04 (Agência Centro)',
          });
        } else {
          item = withdraw({
            amountCents,
            idempotencyKey,
            terminalId: 'Caixa Eletrônico 24h Nexus (SYS-CASH-11)',
          });
        }

        setSuccessItem(item);
        setIsSubmitting(false);
      } catch (err: unknown) {
        setIsSubmitting(false);
        setErrorMessage(err instanceof Error ? err.message : 'Falha na operação.');
      }
    }, 550);
  };

  const handleReset = () => {
    setSuccessItem(null);
    setIdempotencyKey(generateUUID());
    setAmountStr('100,00');
    setErrorMessage('');
  };

  return (
    <div className="w-full px-4 sm:px-6 lg:px-8 max-w-[1600px] mx-auto flex flex-col gap-6">
      {/* Breadcrumb Trail */}
      <nav aria-label="Navegação estrutural" className="flex items-center gap-2 text-body-sm text-on-surface-variant">
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
        <span className="text-on-surface font-semibold">Depositar & Sacar</span>
      </nav>

      {/* Header */}
      <div className="w-full bg-surface-container-lowest p-5 md:p-6 rounded-2xl shadow-sm border border-surface-container flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2.5">
            <h1 className="font-headline-lg text-headline-lg text-on-surface font-bold tracking-tight">
              Gestão de Fundos: Depositar & Sacar
            </h1>
            <span className="px-2.5 py-0.5 rounded-lg bg-emerald-500/10 text-emerald-800 border border-emerald-500/20 font-label-caps text-label-caps font-semibold">
              COMPENSAÇÃO IMEDIATA
            </span>
          </div>
          <p className="font-body-md text-body-md text-on-surface-variant mt-1">
            Realize depósitos e saques em sua conta com confirmação e saldo atualizado na hora.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <Link
            href="/dashboard/extrato"
            className="px-4 py-2 rounded-xl bg-surface-container-high text-on-surface hover:bg-surface-variant font-headline-sm text-body-md font-semibold transition-colors flex items-center gap-1.5 shadow-xs no-underline"
          >
            <span className="material-symbols-outlined text-[18px]">receipt_long</span>
            Ver Extrato
          </Link>
        </div>
      </div>

      {successItem ? (
        /* Estado de Sucesso */
        <div className="w-full bg-surface-container-lowest p-6 md:p-8 rounded-2xl shadow-md border border-emerald-500/30 flex flex-col gap-6 animate-fadeIn">
          <div className="flex items-center gap-3 text-emerald-800">
            <span className="material-symbols-outlined text-4xl text-emerald-600">check_circle</span>
            <div>
              <h2 className="text-xl md:text-2xl font-bold text-on-surface">
                {isDeposit ? 'Depósito Confirmado com Sucesso!' : 'Saque Efetuado com Sucesso!'}
              </h2>
              <p className="text-body-sm text-emerald-700">
                Operação realizada com sucesso sob o comprovante #{successItem.sequence}.
              </p>
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 p-5 rounded-xl bg-surface-container-low border border-surface-container font-body-sm">
            <div>
              <span className="text-on-surface-variant text-xs uppercase font-semibold block">Terminal de Atendimento</span>
              <span className="font-bold text-base text-on-surface block mt-0.5">{successItem.counterpartyName}</span>
              <span className="text-xs text-on-surface-variant block">{successItem.counterpartyAccount}</span>
            </div>
            <div>
              <span className="text-on-surface-variant text-xs uppercase font-semibold block">Valor da Operação</span>
              <span
                className={`font-mono font-bold text-xl block mt-0.5 ${
                  isDeposit ? 'text-emerald-700' : 'text-error'
                }`}
              >
                {isDeposit ? '+' : '-'}{formatCentsToBrl(successItem.amountCents)}
              </span>
              <span className="text-xs text-emerald-700 font-semibold block">Compensação Imediata</span>
            </div>
            <div>
              <span className="text-on-surface-variant text-xs uppercase font-semibold block">Data / Hora</span>
              <span className="font-mono text-on-surface block mt-0.5">
                {new Date(successItem.timestamp).toLocaleString('pt-BR')} (Horário de Brasília)
              </span>
            </div>
            <div>
              <span className="text-on-surface-variant text-xs uppercase font-semibold block">Novo Saldo em Conta</span>
              <span className="font-mono font-bold text-base text-on-surface block mt-0.5">
                {formatCentsToBrl(successItem.balanceAfterCents)}
              </span>
            </div>
            <div className="md:col-span-2 pt-2 border-t border-surface-container text-xs font-mono text-on-surface-variant/80 space-y-1">
              <div>Nº da Transação: {successItem.transactionId}</div>
              <div>Protocolo de Segurança: {successItem.idempotencyKey}</div>
            </div>
          </div>

          <div className="flex items-center gap-3 flex-wrap">
            <button
              onClick={handleReset}
              className="px-5 py-2.5 rounded-xl bg-[#0e1c2f] text-white font-semibold text-sm hover:bg-[#1a2d47] transition-all cursor-pointer flex items-center gap-2"
              type="button"
            >
              <span className="material-symbols-outlined text-[18px]">payments</span>
              Nova Operação
            </button>
            <Link
              href="/dashboard"
              className="px-4 py-2.5 rounded-xl bg-surface-container text-on-surface font-semibold text-sm hover:bg-surface-container-high transition-colors no-underline"
            >
              Voltar à Visão Geral
            </Link>
            <Link
              href="/dashboard/extrato"
              className="px-4 py-2.5 rounded-xl bg-surface-container text-on-surface font-semibold text-sm hover:bg-surface-container-high transition-colors no-underline ml-auto"
            >
              Ver no Extrato
            </Link>
          </div>
        </div>
      ) : (
        /* Formulário de Depósito / Saque */
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
          {/* Coluna Principal (8 cols) */}
          <div className="lg:col-span-8 bg-surface-container-lowest p-6 md:p-8 rounded-2xl shadow-sm border border-surface-container flex flex-col gap-6">
            {/* Seletor de Abas */}
            <div className="flex p-1 rounded-xl bg-surface-container w-full sm:w-80">
              <button
                type="button"
                onClick={() => {
                  setActiveTab('deposit');
                  setErrorMessage('');
                }}
                className={`flex-1 py-2 rounded-lg font-headline-sm text-sm font-semibold transition-all cursor-pointer flex items-center justify-center gap-1.5 ${
                  activeTab === 'deposit'
                    ? 'bg-surface-container-lowest text-on-surface shadow-xs'
                    : 'text-on-surface-variant hover:text-on-surface'
                }`}
              >
                <span className="material-symbols-outlined text-[18px] text-emerald-600">arrow_downward</span>
                Depositar (+)
              </button>
              <button
                type="button"
                onClick={() => {
                  setActiveTab('withdraw');
                  setErrorMessage('');
                }}
                className={`flex-1 py-2 rounded-lg font-headline-sm text-sm font-semibold transition-all cursor-pointer flex items-center justify-center gap-1.5 ${
                  activeTab === 'withdraw'
                    ? 'bg-surface-container-lowest text-on-surface shadow-xs'
                    : 'text-on-surface-variant hover:text-on-surface'
                }`}
              >
                <span className="material-symbols-outlined text-[18px] text-secondary">atm</span>
                Sacar (-)
              </button>
            </div>

            {errorMessage && (
              <div className="p-4 rounded-xl bg-error/10 border border-error/30 text-error text-body-md flex items-center gap-3 animate-fadeIn">
                <span className="material-symbols-outlined text-error shrink-0">error</span>
                <p className="text-sm font-medium">{errorMessage}</p>
              </div>
            )}

            <form onSubmit={handleSubmit} className="flex flex-col gap-5">
              {/* Input de Valor */}
              <div className="flex flex-col gap-1.5">
                <div className="flex items-center justify-between">
                  <label className="font-label-caps text-label-caps uppercase text-on-surface-variant font-semibold" htmlFor="operationAmount">
                    {isDeposit ? 'Valor a Depositar (BRL)' : 'Valor a Sacar (BRL)'}
                  </label>
                  <span className="font-code-sm text-code-sm text-on-surface-variant">
                    Saldo disponível: {formatCentsToBrl(balance.availableCents)}
                  </span>
                </div>
                <div className="relative flex items-center">
                  <span className="absolute left-4 font-headline-md text-headline-md font-semibold text-on-surface-variant">
                    R$
                  </span>
                  <input
                    id="operationAmount"
                    value={amountStr}
                    onChange={(e) => setAmountStr(e.target.value)}
                    placeholder="0,00"
                    type="text"
                    className="w-full h-14 pl-12 pr-4 bg-surface-container-low text-on-surface font-code-lg text-code-lg font-bold rounded-xl border border-transparent focus:border-secondary focus:outline-none transition-colors"
                  />
                </div>
              </div>

              {/* Chips Rápidos */}
              <div className="flex items-center gap-2 flex-wrap">
                <span className="text-xs text-on-surface-variant font-medium mr-1">Valores Rápidos:</span>
                <button
                  type="button"
                  onClick={() => handleAmountChip(10000)}
                  className="px-3 py-1.5 rounded-lg bg-surface-container hover:bg-surface-container-high text-xs font-semibold font-mono cursor-pointer"
                >
                  + R$ 100
                </button>
                <button
                  type="button"
                  onClick={() => handleAmountChip(50000)}
                  className="px-3 py-1.5 rounded-lg bg-surface-container hover:bg-surface-container-high text-xs font-semibold font-mono cursor-pointer"
                >
                  + R$ 500
                </button>
                <button
                  type="button"
                  onClick={() => handleAmountChip(100000)}
                  className="px-3 py-1.5 rounded-lg bg-surface-container hover:bg-surface-container-high text-xs font-semibold font-mono cursor-pointer"
                >
                  + R$ 1.000
                </button>
                <button
                  type="button"
                  onClick={() => handleAmountChip(500000)}
                  className="px-3 py-1.5 rounded-lg bg-surface-container hover:bg-surface-container-high text-xs font-semibold font-mono cursor-pointer"
                >
                  + R$ 5.000
                </button>
                {!isDeposit && (
                  <button
                    type="button"
                    onClick={handleMaxWithdraw}
                    className="px-3 py-1.5 rounded-lg bg-secondary-container text-on-secondary-container hover:bg-surface-variant text-xs font-semibold font-mono cursor-pointer ml-auto"
                  >
                    Máximo (Saldo Total)
                  </button>
                )}
              </div>

              {/* Strip de Limites Operacionais */}
              <div className="p-3 rounded-xl bg-surface-container-low flex flex-col sm:flex-row items-start sm:items-center justify-between gap-1 text-on-surface-variant font-body-sm text-xs">
                <div className="flex items-center gap-1.5">
                  <span className="material-symbols-outlined text-[16px] text-secondary">verified_user</span>
                  <span>Limites de Operação Bacen</span>
                </div>
                <div className="font-code-sm text-xs font-medium text-on-surface">
                  Mínimo: <strong>R$ 0,01</strong> · Máximo por operação: <strong>R$ 50.000,00</strong>
                </div>
              </div>

              {/* Protocolo de Segurança da Operação */}
              <div className="p-3 rounded-xl bg-surface-container-low flex items-center justify-between gap-2">
                <div className="flex items-center gap-2 min-w-0">
                  <span className="material-symbols-outlined text-[18px] text-secondary">shield</span>
                  <div className="flex flex-col min-w-0">
                    <span className="font-label-caps text-label-caps uppercase text-on-surface-variant font-semibold">
                      Protocolo de Segurança da Operação
                    </span>
                    <span className="font-code-sm text-xs text-on-surface font-medium truncate">
                      {idempotencyKey}
                    </span>
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => setIdempotencyKey(generateUUID())}
                  title="Gerar novo código de validação"
                  className="p-1.5 rounded-lg text-secondary hover:text-on-surface hover:bg-surface-container transition-colors cursor-pointer"
                >
                  <span className="material-symbols-outlined text-[18px]">refresh</span>
                </button>
              </div>

              {/* CTA Submissão */}
              <button
                type="submit"
                disabled={isSubmitting || (!isDeposit && isOverBalance) || amountCents <= 0}
                className="w-full h-14 bg-[#0e1c2f] hover:bg-[#1a2d47] text-white font-headline-sm text-headline-sm font-semibold rounded-2xl flex items-center justify-center gap-2 transition-all shadow-md active:scale-[0.99] cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {isSubmitting ? (
                  <>
                    <span className="inline-block w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin"></span>
                    <span>Confirmando Operação...</span>
                  </>
                ) : (
                  <>
                    <span className="material-symbols-outlined text-[22px]">
                      {isDeposit ? 'add_circle' : 'atm'}
                    </span>
                    <span>{isDeposit ? 'Confirmar Depósito em Conta' : 'Confirmar Saque de Fundos'}</span>
                  </>
                )}
              </button>
            </form>
          </div>

          {/* Coluna Lateral: Impacto e Regras de Atendimento (4 cols) */}
          <div className="lg:col-span-4 flex flex-col gap-6 w-full">
            {/* Impacto no Saldo */}
            <div className="bg-surface-container-lowest p-6 rounded-2xl shadow-sm border border-surface-container flex flex-col gap-4">
              <h3 className="font-headline-sm text-headline-sm text-on-surface font-bold">
                Impacto no Saldo
              </h3>

              <div className="space-y-3 font-body-sm">
                <div className="flex justify-between items-center p-3 rounded-xl bg-surface-container-low">
                  <span className="text-on-surface-variant">Saldo Atual:</span>
                  <span className="font-code-md text-code-md font-semibold text-on-surface">
                    {formatCentsToBrl(balance.availableCents)}
                  </span>
                </div>

                <div className="flex justify-between items-center p-3 rounded-xl bg-surface-container-low">
                  <span className="text-on-surface-variant">
                    {isDeposit ? 'Valor a Creditar:' : 'Valor a Debitar:'}
                  </span>
                  <span
                    className={`font-code-md text-code-md font-bold ${
                      isDeposit ? 'text-emerald-700' : 'text-error'
                    }`}
                  >
                    {isDeposit ? '+' : '-'}{formatCentsToBrl(amountCents)}
                  </span>
                </div>

                <div className="flex justify-between items-center p-3.5 rounded-xl bg-surface-container border border-surface-container-high">
                  <span className="font-semibold text-on-surface">Saldo Resultante:</span>
                  <span
                    className={`font-code-lg text-code-lg font-bold ${
                      !isDeposit && isOverBalance ? 'text-error' : 'text-on-surface'
                    }`}
                  >
                    {formatCentsToBrl(projectedBalanceCents)}
                  </span>
                </div>

                {!isDeposit && isOverBalance && (
                  <p className="text-xs text-error font-semibold flex items-center gap-1">
                    <span className="material-symbols-outlined text-[14px]">warning</span>
                    O valor excede o saldo disponível em conta.
                  </p>
                )}
              </div>
            </div>

            {/* Rede de Atendimento Seguro */}
            <div className="bg-surface-container-lowest p-6 rounded-2xl shadow-sm border border-surface-container flex flex-col gap-3">
              <div className="flex items-center gap-2">
                <span className="material-symbols-outlined text-secondary text-[22px]">storefront</span>
                <h4 className="font-headline-sm text-headline-sm text-on-surface font-bold">
                  Rede de Atendimento Seguro
                </h4>
              </div>
              <p className="font-body-sm text-body-sm text-on-surface-variant leading-relaxed">
                Todas as operações realizadas em terminais de autoatendimento e caixas da rede Nexus contam com disponibilidade 24 horas e garantia de liquidez imediata com proteção pelo Fundo Garantidor de Créditos (FGC).
              </p>
              <div className="p-3 bg-surface-container-low rounded-xl border border-surface-container text-xs font-medium text-on-surface-variant flex items-center justify-between">
                <span>Terminal Autorizado:</span>
                <span className="font-bold text-secondary">
                  {isDeposit ? 'Autoatendimento Centro 04' : 'Caixa Eletrônico 24h'}
                </span>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default function OperacoesPage() {
  return (
    <Suspense
      fallback={
        <div className="w-full max-w-[1600px] mx-auto p-8 flex items-center justify-center">
          <div className="flex items-center gap-3 text-secondary font-medium">
            <span className="inline-block w-5 h-5 border-2 border-secondary border-t-transparent rounded-full animate-spin"></span>
            <span>Carregando ambiente seguro de depósitos e saques...</span>
          </div>
        </div>
      }
    >
      <OperacoesContent />
    </Suspense>
  );
}
