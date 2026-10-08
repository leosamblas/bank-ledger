import React from 'react';

export function Stepper() {
  return (
    <div className="py-8">
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        {/* Step 1 (Active) */}
        <div className="bg-surface-container-lowest p-4 rounded-xl shadow-sm flex items-center gap-4">
          <div className="w-9 h-9 rounded-full bg-primary text-on-primary flex items-center justify-center font-headline-sm text-headline-sm">
            1
          </div>
          <div className="min-w-0">
            <span className="block font-label-caps text-label-caps text-secondary uppercase">
              Passo Atual
            </span>
            <span className="font-headline-sm text-headline-sm text-on-surface truncate block">
              Dados Pessoais
            </span>
          </div>
        </div>
        {/* Step 2 */}
        <div className="bg-surface-container-low p-4 rounded-xl flex items-center gap-4 opacity-75">
          <div className="w-9 h-9 rounded-full bg-surface-container-highest text-on-surface-variant flex items-center justify-center font-headline-sm text-headline-sm">
            2
          </div>
          <div className="min-w-0">
            <span className="block font-label-caps text-label-caps text-on-surface-variant uppercase">
              Próxima Etapa
            </span>
            <span className="font-headline-sm text-headline-sm text-on-surface truncate block">
              Segurança &amp; Acesso
            </span>
          </div>
        </div>
        {/* Step 3 */}
        <div className="bg-surface-container-low p-4 rounded-xl flex items-center gap-4 opacity-75">
          <div className="w-9 h-9 rounded-full bg-surface-container-highest text-on-surface-variant flex items-center justify-center font-headline-sm text-headline-sm">
            3
          </div>
          <div className="min-w-0">
            <span className="block font-label-caps text-label-caps text-on-surface-variant uppercase">
              Finalização
            </span>
            <span className="font-headline-sm text-headline-sm text-on-surface truncate block">
              Ativação da Conta
            </span>
          </div>
        </div>
      </div>
    </div>
  );
}
