"use client";

import React, { useState } from 'react';
import Link from 'next/link';
import { useBanking } from '@/hooks/useBanking';
import { formatCentsToBrl } from '@/utils/currency';
import { formatRelativeTime } from '@/utils/date';

export default function DashboardPage() {
  const { account, balance, statement, resetData } = useBanking();
  const [showCardDetails, setShowCardDetails] = useState(false);
  const [cardBlocked, setCardBlocked] = useState(false);
  const [adjustLimitOpen, setAdjustLimitOpen] = useState(false);
  const [limitNotice, setLimitNotice] = useState('');

  // 4 transações mais recentes
  const recentTransactions = statement.slice(0, 4);

  // Cálculos de limite
  const availableLimitCents = Math.max(0, balance.dailyLimitCents - balance.usedTodayCents);
  const limitUsedPercent = Math.min(
    100,
    Math.round((balance.usedTodayCents / balance.dailyLimitCents) * 100)
  );

  return (
    <div className="w-full px-4 sm:px-6 lg:px-8 max-w-[1600px] mx-auto flex flex-col gap-6">
      {/* 1. Linha de Boas-Vindas e Status Geral */}
      <div className="w-full bg-surface-container-lowest p-5 md:p-6 rounded-2xl shadow-sm border border-surface-container flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
        <div className="flex flex-col gap-1.5">
          <div className="flex items-center gap-2 flex-wrap">
            <h1 className="font-headline-lg text-headline-lg text-on-surface font-bold tracking-tight">
              Olá, {account.holderName}
            </h1>
            <span className="px-2.5 py-0.5 rounded-lg bg-emerald-500/10 text-emerald-800 border border-emerald-500/20 font-label-caps text-label-caps tracking-wider flex items-center gap-1.5 font-semibold">
              <span className="w-2 h-2 rounded-full bg-emerald-500 inline-block animate-pulse"></span>
              CONTA ATIVA
            </span>
            <span className="px-2.5 py-0.5 rounded-lg bg-surface-container text-secondary font-label-caps text-label-caps font-semibold">
              Conta Corrente
            </span>
          </div>
          <div className="flex items-center gap-3 flex-wrap font-code-sm text-code-sm text-on-surface-variant">
            <span>
              Agência: <strong className="text-on-surface font-semibold">{account.agency}</strong>
            </span>
            <span className="text-outline-variant">•</span>
            <span>
              Conta: <strong className="text-on-surface font-semibold">{account.accountNumber}-{account.digit}</strong>
            </span>
            <span className="text-outline-variant">•</span>
            <span>
              Titular: <strong className="text-on-surface font-semibold">{account.companyName}</strong>
            </span>
          </div>
        </div>

        {/* Cartão de Status Seguro */}
        <div className="flex items-center gap-3 bg-surface-container-low px-4 py-2.5 rounded-xl border border-surface-container shadow-xs w-full md:w-auto justify-between md:justify-start">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-surface-container-high flex items-center justify-center text-emerald-700">
              <span className="material-symbols-outlined text-[20px]">verified_user</span>
            </div>
            <div className="flex flex-col">
              <div className="flex items-center gap-1.5 text-[13px] font-semibold text-on-surface">
                <span>Conexão Protegida de ponta a ponta</span>
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 inline-block"></span>
              </div>
              <span className="font-body-sm text-[12px] text-on-surface-variant">
                Conexão Segura Certificada
              </span>
            </div>
          </div>
          <button
            onClick={() => {
              resetData();
              setLimitNotice('Dados restaurados ao estado padrão.');
              setTimeout(() => setLimitNotice(''), 3000);
            }}
            className="p-1.5 hover:bg-surface-container rounded-lg text-secondary transition-transform active:rotate-180 cursor-pointer"
            title="Restaurar dados iniciais de demonstração"
            type="button"
          >
            <span className="material-symbols-outlined text-[18px]">refresh</span>
          </button>
        </div>
      </div>

      {limitNotice && (
        <div className="p-3 bg-emerald-500/10 border border-emerald-500/30 rounded-xl text-emerald-800 text-body-sm flex items-center gap-2 animate-fadeIn">
          <span className="material-symbols-outlined text-[18px]">check_circle</span>
          <span>{limitNotice}</span>
        </div>
      )}

      {/* 2. Grid Estruturado: 65% Principal (Left) / 35% Lateral (Right) */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        {/* COLUNA PRINCIPAL (65% / 8 Cols Desktop) */}
        <div className="lg:col-span-8 flex flex-col gap-6 w-full">
          {/* Card de Saldo Principal Amplo, Legível e Elegante */}
          <div className="w-full bg-surface-container-lowest rounded-2xl shadow-sm border border-surface-container p-6 md:p-8 flex flex-col gap-6 relative overflow-hidden">
            {/* Marca d'água sutil institucional */}
            <div className="absolute -right-6 -bottom-6 opacity-[0.03] pointer-events-none select-none text-primary">
              <span className="material-symbols-outlined text-[220px]">account_balance</span>
            </div>

            {/* Header do Card de Saldo */}
            <div className="flex flex-col gap-2 z-10">
              <div className="flex items-center justify-between flex-wrap gap-2">
                <span className="font-label-caps text-label-caps text-on-surface-variant uppercase font-semibold tracking-wider">
                  Saldo Disponível em Conta
                </span>
                <span className="font-body-sm text-[12px] px-2.5 py-1 rounded-md bg-surface-container-low text-on-surface-variant flex items-center gap-1 border border-surface-container">
                  <span className="material-symbols-outlined text-[15px] text-emerald-600">check_circle</span>
                  Saldo em tempo real
                </span>
              </div>
              <div className="flex items-baseline gap-3 flex-wrap">
                <span className="font-display-hero text-display-hero text-on-surface font-bold tracking-tight">
                  {formatCentsToBrl(balance.availableCents)}
                </span>
                <span className="font-code-sm text-[13px] text-emerald-800 bg-emerald-500/10 border border-emerald-500/20 px-2.5 py-1 rounded-md font-semibold flex items-center gap-1">
                  <span className="material-symbols-outlined text-[16px]">trending_up</span> +{balance.monthlyVariationPercent}% no mês
                </span>
              </div>
            </div>

            {/* Sub-métricas Horizontais em Grid Limpo */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-1 z-10">
              {/* Sub-métrica 1: Saldo Bloqueado */}
              <div className="bg-surface-container-low p-4 rounded-xl border border-surface-container flex flex-col justify-between gap-1">
                <span className="font-label-caps text-[11px] text-on-surface-variant uppercase font-semibold">
                  Saldo Bloqueado (Garantia/Judicial)
                </span>
                <span className="font-code-lg text-code-lg text-on-surface font-bold">
                  {formatCentsToBrl(balance.blockedCents)}
                </span>
                <span className="font-body-sm text-[12px] text-on-surface-variant">
                  0 centavos sob custódia
                </span>
              </div>

              {/* Sub-métrica 2: Limite Diário de Transferências */}
              <div className="bg-surface-container-low p-4 rounded-xl border border-surface-container flex flex-col justify-between gap-2">
                <div className="flex items-center justify-between">
                  <span className="font-label-caps text-[11px] text-on-surface-variant uppercase font-semibold">
                    Limite Diário de Transferências
                  </span>
                  <span className="font-code-sm text-[11px] text-emerald-700 font-semibold">
                    {100 - limitUsedPercent}% disp.
                  </span>
                </div>
                <div className="flex items-baseline justify-between font-code-sm text-code-sm">
                  <span className="font-semibold text-on-surface text-[15px]">
                    {formatCentsToBrl(availableLimitCents)}
                  </span>
                  <span className="text-on-surface-variant text-[12px]">
                    de {formatCentsToBrl(balance.dailyLimitCents)}
                  </span>
                </div>
                <div className="w-full bg-surface-container h-2 rounded-full overflow-hidden">
                  <div
                    className="bg-secondary h-full rounded-full transition-all duration-500"
                    style={{ width: `${100 - limitUsedPercent}%` }}
                  ></div>
                </div>
              </div>
            </div>

            {/* Botões de Ação Rápida em Destaque */}
            <div className="flex items-center gap-3 flex-wrap pt-2 z-10 border-t border-surface-container">
              <Link
                href="/dashboard/transferir"
                className="px-5 py-2.5 rounded-xl bg-[#0e1c2f] text-white font-headline-sm text-[14px] flex items-center gap-2 hover:bg-[#1a2d47] active:scale-[0.99] transition-all shadow-sm font-semibold no-underline"
              >
                <span className="material-symbols-outlined text-[18px] text-emerald-400">arrow_outward</span>
                Nova Transferência
              </Link>
              <Link
                href="/dashboard/operacoes?tab=deposit"
                className="px-4 py-2.5 rounded-xl bg-surface-container-high text-on-surface font-headline-sm text-[14px] flex items-center gap-2 hover:bg-surface-variant transition-colors shadow-xs font-semibold no-underline"
              >
                <span className="material-symbols-outlined text-[18px] text-emerald-600">arrow_downward</span>
                Depositar Fundos
              </Link>
              <Link
                href="/dashboard/operacoes?tab=withdraw"
                className="px-4 py-2.5 rounded-xl bg-surface-container-high text-on-surface font-headline-sm text-[14px] flex items-center gap-2 hover:bg-surface-variant transition-colors shadow-xs font-semibold no-underline"
              >
                <span className="material-symbols-outlined text-[18px] text-secondary">atm</span>
                Efetuar Saque
              </Link>
              <Link
                href="/dashboard/extrato"
                className="px-4 py-2.5 rounded-xl bg-surface-container-low text-on-surface-variant font-headline-sm text-[14px] flex items-center gap-2 hover:text-on-surface hover:bg-surface-container transition-colors ml-auto font-medium no-underline"
              >
                <span className="material-symbols-outlined text-[18px]">receipt_long</span>
                Ver Extrato Completo
              </Link>
            </div>
          </div>

          {/* Seção Últimas Movimentações */}
          <div className="w-full bg-surface-container-lowest rounded-2xl shadow-sm border border-surface-container p-6 md:p-8 flex flex-col gap-6">
            <div className="flex items-center justify-between flex-wrap gap-3">
              <div className="flex flex-col">
                <h2 className="font-headline-md text-headline-md text-on-surface font-bold tracking-tight">
                  Últimas Movimentações
                </h2>
                <span className="font-body-sm text-body-sm text-on-surface-variant">
                  Histórico recente de débitos e créditos da conta corrente
                </span>
              </div>
              <div className="flex items-center gap-2">
                <span className="font-body-sm text-body-sm px-3 py-1 rounded-lg bg-surface-container-low text-on-surface-variant flex items-center gap-1.5 border border-surface-container">
                  <span className="material-symbols-outlined text-[16px] text-emerald-600">check_circle</span>
                  Lançamentos Confirmados
                </span>
              </div>
            </div>

            {/* Lista de Transações Recentes */}
            <div className="flex flex-col gap-3">
              {recentTransactions.map((tx) => {
                const isCredit = tx.nature === 'CREDIT';
                const isFailed = tx.category === 'FAILED';

                return (
                  <div
                    key={tx.id}
                    className="p-4 rounded-xl bg-surface-container-low hover:bg-surface-container/60 transition-colors border border-surface-container/80 flex items-center justify-between gap-4"
                  >
                    <div className="flex items-center gap-3.5 min-w-0">
                      <div
                        className={`w-10 h-10 rounded-lg flex-shrink-0 flex items-center justify-center ${
                          isFailed
                            ? 'bg-error-container/40 text-error'
                            : isCredit
                            ? 'bg-emerald-500/10 text-emerald-700'
                            : 'bg-error-container/40 text-error'
                        }`}
                      >
                        <span className="material-symbols-outlined text-[22px]">
                          {isFailed
                            ? 'error'
                            : tx.type === 'DEPOSIT'
                            ? 'payments'
                            : tx.type === 'WITHDRAWAL'
                            ? 'local_atm'
                            : isCredit
                            ? 'call_received'
                            : 'call_made'}
                        </span>
                      </div>
                      <div className="flex flex-col min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className="font-headline-sm text-headline-sm text-[15px] text-on-surface font-semibold truncate">
                            {tx.description}
                          </span>
                          <span
                            className={`px-2 py-0.5 rounded font-code-sm text-[11px] font-bold ${
                              isFailed
                                ? 'bg-error-container text-on-error-container'
                                : isCredit
                                ? 'bg-emerald-500/15 text-emerald-900'
                                : 'bg-error-container text-on-error-container'
                            }`}
                          >
                            {isFailed ? 'REJEITADO' : isCredit ? 'CRÉDITO' : 'DÉBITO'}
                          </span>
                        </div>
                        <span className="font-body-sm text-body-sm text-on-surface-variant truncate">
                          Contraparte: <strong className="text-on-surface font-medium">{tx.counterpartyName}</strong> • {tx.counterpartyAccount}
                        </span>
                        <span className="font-code-sm text-[11px] text-on-surface-variant/80 mt-0.5">
                          {formatRelativeTime(tx.timestamp)} • Protocolo #{tx.sequence}
                        </span>
                      </div>
                    </div>
                    <div className="flex flex-col items-end flex-shrink-0">
                      <span
                        className={`font-code-lg text-code-lg font-bold ${
                          isFailed
                            ? 'text-on-surface-variant line-through'
                            : isCredit
                            ? 'text-emerald-700'
                            : 'text-error'
                        }`}
                      >
                        {isCredit ? '+' : isFailed ? '' : '-'}
                        {formatCentsToBrl(tx.amountCents)}
                      </span>
                      <span className="font-code-sm text-[11px] px-2 py-0.5 mt-1 rounded bg-surface-container text-on-surface-variant">
                        Saldo após: <strong className="text-on-surface">{formatCentsToBrl(tx.balanceAfterCents)}</strong>
                      </span>
                    </div>
                  </div>
                );
              })}
            </div>

            {/* Rodapé da Lista */}
            <div className="pt-2 flex flex-col sm:flex-row items-center justify-between gap-3 border-t border-surface-container">
              <span className="font-body-sm text-body-sm text-on-surface-variant">
                Exibindo {recentTransactions.length} de {statement.length} lançamentos
              </span>
              <Link
                href="/dashboard/extrato"
                className="px-4 py-2 rounded-xl bg-surface-container hover:bg-surface-container-high text-on-surface font-headline-sm text-[13px] font-semibold transition-colors flex items-center gap-1.5 shadow-xs no-underline"
              >
                Visualizar Extrato Completo com Filtros
                <span className="material-symbols-outlined text-[16px]">arrow_forward</span>
              </Link>
            </div>
          </div>
        </div>

        {/* COLUNA LATERAL (35% / 4 Cols Desktop) */}
        <div className="lg:col-span-4 flex flex-col gap-6 w-full">
          {/* 1. Card Visual Nexus Black Premium */}
          <div className="w-full bg-gradient-to-br from-neutral-950 via-zinc-900 to-neutral-900 text-white rounded-2xl p-6 shadow-md border border-neutral-800 flex flex-col justify-between gap-6 relative overflow-hidden">
            <div className="absolute -right-12 -top-12 w-48 h-48 bg-white/5 rounded-full blur-2xl pointer-events-none"></div>
            
            <div className="flex items-center justify-between z-10">
              <div className="flex items-center gap-2">
                <span className="material-symbols-outlined text-emerald-400 text-[24px]">contactless</span>
                <span className="font-label-caps text-[11px] tracking-wider text-neutral-400 font-semibold uppercase">
                  Nexus Black
                </span>
              </div>
              <span className="font-headline-sm text-headline-sm font-bold tracking-tight text-white">
                Nexus Bank
              </span>
            </div>

            <div className="flex items-center gap-4 z-10 my-1">
              <div className="w-11 h-8 rounded-md bg-gradient-to-tr from-amber-400 via-amber-300 to-amber-200 border border-amber-300 shadow flex items-center justify-center">
                <div className="w-7 h-5 rounded border border-amber-600/40 grid grid-cols-2"></div>
              </div>
              <span className="font-code-lg text-[18px] tracking-widest text-neutral-100 font-medium">
                {showCardDetails ? '4912 8841 0921 9012' : '•••• •••• •••• 9012'}
              </span>
            </div>

            <div className="flex items-end justify-between z-10">
              <div className="flex flex-col">
                <span className="text-[10px] font-label-caps uppercase text-neutral-400 tracking-wider">Titular</span>
                <span className="font-headline-sm text-[13px] text-white font-semibold uppercase tracking-wide">
                  {account.holderName}
                </span>
              </div>
              <div className="flex flex-col items-end">
                <span className="text-[10px] font-label-caps uppercase text-neutral-400 tracking-wider">
                  {cardBlocked ? 'Status' : 'Validade'}
                </span>
                <span className={`font-code-sm text-code-sm font-medium ${cardBlocked ? 'text-red-400' : 'text-white'}`}>
                  {cardBlocked ? 'BLOQUEADO' : '09/29'}
                </span>
              </div>
            </div>

            {/* Ações do Cartão */}
            <div className="flex items-center gap-2 pt-3 border-t border-white/10 z-10">
              <button
                onClick={() => setShowCardDetails(!showCardDetails)}
                className="flex-1 py-1.5 px-3 rounded-lg bg-white/10 hover:bg-white/15 text-white font-body-sm text-[12px] font-medium transition-colors flex items-center justify-center gap-1.5 cursor-pointer"
                type="button"
              >
                <span className="material-symbols-outlined text-[15px]">
                  {showCardDetails ? 'visibility_off' : 'visibility'}
                </span>
                {showCardDetails ? 'Ocultar' : 'Ver Dados'}
              </button>
              <button
                onClick={() => setCardBlocked(!cardBlocked)}
                className="flex-1 py-1.5 px-3 rounded-lg bg-white/10 hover:bg-white/15 text-white font-body-sm text-[12px] font-medium transition-colors flex items-center justify-center gap-1.5 cursor-pointer"
                type="button"
              >
                <span className="material-symbols-outlined text-[15px]">
                  {cardBlocked ? 'lock_open' : 'lock'}
                </span>
                {cardBlocked ? 'Desbloquear' : 'Bloquear'}
              </button>
            </div>
          </div>

          {/* 2. Card Limites Operacionais Diários */}
          <div className="w-full bg-surface-container-lowest p-6 rounded-2xl shadow-sm border border-surface-container flex flex-col gap-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <span className="material-symbols-outlined text-secondary text-[22px]">tune</span>
                <h3 className="font-headline-sm text-headline-sm text-on-surface font-bold">
                  Limites Operacionais
                </h3>
              </div>
              <span className="px-2 py-0.5 rounded-lg bg-surface-container-high text-emerald-800 font-label-caps text-label-caps font-semibold flex items-center gap-1">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 inline-block animate-pulse"></span>
                DISPONÍVEL
              </span>
            </div>

            <div className="flex flex-col gap-3 bg-surface-container-low p-4 rounded-xl border border-surface-container">
              <div className="flex items-center justify-between text-body-sm">
                <span className="text-on-surface-variant">Consumido hoje:</span>
                <span className="font-code-sm text-code-sm text-on-surface font-bold">
                  {formatCentsToBrl(balance.usedTodayCents)}{' '}
                  <span className="text-secondary font-medium">({limitUsedPercent}%)</span>
                </span>
              </div>
              <div className="w-full bg-surface-container h-2.5 rounded-full overflow-hidden">
                <div
                  className="bg-secondary h-full rounded-full transition-all duration-500"
                  style={{ width: `${limitUsedPercent}%` }}
                ></div>
              </div>
              <div className="flex items-center justify-between font-code-sm text-[11px] text-on-surface-variant">
                <span>Consumido: {formatCentsToBrl(balance.usedTodayCents)}</span>
                <span>Total: {formatCentsToBrl(balance.dailyLimitCents)}</span>
              </div>
            </div>

            <div className="flex items-center justify-between bg-surface-container-low p-3.5 rounded-xl border border-surface-container">
              <div className="flex flex-col">
                <span className="font-label-caps text-[11px] text-on-surface-variant uppercase font-semibold">
                  Disponível para Transações
                </span>
                <span className="font-headline-sm text-headline-sm text-on-surface font-bold">
                  {formatCentsToBrl(availableLimitCents)}
                </span>
              </div>
              <button
                onClick={() => setAdjustLimitOpen(!adjustLimitOpen)}
                className="px-3.5 py-1.5 rounded-lg bg-surface-container-high text-on-surface font-code-sm text-[12px] font-semibold hover:bg-surface-variant transition-colors cursor-pointer"
                type="button"
              >
                Ajustar Limite
              </button>
            </div>

            {adjustLimitOpen && (
              <div className="p-3 bg-surface-container-low rounded-xl border border-surface-container text-body-sm text-on-surface-variant space-y-2 animate-fadeIn">
                <p>
                  Para solicitar o aumento do seu limite diário, envie sua solicitação para avaliação cadastral ou fale diretamente com seu gerente de relacionamento.
                </p>
                <div className="flex justify-end gap-2">
                  <button
                    onClick={() => {
                      setAdjustLimitOpen(false);
                      setLimitNotice('Solicitação de ajuste de limite enviada para análise.');
                      setTimeout(() => setLimitNotice(''), 3000);
                    }}
                    className="px-3 py-1 bg-secondary text-white rounded-lg text-xs font-semibold cursor-pointer"
                  >
                    Solicitar Aumento
                  </button>
                </div>
              </div>
            )}
          </div>

          {/* 3. Card Segurança & Proteção */}
          <div className="w-full bg-surface-container-lowest p-6 rounded-2xl shadow-sm border border-surface-container flex flex-col gap-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <span className="material-symbols-outlined text-secondary text-[22px]">shield</span>
                <h3 className="font-headline-sm text-headline-sm text-on-surface font-bold">
                  Segurança & Proteção
                </h3>
              </div>
              <span className="px-2 py-0.5 rounded-lg bg-emerald-500/10 text-emerald-800 font-label-caps text-[10px] font-bold uppercase border border-emerald-500/20">
                100% PROTEGIDA
              </span>
            </div>

            <div className="flex flex-col gap-2.5">
              <div className="flex items-center justify-between p-3 rounded-xl bg-surface-container-low border border-surface-container">
                <div className="flex items-center gap-3">
                  <span className="material-symbols-outlined text-emerald-600 text-[20px]">face</span>
                  <div className="flex flex-col">
                    <span className="text-on-surface font-medium text-body-sm leading-tight">Biometria Facial Ativa</span>
                    <span className="text-on-surface-variant text-[11px]">Reconhecimento avançado validado</span>
                  </div>
                </div>
                <span className="px-2 py-0.5 rounded bg-emerald-500/15 text-emerald-900 font-code-sm text-[11px] font-bold">
                  Ativo
                </span>
              </div>

              <div className="flex items-center justify-between p-3 rounded-xl bg-surface-container-low border border-surface-container">
                <div className="flex items-center gap-3">
                  <span className="material-symbols-outlined text-emerald-600 text-[20px]">notifications_active</span>
                  <div className="flex flex-col">
                    <span className="text-on-surface font-medium text-body-sm leading-tight">Alertas em Tempo Real</span>
                    <span className="text-on-surface-variant text-[11px]">Notificação instantânea via SMS e Push</span>
                  </div>
                </div>
                <span className="px-2 py-0.5 rounded bg-emerald-500/15 text-emerald-900 font-code-sm text-[11px] font-bold">
                  Ativo
                </span>
              </div>

              <div className="flex items-center justify-between p-3 rounded-xl bg-surface-container-low border border-surface-container">
                <div className="flex items-center gap-3">
                  <span className="material-symbols-outlined text-secondary text-[20px]">verified</span>
                  <div className="flex flex-col">
                    <span className="text-on-surface font-medium text-body-sm leading-tight">Proteção Institucional FGC</span>
                    <span className="text-on-surface-variant text-[11px]">Garantido nos termos regulamentares</span>
                  </div>
                </div>
                <span className="text-secondary font-semibold text-code-sm text-[11px]">
                  Certificado
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

