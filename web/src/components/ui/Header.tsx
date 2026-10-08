import React from 'react';
import Link from 'next/link';

interface HeaderProps {
  currentPage?: 'signup' | 'login';
}

export function Header({ currentPage = 'signup' }: HeaderProps) {
  const isLogin = currentPage === 'login';

  return (
    <header className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pb-6 border-b border-surface-container">
      {/* Brand Identity / Logo */}
      <Link
        href="/"
        className="flex items-center gap-3 group no-underline select-none"
        title="Nexus Bank — Ir para a página inicial"
      >
        <div className="w-10 h-10 rounded-xl bg-[#0e1c2f] flex items-center justify-center text-white shadow-sm group-hover:bg-[#1a2d47] transition-colors shrink-0">
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
      <div className="flex items-center gap-3 sm:gap-4 flex-wrap self-start sm:self-auto">
        <div className="hidden sm:flex items-center gap-2 text-on-surface-variant font-body-sm text-body-sm px-3 py-1.5 rounded-full bg-surface-container-low/70 border border-surface-container">
          <span className="inline-flex w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
          <span className="font-medium text-[12px]">Ambiente Seguro SSL</span>
        </div>

        {isLogin ? (
          <Link
            href="/signup"
            className="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-[#0e1c2f] text-white hover:bg-[#1a2d47] font-body-sm font-semibold transition-all duration-200 shadow-xs hover:shadow-md hover:shadow-black/15 group no-underline"
          >
            <span>Abrir Conta Gratuita</span>
            <span className="material-symbols-outlined text-[16px] no-underline group-hover:translate-x-0.5 transition-transform shrink-0">
              arrow_forward
            </span>
          </Link>
        ) : (
          <Link
            href="/login"
            className="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-[#0e1c2f] text-white hover:bg-[#1a2d47] font-body-sm font-semibold transition-all duration-200 shadow-xs hover:shadow-md hover:shadow-black/15 group no-underline"
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

