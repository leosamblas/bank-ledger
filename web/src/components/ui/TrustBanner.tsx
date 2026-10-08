import React from 'react';

export function TrustBanner() {
  return (
    <div className="bg-surface-container p-6 rounded-2xl flex flex-col gap-4">
      <div className="flex items-center gap-1 text-amber-500">
        <span className="material-symbols-outlined text-lg" style={{ fontVariationSettings: "'FILL' 1" }}>star</span>
        <span className="material-symbols-outlined text-lg" style={{ fontVariationSettings: "'FILL' 1" }}>star</span>
        <span className="material-symbols-outlined text-lg" style={{ fontVariationSettings: "'FILL' 1" }}>star</span>
        <span className="material-symbols-outlined text-lg" style={{ fontVariationSettings: "'FILL' 1" }}>star</span>
        <span className="material-symbols-outlined text-lg" style={{ fontVariationSettings: "'FILL' 1" }}>star</span>
        <span className="font-headline-sm text-headline-sm text-on-surface ml-2">4.9 / 5</span>
      </div>
      <p className="font-body-md text-body-md text-on-surface italic leading-relaxed">
        “A abertura de conta no Nexus Bank foi a experiência bancária mais fluida e segura que já tive. Em poucos instantes meu cartão virtual estava pronto e minha agência já operava normalmente.”
      </p>
      <div className="flex items-center justify-between pt-2 border-t border-surface-container-highest">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-full bg-primary-container text-on-primary flex items-center justify-center font-headline-sm text-headline-sm">
            LC
          </div>
          <div>
            <span className="block font-headline-sm text-headline-sm text-on-surface">Lucas Castanho</span>
            <span className="block font-body-sm text-body-sm text-on-surface-variant">Empresário e Cliente Nexus</span>
          </div>
        </div>
        <div className="text-right">
          <span className="block font-label-caps text-label-caps text-emerald-700 uppercase font-semibold">Comunidade Ativa</span>
          <span className="block font-body-sm text-body-sm font-semibold text-on-surface">Mais de 50.000 clientes</span>
        </div>
      </div>
    </div>
  );
}
