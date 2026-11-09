"use client";

import React, { useState } from 'react';
import Link from 'next/link';
import { useBanking } from '@/hooks/useBanking';
import { formatCentsToBrl, parseBrlToCents } from '@/utils/currency';
import { generateUUID } from '@/services/mockBankingStore';
import { AccountLookupResponse, StatementItem } from '@/services/types';

export default function TransferirPage() {
  const { balance, transfer, lookup } = useBanking();

  const [agency, setAgency] = useState('0001');
  const [accountNumber, setAccountNumber] = useState('123456-7');
  const [amountStr, setAmountStr] = useState('2.450,00');
  const [description, setDescription] = useState('Pagamento de consultoria de software');
  const [idempotencyKey, setIdempotencyKey] = useState('c9b24e60-84a1-4321-9e22-094fa817b120');
  const [showTechDetails, setShowTechDetails] = useState(false);

  const [counterparty, setCounterparty] = useState<AccountLookupResponse | null>(() =>
    lookup('0001', '123456-7')
  );
  const [isLookingUp, setIsLookingUp] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [successItem, setSuccessItem] = useState<StatementItem | null>(null);

  const handleLookup = (ag: string, acc: string) => {
    setIsLookingUp(true);
    setErrorMessage('');
    setTimeout(() => {
      const res = lookup(ag, acc);
      setCounterparty(res);
      setIsLookingUp(false);
    }, 250);
  };

  const amountCents = parseBrlToCents(amountStr);
  const projectedBalanceCents = balance.availableCents - amountCents;
  const isOverBalance = amountCents > balance.availableCents;
  const availableLimitCents = Math.max(0, balance.dailyLimitCents - balance.usedTodayCents);
  const isOverLimit = amountCents > availableLimitCents;

  const handleAmountChip = (centsToAdd: number) => {
    const nextCents = amountCents + centsToAdd;
    setAmountStr((nextCents / 100).toFixed(2).replace('.', ','));
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage('');

    if (amountCents <= 0) {
      setErrorMessage('Informe um valor válido maior que zero.');
      return;
    }

    if (isOverBalance) {
      setErrorMessage('Saldo insuficiente para realizar esta transferência.');
      return;
    }

    if (isOverLimit) {
      setErrorMessage(`Limite diário excedido. Limite disponível: ${formatCentsToBrl(availableLimitCents)}.`);
      return;
    }

    if (!counterparty) {
      setErrorMessage('Verifique os dados da conta de destino antes de prosseguir.');
      return;
    }

    setIsSubmitting(true);

    setTimeout(() => {
      try {
        const item = transfer({
          destinationAgency: agency,
          destinationAccount: accountNumber,
          amountCents,
          description,
          idempotencyKey,
        });

        setSuccessItem(item);
        setIsSubmitting(false);
      } catch (err: unknown) {
        setIsSubmitting(false);
        setErrorMessage(err instanceof Error ? err.message : 'Falha ao processar transferência.');
      }
    }, 600);
  };

  const handleResetForm = () => {
    setSuccessItem(null);
    setIdempotencyKey(generateUUID());
    setAmountStr('0,00');
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
        <span className="text-on-surface font-semibold">Transferência entre Contas</span>
      </nav>

      {/* Header */}
      <div className="w-full bg-surface-container-lowest p-5 md:p-6 rounded-2xl shadow-sm border border-surface-container flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2.5">
            <h1 className="font-headline-lg text-headline-lg text-on-surface font-bold tracking-tight">
              Transferência entre Contas Nexus
            </h1>
            <span className="px-2.5 py-0.5 rounded-lg bg-emerald-500/10 text-emerald-800 border border-emerald-500/20 font-label-caps text-label-caps font-semibold flex items-center gap-1">
              <span className="material-symbols-outlined text-[14px]">bolt</span>
              Liquidação Imediata
            </span>
          </div>
          <p className="font-body-md text-body-md text-on-surface-variant mt-1">
            Transfira valores para outras contas com liquidação instantânea e total segurança.
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
        /* Estado de Sucesso com Comprovante Imediato */
        <div className="w-full bg-surface-container-lowest p-6 md:p-8 rounded-2xl shadow-md border border-emerald-500/30 flex flex-col gap-6 animate-fadeIn">
          <div className="flex items-center gap-3 text-emerald-800">
            <span className="material-symbols-outlined text-4xl text-emerald-600">check_circle</span>
            <div>
              <h2 className="text-xl md:text-2xl font-bold text-on-surface">
                Transferência Executada com Sucesso!
              </h2>
              <p className="text-body-sm text-emerald-700">
                Operação confirmada e finalizada sob o comprovante #{successItem.sequence}.
              </p>
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 p-5 rounded-xl bg-surface-container-low border border-surface-container font-body-sm">
            <div>
              <span className="text-on-surface-variant text-xs uppercase font-semibold block">Favorecido (Destino)</span>
              <span className="font-bold text-base text-on-surface block mt-0.5">{successItem.counterpartyName}</span>
              <span className="text-xs text-on-surface-variant block">{successItem.counterpartyAccount}</span>
            </div>
            <div>
              <span className="text-on-surface-variant text-xs uppercase font-semibold block">Valor Transferido</span>
              <span className="font-mono font-bold text-xl text-error block mt-0.5">
                -{formatCentsToBrl(successItem.amountCents)}
              </span>
              <span className="text-xs text-emerald-700 font-semibold block">Tarifa: R$ 0,00</span>
            </div>
            <div>
              <span className="text-on-surface-variant text-xs uppercase font-semibold block">Data / Hora</span>
              <span className="font-mono text-on-surface block mt-0.5">
                {new Date(successItem.timestamp).toLocaleString('pt-BR')} (Horário de Brasília)
              </span>
            </div>
            <div>
              <span className="text-on-surface-variant text-xs uppercase font-semibold block">Saldo Resultante em Conta</span>
              <span className="font-mono font-bold text-base text-on-surface block mt-0.5">
                {formatCentsToBrl(successItem.balanceAfterCents)}
              </span>
            </div>
            <div className="md:col-span-2 pt-2 border-t border-surface-container text-xs font-mono text-on-surface-variant/80 space-y-1">
              <div>Identificador da Transação: {successItem.transactionId}</div>
              <div>Protocolo de Segurança: {successItem.idempotencyKey}</div>
            </div>
          </div>

          <div className="flex items-center gap-3 flex-wrap">
            <button
              onClick={handleResetForm}
              className="px-5 py-2.5 rounded-xl bg-[#0e1c2f] text-white font-semibold text-sm hover:bg-[#1a2d47] transition-all cursor-pointer flex items-center gap-2"
              type="button"
            >
              <span className="material-symbols-outlined text-[18px]">add</span>
              Realizar Nova Transferência
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
        /* Formulário de Transferência */
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
          {/* Coluna Principal: Formulário (8 cols) */}
          <div className="lg:col-span-8 bg-surface-container-lowest p-6 md:p-8 rounded-2xl shadow-sm border border-surface-container flex flex-col gap-6">
            {errorMessage && (
              <div className="p-4 rounded-xl bg-error/10 border border-error/30 text-error text-body-md flex items-center gap-3 animate-fadeIn">
                <span className="material-symbols-outlined text-error shrink-0">error</span>
                <p className="text-sm font-medium">{errorMessage}</p>
              </div>
            )}

            <form onSubmit={handleSubmit} className="flex flex-col gap-5">
              {/* Field Group 1: Identificação da Conta Destino */}
              <div className="space-y-2">
                <span className="font-label-caps text-label-caps uppercase text-secondary font-bold tracking-wider block">
                  1. Conta de Destino (Nexus Bank)
                </span>
                <div className="grid grid-cols-1 sm:grid-cols-12 gap-3">
                  <div className="sm:col-span-4 flex flex-col gap-1">
                    <label className="font-label-caps text-label-caps uppercase text-on-surface-variant font-semibold" htmlFor="agencyInput">
                      Agência
                    </label>
                    <div className="relative flex items-center">
                      <input
                        id="agencyInput"
                        value={agency}
                        onChange={(e) => setAgency(e.target.value)}
                        maxLength={4}
                        placeholder="0001"
                        type="text"
                        className="w-full h-11 px-3 bg-surface-container-low text-on-surface font-code-md text-code-md rounded-xl border border-transparent focus:border-secondary focus:outline-none transition-colors"
                      />
                      <span className="absolute right-3 font-code-sm text-code-sm text-on-surface-variant">Nexus</span>
                    </div>
                  </div>

                  <div className="sm:col-span-8 flex flex-col gap-1">
                    <label className="font-label-caps text-label-caps uppercase text-on-surface-variant font-semibold" htmlFor="accountInput">
                      Conta Corrente com Dígito
                    </label>
                    <div className="flex gap-2">
                      <input
                        id="accountInput"
                        value={accountNumber}
                        onChange={(e) => setAccountNumber(e.target.value)}
                        placeholder="000000-0"
                        type="text"
                        className="w-full h-11 px-3 bg-surface-container-low text-on-surface font-code-md text-code-md rounded-xl border border-transparent focus:border-secondary focus:outline-none transition-colors"
                      />
                      <button
                        type="button"
                        onClick={() => handleLookup(agency, accountNumber)}
                        className="h-11 px-4 bg-surface-container-high hover:bg-secondary-container text-on-surface font-body-md text-body-md font-semibold rounded-xl flex items-center gap-1.5 transition-colors shadow-xs cursor-pointer shrink-0"
                      >
                        <span className="material-symbols-outlined text-[18px]">person_search</span>
                        <span>Verificar</span>
                      </button>
                    </div>
                  </div>
                </div>
              </div>

              {/* Feedback de Contraparte Mascarada (Privacidade Garantida) */}
              {isLookingUp ? (
                <div className="p-4 rounded-xl bg-surface-container-low flex items-center gap-3">
                  <span className="inline-block w-4 h-4 border-2 border-secondary border-t-transparent rounded-full animate-spin"></span>
                  <span className="text-body-sm text-on-surface-variant">Verificando dados da conta...</span>
                </div>
              ) : counterparty ? (
                <div className="bg-surface-container-low p-4 rounded-xl border border-surface-container transition-all">
                  <div className="flex items-start justify-between gap-3 flex-wrap">
                    <div className="flex items-start gap-3">
                      <div className="w-10 h-10 rounded-xl bg-emerald-500/10 text-emerald-800 flex items-center justify-center shrink-0 mt-0.5">
                        <span className="material-symbols-outlined text-[22px]">verified_user</span>
                      </div>
                      <div>
                        <span className="font-label-caps text-label-caps uppercase text-emerald-800 font-semibold block">
                          Titular Confirmado
                        </span>
                        <div className="font-headline-sm text-headline-sm text-on-surface font-semibold tracking-tight mt-0.5">
                          {counterparty.holderNameMasked}
                        </div>
                        <div className="font-body-sm text-body-sm text-on-surface-variant flex flex-wrap gap-x-2 mt-1">
                          <span>CPF: {counterparty.documentMasked}</span>
                          <span>·</span>
                          <span>{counterparty.bankName}</span>
                          <span>·</span>
                          <span className="text-emerald-700 font-medium">{counterparty.accountType}</span>
                        </div>
                      </div>
                    </div>
                    <span className="px-2.5 py-1 rounded-lg bg-emerald-500/15 text-emerald-900 border border-emerald-500/20 font-label-caps text-label-caps font-semibold">
                      CONTA VERIFICADA
                    </span>
                  </div>
                </div>
              ) : null}

              {/* Field Group 2: Valor e Descrição */}
              <div className="space-y-3 pt-2">
                <span className="font-label-caps text-label-caps uppercase text-secondary font-bold tracking-wider block">
                  2. Valor e Descrição
                </span>

                <div className="flex flex-col gap-1.5">
                  <div className="flex items-center justify-between">
                    <label className="font-label-caps text-label-caps uppercase text-on-surface-variant font-semibold" htmlFor="amountInput">
                      Valor da Transferência (BRL)
                    </label>
                    <span className="font-code-sm text-code-sm text-on-surface-variant">
                      Disponível hoje: {formatCentsToBrl(availableLimitCents)}
                    </span>
                  </div>
                  <div className="relative flex items-center">
                    <span className="absolute left-4 font-headline-md text-headline-md font-semibold text-on-surface-variant">
                      R$
                    </span>
                    <input
                      id="amountInput"
                      value={amountStr}
                      onChange={(e) => setAmountStr(e.target.value)}
                      placeholder="0,00"
                      type="text"
                      className="w-full h-14 pl-12 pr-4 bg-surface-container-low text-on-surface font-code-lg text-code-lg font-bold rounded-xl border border-transparent focus:border-secondary focus:outline-none transition-colors"
                    />
                  </div>
                </div>

                {/* Chips de Valor Rápido */}
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="text-xs text-on-surface-variant font-medium mr-1">Adicionar:</span>
                  <button
                    type="button"
                    onClick={() => handleAmountChip(10000)}
                    className="px-3 py-1 rounded-lg bg-surface-container hover:bg-surface-container-high text-xs font-semibold font-mono cursor-pointer"
                  >
                    + R$ 100
                  </button>
                  <button
                    type="button"
                    onClick={() => handleAmountChip(50000)}
                    className="px-3 py-1 rounded-lg bg-surface-container hover:bg-surface-container-high text-xs font-semibold font-mono cursor-pointer"
                  >
                    + R$ 500
                  </button>
                  <button
                    type="button"
                    onClick={() => handleAmountChip(100000)}
                    className="px-3 py-1 rounded-lg bg-surface-container hover:bg-surface-container-high text-xs font-semibold font-mono cursor-pointer"
                  >
                    + R$ 1.000
                  </button>
                  <button
                    type="button"
                    onClick={() => handleAmountChip(500000)}
                    className="px-3 py-1 rounded-lg bg-surface-container hover:bg-surface-container-high text-xs font-semibold font-mono cursor-pointer"
                  >
                    + R$ 5.000
                  </button>
                </div>

                <div className="flex flex-col gap-1.5 pt-1">
                  <label className="font-label-caps text-label-caps uppercase text-on-surface-variant font-semibold" htmlFor="descInput">
                    Identificação / Motivo da Transferência
                  </label>
                  <input
                    id="descInput"
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                    placeholder="Ex: Pagamento de fornecedor, honorários, etc."
                    type="text"
                    className="w-full h-11 px-3 bg-surface-container-low text-on-surface font-body-md text-body-md rounded-xl border border-transparent focus:border-secondary focus:outline-none transition-colors"
                  />
                </div>
              </div>

              {/* Drawer de Parâmetros e Segurança */}
              <div className="bg-surface-container-low rounded-xl p-3 border border-surface-container">
                <button
                  type="button"
                  onClick={() => setShowTechDetails(!showTechDetails)}
                  className="w-full flex items-center justify-between text-left text-on-surface hover:text-primary transition-colors cursor-pointer"
                >
                  <div className="flex items-center gap-2">
                    <span className="material-symbols-outlined text-[18px] text-secondary">shield</span>
                    <span className="font-label-caps text-label-caps uppercase font-semibold">
                      Parâmetros e Protocolo de Segurança
                    </span>
                  </div>
                  <span className={`material-symbols-outlined text-[20px] transition-transform ${showTechDetails ? 'rotate-180' : ''}`}>
                    expand_more
                  </span>
                </button>

                {showTechDetails && (
                  <div className="mt-3 pt-3 border-t border-surface-container flex flex-col gap-2 font-body-sm text-body-sm text-on-surface-variant animate-fadeIn">
                    <div className="flex items-center justify-between flex-wrap gap-1">
                      <span className="font-semibold text-on-surface">Protocolo da Operação:</span>
                      <div className="flex items-center gap-2">
                        <span className="font-code-sm text-code-sm text-on-surface select-all">{idempotencyKey}</span>
                        <button
                          type="button"
                          onClick={() => setIdempotencyKey(generateUUID())}
                          title="Gerar novo protocolo"
                          className="text-secondary hover:text-on-surface cursor-pointer"
                        >
                          <span className="material-symbols-outlined text-[16px]">refresh</span>
                        </button>
                      </div>
                    </div>
                    <div className="flex items-center justify-between">
                      <span className="font-semibold text-on-surface">Tipo de Liquidação:</span>
                      <span className="text-emerald-700 font-medium">Instantânea com Garantia Bancária</span>
                    </div>
                    <div className="flex items-center justify-between">
                      <span className="font-semibold text-on-surface">Canal Protegido:</span>
                      <span>Conexão Criptografada Segura</span>
                    </div>
                  </div>
                )}
              </div>

              {/* Botão de Envio */}
              <button
                type="submit"
                disabled={isSubmitting || isOverBalance || isOverLimit || amountCents <= 0}
                className="w-full h-14 bg-[#0e1c2f] hover:bg-[#1a2d47] text-white font-headline-sm text-headline-sm font-semibold rounded-2xl flex items-center justify-center gap-2 transition-all shadow-md active:scale-[0.99] cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {isSubmitting ? (
                  <>
                    <span className="inline-block w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin"></span>
                    <span>Processando Transferência Segura...</span>
                  </>
                ) : (
                  <>
                    <span className="material-symbols-outlined text-[22px]">send_money</span>
                    <span>Confirmar e Executar Transferência</span>
                  </>
                )}
              </button>
            </form>
          </div>

          {/* Coluna Lateral: Resumo e Regras (4 cols) */}
          <div className="lg:col-span-4 flex flex-col gap-6 w-full">
            {/* Card Resumo do Impacto */}
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
                  <span className="text-on-surface-variant">Valor da Transferência:</span>
                  <span className="font-code-md text-code-md font-bold text-error">
                    -{formatCentsToBrl(amountCents)}
                  </span>
                </div>

                <div className="flex justify-between items-center p-3 rounded-xl bg-surface-container-low">
                  <span className="text-on-surface-variant">Tarifa Bancária:</span>
                  <span className="font-code-md text-code-md font-bold text-emerald-700">
                    R$ 0,00 (Gratuita)
                  </span>
                </div>

                <div className="flex justify-between items-center p-3.5 rounded-xl bg-surface-container border border-surface-container-high">
                  <span className="font-semibold text-on-surface">Saldo Projetado:</span>
                  <span
                    className={`font-code-lg text-code-lg font-bold ${
                      isOverBalance ? 'text-error' : 'text-on-surface'
                    }`}
                  >
                    {formatCentsToBrl(projectedBalanceCents)}
                  </span>
                </div>

                {isOverBalance && (
                  <p className="text-xs text-error font-semibold flex items-center gap-1">
                    <span className="material-symbols-outlined text-[14px]">warning</span>
                    O valor excede o saldo disponível em conta.
                  </p>
                )}
              </div>
            </div>

            {/* Card Explicativo Transferência Segura */}
            <div className="bg-surface-container-lowest p-6 rounded-2xl shadow-sm border border-surface-container flex flex-col gap-3">
              <div className="flex items-center gap-2">
                <span className="material-symbols-outlined text-secondary text-[22px]">shield</span>
                <h4 className="font-headline-sm text-headline-sm text-on-surface font-bold">
                  Transferência Segura
                </h4>
              </div>
              <p className="font-body-sm text-body-sm text-on-surface-variant leading-relaxed">
                Todas as transferências contam com confirmação prévia dos dados do titular e liquidação em tempo real. Os valores saem da sua conta e entram diretamente na conta do favorecido de forma instantânea e comprovada.
              </p>
              <div className="p-3 bg-emerald-500/10 rounded-xl border border-emerald-500/20 text-xs text-emerald-800 flex items-center gap-2">
                <span className="material-symbols-outlined text-[18px]">verified</span>
                <span>Conformidade e segurança autorizadas pelo Banco Central.</span>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
