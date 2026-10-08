"use client";

import { useState, useMemo } from 'react';

export function Simulator() {
  const [principal, setPrincipal] = useState(20000);
  const [months, setMonths] = useState(12);

  const formatBRL = (val: number) => {
    return val.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  };

  const { totalNexus, gainNexus, totalPoup, gainPoup, diff } = useMemo(() => {
    const rateNexusMonthly = 0.0085;
    const ratePoupMonthly = 0.0050;

    const tNexus = principal * Math.pow(1 + rateNexusMonthly, months);
    const tPoup = principal * Math.pow(1 + ratePoupMonthly, months);

    return {
      totalNexus: tNexus,
      gainNexus: tNexus - principal,
      totalPoup: tPoup,
      gainPoup: tPoup - principal,
      diff: (tNexus - principal) - (tPoup - principal)
    };
  }, [principal, months]);

  return (
    <section className="w-full py-space-xl bg-surface-container-low scroll-mt-24 md:scroll-mt-28" id="simulador">
      <div className="w-full px-margin mx-auto max-w-7xl">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-gutter items-center">
          {/* Left: Context & Explanation */}
          <div className="lg:col-span-5 flex flex-col gap-space-sm">
            <span className="font-label-caps text-label-caps uppercase tracking-wider text-secondary">Rentabilidade Inteligente</span>
            <h2 className="font-headline-lg text-headline-lg text-on-surface">
              Veja seu patrimônio render muito mais que na poupança.
            </h2>
            <p className="font-body-md text-body-md text-on-surface-variant">
              No Nexus Bank, o saldo da sua conta rende diariamente a <strong className="text-on-surface">102% do CDI</strong>, com liquidez diária imediata e total cobertura pelo Fundo Garantidor de Créditos.
            </p>
            <div className="mt-space-md p-space-md rounded-lg bg-surface-container-lowest shadow-sm flex flex-col gap-space-xs">
              <div className="flex items-center gap-space-xs text-on-surface">
                <span className="material-symbols-outlined text-secondary text-[20px]">info</span>
                <span className="font-headline-sm text-headline-sm">Liquidez Diária Real</span>
              </div>
              <p className="font-body-sm text-body-sm text-on-surface-variant">
                Você pode resgatar ou utilizar seu saldo a qualquer momento via Pix ou cartão sem perder os rendimentos já consolidados.
              </p>
            </div>
          </div>
          
          {/* Right: Simulator Component */}
          <div className="lg:col-span-7 bg-surface-container-lowest p-space-lg rounded-xl shadow-md">
            <div className="flex flex-col gap-space-md">
              {/* Value Slider */}
              <div>
                <div className="flex justify-between items-center mb-space-xs">
                  <label className="font-label-caps text-label-caps text-on-surface-variant uppercase" htmlFor="calc-range">Valor Aplicado</label>
                  <span className="font-code-lg text-code-lg font-bold text-on-surface">{formatBRL(principal)}</span>
                </div>
                <input 
                  className="w-full h-2 bg-surface-container rounded-lg appearance-none cursor-pointer accent-primary-container" 
                  id="calc-range" 
                  max="250000" 
                  min="1000" 
                  step="1000" 
                  type="range" 
                  value={principal}
                  onChange={(e) => setPrincipal(Number(e.target.value))}
                />
                <div className="flex justify-between text-on-surface-variant font-code-sm text-code-sm mt-1">
                  <span>R$ 1.000</span>
                  <span>R$ 100.000</span>
                  <span>R$ 250.000 (Teto FGC)</span>
                </div>
              </div>
              
              {/* Term Selection */}
              <div>
                <span className="font-label-caps text-label-caps text-on-surface-variant uppercase block mb-space-xs">Prazo de Aplicação</span>
                <div className="grid grid-cols-3 gap-space-sm">
                  <button 
                    className={`py-space-sm rounded-lg font-code-md text-code-md transition-colors text-center ${months === 12 ? 'bg-primary-container text-primary-fixed' : 'bg-surface-container text-on-surface hover:bg-surface-container-high'}`} 
                    type="button"
                    onClick={() => setMonths(12)}
                  >
                    12 Meses
                  </button>
                  <button 
                    className={`py-space-sm rounded-lg font-code-md text-code-md transition-colors text-center ${months === 24 ? 'bg-primary-container text-primary-fixed' : 'bg-surface-container text-on-surface hover:bg-surface-container-high'}`} 
                    type="button"
                    onClick={() => setMonths(24)}
                  >
                    24 Meses
                  </button>
                  <button 
                    className={`py-space-sm rounded-lg font-code-md text-code-md transition-colors text-center ${months === 36 ? 'bg-primary-container text-primary-fixed' : 'bg-surface-container text-on-surface hover:bg-surface-container-high'}`} 
                    type="button"
                    onClick={() => setMonths(36)}
                  >
                    36 Meses
                  </button>
                </div>
              </div>
              
              {/* Comparison Metric Cards */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-space-md pt-space-xs">
                {/* Nexus Card */}
                <div className="p-space-md rounded-lg bg-surface-container-low flex flex-col justify-between">
                  <div>
                    <div className="flex items-center justify-between mb-space-xs">
                      <span className="font-label-caps text-label-caps text-secondary font-bold">NEXUS BANK (102% CDI)</span>
                      <span className="material-symbols-outlined text-secondary text-[18px]">verified</span>
                    </div>
                    <span className="font-code-lg text-code-lg font-bold text-on-surface">{formatBRL(totalNexus)}</span>
                  </div>
                  <div className="mt-space-sm flex items-center gap-1 text-on-surface-variant font-body-sm text-body-sm">
                    <span>Rendimento líquido est.:</span>
                    <strong className="text-on-surface font-code-sm text-code-sm">+{formatBRL(gainNexus)}</strong>
                  </div>
                </div>
                
                {/* Traditional Savings Card */}
                <div className="p-space-md rounded-lg bg-surface-container-highest/50 flex flex-col justify-between">
                  <div>
                    <div className="flex items-center justify-between mb-space-xs">
                      <span className="font-label-caps text-label-caps text-on-surface-variant">POUPANÇA TRADICIONAL</span>
                      <span className="material-symbols-outlined text-on-surface-variant text-[18px]">history</span>
                    </div>
                    <span className="font-code-lg text-code-lg font-bold text-on-surface-variant">{formatBRL(totalPoup)}</span>
                  </div>
                  <div className="mt-space-sm flex items-center gap-1 text-on-surface-variant font-body-sm text-body-sm">
                    <span>Rendimento est.:</span>
                    <span className="font-code-sm text-code-sm">+{formatBRL(gainPoup)}</span>
                  </div>
                </div>
              </div>
              
              {/* Summary callout */}
              <div className="p-space-sm bg-surface-container rounded flex items-center justify-between">
                <span className="font-body-sm text-body-sm text-on-surface">
                  Ganho adicional estimado no Nexus:
                </span>
                <span className="font-code-md text-code-md font-bold text-secondary">
                  +{formatBRL(diff)} a mais
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
