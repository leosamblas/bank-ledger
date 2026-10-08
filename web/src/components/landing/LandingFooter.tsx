"use client";

import React from 'react';
import Link from 'next/link';

export function LandingFooter() {
  const scrollToTop = () => {
    if (typeof window !== 'undefined') {
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  };

  return (
    <footer className="w-full bg-surface-container-low text-on-surface-variant border-t border-surface-container">
      <div className="w-full px-margin pt-space-xl pb-space-lg max-w-7xl mx-auto">
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-gutter">
          {/* Col 1: Identidade & BACEN */}
          <div>
            <div className="flex items-center gap-space-sm mb-space-md">
              <div className="w-7 h-7 rounded bg-primary-container flex items-center justify-center shadow-xs">
                <span className="material-symbols-outlined text-primary-fixed text-[18px]">
                  account_balance
                </span>
              </div>
              <span className="font-headline-sm text-headline-sm text-on-surface font-bold tracking-tight">
                NEXUS BANK S.A.
              </span>
            </div>
            <p className="font-body-sm text-body-sm text-on-surface-variant mb-space-md leading-relaxed">
              Instituição Financeira autorizada e regulada pelo Banco Central do Brasil. Infraestrutura de liquidação institucional, câmbio e custódia soberana.
            </p>
            <div className="flex items-center gap-space-sm flex-wrap">
              <span className="font-label-caps text-label-caps px-space-sm py-space-xs bg-surface-container rounded text-on-surface-variant font-medium">
                BACEN Nº 0418
              </span>
              <span className="font-label-caps text-label-caps px-space-sm py-space-xs bg-surface-container rounded text-on-surface-variant font-medium">
                ISIN BRNEXU
              </span>
            </div>
          </div>

          {/* Col 2: Canais de Atendimento */}
          <div>
            <h4 className="font-headline-sm text-headline-sm text-on-surface font-semibold mb-space-md">
              Canais de Atendimento
            </h4>
            <ul className="space-y-space-sm font-body-sm text-body-sm">
              <li>
                <span className="font-label-caps text-label-caps block text-on-surface font-semibold">
                  SAC 24h
                </span>
                <span className="font-code-md text-code-md text-on-surface font-semibold">
                  0800 772 0100
                </span>
                <p className="text-on-surface-variant text-[12px] mt-0.5">
                  Reclamações, cancelamentos e informações gerais
                </p>
              </li>
              <li>
                <span className="font-label-caps text-label-caps block text-on-surface font-semibold">
                  Ouvidoria Institucional
                </span>
                <span className="font-code-md text-code-md text-on-surface font-semibold">
                  0800 772 0199
                </span>
                <p className="text-on-surface-variant text-[12px] mt-0.5">
                  Atendimento em dias úteis, das 09h às 18h
                </p>
              </li>
              <li>
                <span className="font-label-caps text-label-caps block text-on-surface font-semibold">
                  Deficientes Auditivos ou de Fala
                </span>
                <span className="font-code-md text-code-md text-on-surface font-semibold">
                  0800 772 0155
                </span>
              </li>
            </ul>
          </div>

          {/* Col 3: Conformidade e Garantias */}
          <div>
            <h4 className="font-headline-sm text-headline-sm text-on-surface font-semibold mb-space-md">
              Conformidade e Garantias
            </h4>
            <ul className="space-y-space-sm font-body-sm text-body-sm leading-relaxed">
              <li className="flex items-start gap-space-xs">
                <span className="material-symbols-outlined text-[18px] text-secondary flex-shrink-0 mt-0.5">
                  verified_user
                </span>
                <span>
                  Garantia ordinária do <strong className="text-on-surface font-semibold">Fundo Garantidor de Créditos (FGC)</strong> até R$ 250.000 por CPF/CNPJ.
                </span>
              </li>
              <li className="flex items-start gap-space-xs">
                <span className="material-symbols-outlined text-[18px] text-secondary flex-shrink-0 mt-0.5">
                  policy
                </span>
                <span>
                  Aderente às diretrizes de Open Finance Brasil e Resoluções CMN.
                </span>
              </li>
              <li className="flex items-start gap-space-xs">
                <span className="material-symbols-outlined text-[18px] text-secondary flex-shrink-0 mt-0.5">
                  lock
                </span>
                <span>
                  Sistemas com criptografia pós-quântica e chaves HSM dedicadas.
                </span>
              </li>
            </ul>
          </div>

          {/* Col 4: Regulatório e Legal */}
          <div>
            <h4 className="font-headline-sm text-headline-sm text-on-surface font-semibold mb-space-md">
              Regulatório e Legal
            </h4>
            <ul className="space-y-2 font-body-sm text-body-sm">
              <li>
                <Link className="hover:text-on-surface transition-colors block py-0.5" href="/privacy">
                  Política de Privacidade e LGPD
                </Link>
              </li>
              <li>
                <Link className="hover:text-on-surface transition-colors block py-0.5" href="/terms">
                  Termos e Condições de Uso
                </Link>
              </li>
              <li>
                <Link className="hover:text-on-surface transition-colors block py-0.5" href="/security">
                  Diretrizes de Segurança da Informação
                </Link>
              </li>
              <li>
                <Link className="hover:text-on-surface transition-colors block py-0.5" href="/fees">
                  Tabela de Tarifas e Serviços
                </Link>
              </li>
              <li>
                <Link className="hover:text-on-surface transition-colors block py-0.5" href="/financials">
                  Demonstrações Financeiras e Balanços
                </Link>
              </li>
            </ul>
          </div>
        </div>
      </div>

      {/* Bottom Bar: Copyright, SPB Status & Back to Top - Flush com o final da página */}
      <div className="w-full border-t border-surface-container bg-surface-container-high/40">
        <div className="w-full px-margin py-space-md max-w-7xl mx-auto flex flex-col md:flex-row items-center justify-between gap-space-md font-body-sm text-body-sm text-on-surface-variant">
          <div className="flex flex-col md:flex-row md:items-center gap-space-sm text-center md:text-left">
            <span>© 2025 Nexus Bank S.A. CNPJ 00.418.992/0001-44</span>
            <span className="hidden md:inline text-outline-variant">•</span>
            <span>Av. Brigadeiro Faria Lima, 3477, Itaim Bibi, São Paulo - SP</span>
          </div>
          <div className="flex items-center gap-space-md">
            <div className="flex items-center gap-2">
              <span className="font-code-sm text-code-sm uppercase font-semibold text-on-surface">
                SISTEMA SPB CONECTADO
              </span>
              <div className="w-2 h-2 rounded-full bg-secondary animate-pulse"></div>
            </div>
            <span className="text-outline-variant">•</span>
            <button
              type="button"
              onClick={scrollToTop}
              className="text-on-surface hover:text-secondary font-semibold flex items-center gap-1 transition-colors cursor-pointer"
              title="Voltar ao início da página"
            >
              <span>Topo</span>
              <span className="material-symbols-outlined text-[16px]">arrow_upward</span>
            </button>
          </div>
        </div>
      </div>
    </footer>
  );
}
