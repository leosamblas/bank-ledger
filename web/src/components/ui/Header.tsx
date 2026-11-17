import React from 'react';
import Link from 'next/link';

interface HeaderProps {
  currentPage?: 'signup' | 'login';
}

export function Header({ currentPage = 'signup' }: HeaderProps) {
  const isLogin = currentPage === 'login';

  return (
    <header className="h-16 flex items-center justify-between gap-4 pb-4 border-b border-surface-container">
      {/* Brand Identity / Logo */}
      <Link
        href="/"
        className="flex items-center gap-3 group no-underline select-none"
        title="Nexus Bank — Ir para a página inicial"
      >
        <div className="w-10 h-10 rounded-xl bg-primary-container text-on-primary flex items-center justify-center shadow-xs group-hover:bg-primary-container/90 transition-colors shrink-0">
          <span
            className="material-symbols-outlined text-2xl no-underline"
            style={{ fontVariationSettings: "'FILL' 1" }}
          >
            account_balance
          </span>
        </div>
        <div>
          <span className="font-headline-sm text-headline-sm text-on-surface tracking-tight block font-bold group-hover:text-secondary transition-colors">
            Nexus Bank
          </span>
          <span className="block font-label-caps text-label-caps text-secondary uppercase tracking-widest text-[10px]">
            Instituição de Pagamento • Bacen Autorizado
          </span>
        </div>
      </Link>

      {/* Right Actions: Trust Badge + Primary Context Action */}
      <div className="flex items-center gap-3 shrink-0">
        <div className="hidden md:flex items-center gap-2 text-on-surface-variant font-body-sm text-body-sm px-3 py-1.5 rounded-full bg-surface-container-low/70 border border-surface-container">
          <span className="inline-flex w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
          <span className="font-medium text-[12px]">Ambiente Seguro SSL</span>
        </div>

        {isLogin ? (
          <Link
            href="/signup"
            className="min-h-[44px] px-4 py-2 rounded-full bg-primary-container text-on-primary hover:bg-primary-container/90 font-body-sm font-semibold transition-all flex items-center gap-1.5 shadow-xs hover:shadow-md no-underline"
          >
            <span>Criar conta</span>
            <span className="material-symbols-outlined text-[16px] no-underline group-hover:translate-x-0.5 transition-transform shrink-0">
              arrow_forward
            </span>
          </Link>
        ) : (
          <Link
            href="/login"
            className="min-h-[44px] px-4 py-2 rounded-full bg-primary-container text-on-primary hover:bg-primary-container/90 font-body-sm font-semibold transition-all flex items-center gap-1.5 shadow-xs hover:shadow-md no-underline"
          >
            <span>Já tem conta? Entrar</span>
            <span className="material-symbols-outlined text-[16px] no-underline group-hover:translate-x-0.5 transition-transform shrink-0">
              login
            </span>
          </Link>
        )}
      </div>
    </header>
  );
}
