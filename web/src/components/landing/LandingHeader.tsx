"use client";

import React, { useState } from 'react';
import Link from 'next/link';

export function LandingHeader() {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  return (
    <header className="fixed top-0 left-0 w-full z-50 bg-surface-bright/95 backdrop-blur-md shadow-[0_1px_8px_rgba(0,0,0,0.04)] border-b border-surface-container">
      <div className="h-20 w-full px-margin flex items-center justify-between">
        <div className="flex items-center gap-space-xl">
          <Link className="flex items-center gap-space-sm" href="/">
            <div className="w-9 h-9 rounded bg-primary-container flex items-center justify-center">
              <span className="material-symbols-outlined text-primary-fixed text-[22px]">account_balance</span>
            </div>
            <div className="flex flex-col">
              <span className="font-headline-sm text-headline-sm tracking-tight text-on-surface">NEXUS BANK</span>
              <span className="font-label-caps text-label-caps uppercase tracking-wider text-on-surface-variant">Sovereign Clearing</span>
            </div>
          </Link>
          <nav className="hidden xl:flex items-center gap-space-lg">
            <a className="font-body-md text-body-md text-on-surface-variant hover:text-on-surface transition-colors" href="#recursos">Para Você</a>
            <a className="font-body-md text-body-md text-on-surface-variant hover:text-on-surface transition-colors" href="#omnichannel">Para Empresas</a>
            <a className="font-body-md text-body-md text-on-surface-variant hover:text-on-surface transition-colors" href="#simulador">Simulador 102% CDI</a>
            <a className="font-body-md text-body-md text-on-surface-variant hover:text-on-surface transition-colors" href="#faq">Perguntas Frequentes</a>
          </nav>
        </div>
        <div className="flex items-center gap-space-md">
          <Link
            className="hidden sm:inline-flex font-body-md text-body-md text-on-surface-variant hover:text-on-surface px-space-md py-space-sm rounded transition-colors font-medium"
            href="/login"
          >
            Acessar Conta
          </Link>
          <Link
            className="font-body-md text-body-md bg-[#0e1c2f] text-white hover:bg-[#1a2d47] px-5 py-2 rounded-full transition-all duration-200 shadow-2xs hover:shadow-md hover:shadow-black/15 font-semibold active:scale-[0.99]"
            href="/signup"
          >
            Abrir Conta
          </Link>
          <Link
            href="/login"
            title="Acessar Minha Conta"
            className="w-9 h-9 rounded-full bg-[#0e1c2f] hover:bg-[#1a2d47] flex items-center justify-center ml-space-xs text-white transition-all duration-200 shadow-2xs hover:shadow-md"
          >
            <span className="material-symbols-outlined text-[19px]">person</span>
          </Link>
          <button
            type="button"
            className="xl:hidden p-2 text-on-surface-variant hover:text-on-surface"
            onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
            aria-label="Abrir Menu"
          >
            <span className="material-symbols-outlined text-[24px]">
              {mobileMenuOpen ? 'close' : 'menu'}
            </span>
          </button>
        </div>
      </div>

      {/* Mobile Drawer Menu */}
      {mobileMenuOpen && (
        <div className="xl:hidden w-full bg-surface-bright border-b border-surface-container px-margin py-4 flex flex-col gap-3 shadow-md">
          <a
            href="#recursos"
            onClick={() => setMobileMenuOpen(false)}
            className="font-body-md text-body-md text-on-surface py-2 border-b border-surface-container"
          >
            Para Você
          </a>
          <a
            href="#omnichannel"
            onClick={() => setMobileMenuOpen(false)}
            className="font-body-md text-body-md text-on-surface py-2 border-b border-surface-container"
          >
            Para Empresas &amp; Extrato
          </a>
          <a
            href="#simulador"
            onClick={() => setMobileMenuOpen(false)}
            className="font-body-md text-body-md text-on-surface py-2 border-b border-surface-container"
          >
            Simulador 102% CDI
          </a>
          <a
            href="#faq"
            onClick={() => setMobileMenuOpen(false)}
            className="font-body-md text-body-md text-on-surface py-2 border-b border-surface-container"
          >
            Perguntas Frequentes
          </a>
          <Link
            href="/login"
            onClick={() => setMobileMenuOpen(false)}
            className="font-headline-sm text-[14px] text-on-surface py-2 border-b border-surface-container flex items-center justify-between"
          >
            <span>Já sou cliente (Acessar Conta)</span>
            <span className="material-symbols-outlined text-[18px]">login</span>
          </Link>
          <Link
            href="/signup"
            onClick={() => setMobileMenuOpen(false)}
            className="font-headline-sm text-[14px] text-[#0e1c2f] py-2 flex items-center justify-between font-bold group no-underline"
          >
            <span className="group-hover:underline">Abrir Conta Digital</span>
            <span className="material-symbols-outlined text-[16px] no-underline group-hover:translate-x-1 transition-transform">
              arrow_forward
            </span>
          </Link>
        </div>
      )}
    </header>
  );
}
