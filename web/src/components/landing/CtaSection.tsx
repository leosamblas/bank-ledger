"use client";

import React from 'react';
import Link from 'next/link';

export function CtaSection() {
  return (
    <section className="w-full py-space-xl bg-surface">
      <div className="w-full px-margin mx-auto max-w-7xl">
        <div className="relative overflow-hidden rounded-2xl bg-primary-container text-on-primary p-space-xl md:p-14 shadow-xl flex flex-col md:flex-row items-center justify-between gap-space-lg">
          {/* Ambient subtle glow */}
          <div className="absolute -right-20 -bottom-20 w-80 h-80 rounded-full bg-secondary/30 blur-3xl pointer-events-none" />
          <div className="absolute -left-20 -top-20 w-60 h-60 rounded-full bg-secondary-fixed/10 blur-2xl pointer-events-none" />

          {/* Left: Copy & Tag */}
          <div className="max-w-xl z-10 flex flex-col">
            <div className="inline-flex items-center gap-space-xs self-start px-space-sm py-1 rounded bg-surface-container-highest/10 text-primary-fixed mb-space-sm">
              <span className="material-symbols-outlined text-[16px]">bolt</span>
              <span className="font-label-caps text-label-caps uppercase tracking-wider font-semibold">
                Abertura Descomplicada
              </span>
            </div>
            <h2 className="font-headline-lg text-headline-lg md:text-[32px] text-primary-fixed tracking-tight font-bold leading-tight">
              Pronto para transformar sua experiência financeira?
            </h2>
            <p className="font-body-md text-body-md text-primary-fixed/80 mt-space-xs leading-relaxed">
              Abra sua conta em menos de 3 minutos sem sair de casa e desfrute da mais avançada infraestrutura de pagamentos e rendimentos do Brasil.
            </p>
          </div>

          {/* Right: CTA Actions */}
          <div className="flex flex-col sm:flex-row items-center gap-space-sm z-10 w-full md:w-auto flex-shrink-0">
            <Link
              href="/signup"
              className="w-full sm:w-auto inline-flex items-center justify-center gap-2 px-8 py-3.5 rounded-full bg-white text-[#0e1c2f] hover:bg-surface-bright hover:shadow-xl hover:shadow-black/20 hover:-translate-y-0.5 transition-all duration-200 shadow-md font-headline-sm text-headline-sm font-bold active:translate-y-0 active:scale-[0.99] group no-underline"
            >
              <span>Abrir Minha Conta</span>
              <span className="material-symbols-outlined text-[20px] text-emerald-600 no-underline group-hover:scale-110 transition-transform shrink-0">
                check_circle
              </span>
            </Link>
            <Link
              href="/login"
              className="w-full sm:w-auto inline-flex items-center justify-center px-6 py-3.5 rounded-full bg-white/10 hover:bg-white/20 text-white transition-all duration-200 font-headline-sm text-headline-sm font-semibold border border-white/20 hover:border-white/40 shadow-2xs hover:shadow-xs active:scale-[0.99]"
            >
              Já sou cliente
            </Link>
          </div>
        </div>
      </div>
    </section>
  );
}
