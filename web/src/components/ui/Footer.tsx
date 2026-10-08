import React from 'react';
import Link from 'next/link';

interface FooterProps {
  className?: string;
}

export function Footer({ className = '' }: FooterProps) {
  return (
    <footer
      role="contentinfo"
      className={`w-full border-t border-surface-container mt-auto bg-surface shrink-0 ${className}`}
    >
      <div className="w-full max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6 flex flex-col sm:flex-row items-center justify-between text-on-surface-variant font-body-sm text-body-sm gap-4 text-center sm:text-left">
        <div className="flex flex-col sm:flex-row items-center gap-2 sm:gap-3">
          <Link
            href="/"
            className="inline-flex items-center gap-1.5 font-semibold text-on-surface hover:text-secondary transition-colors group no-underline"
          >
            <span className="material-symbols-outlined text-[16px] group-hover:-translate-x-1 transition-transform text-secondary no-underline shrink-0">
              arrow_back
            </span>
            <span className="group-hover:underline">Página Inicial</span>
          </Link>
          <span className="hidden sm:inline text-surface-container-highest">•</span>
          <p>
            Nexus Bank S.A. — Instituição de Pagamento autorizada pelo Banco Central do Brasil. CNPJ 00.000.000/0001-00.
          </p>
        </div>
        <div className="flex gap-4">
          <a className="hover:underline" href="#">
            Central de Ajuda
          </a>
          <a className="hover:underline" href="#">
            Segurança
          </a>
          <a className="hover:underline" href="#">
            Ouvidoria 0800
          </a>
        </div>
      </div>
    </footer>
  );
}

