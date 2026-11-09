import React from 'react';

export function BankingFooter() {
  return (
    <footer className="w-full bg-surface-container-low py-6 border-t border-surface-container mt-auto">
      <div className="w-full px-4 sm:px-6 lg:px-8 max-w-[1600px] mx-auto flex flex-col md:flex-row items-center justify-between gap-4 text-on-surface-variant font-body-sm text-body-sm">
        <div className="flex items-center gap-2 flex-wrap text-center md:text-left">
          <span className="font-headline-sm text-headline-sm text-[13px] font-bold text-on-surface">
            Nexus Bank S.A.
          </span>
          <span>—</span>
          <span>Instituição autorizada pelo Banco Central do Brasil. CNPJ 00.000.000/0001-00.</span>
        </div>
        <div className="flex items-center gap-4 flex-wrap text-center md:text-right font-body-sm text-[13px]">
          <span>Ouvidoria: 0800 770 1234</span>
          <span>•</span>
          <span>SAC 24h: 0800 770 4321</span>
          <span>•</span>
          <span>© 2026 Nexus Bank S.A.</span>
        </div>
      </div>
    </footer>
  );
}

