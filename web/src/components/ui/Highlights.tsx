import React from 'react';

export function Highlights() {
  return (
    <div className="space-y-3">
      <div className="bg-surface-container-lowest p-5 rounded-xl shadow-sm flex items-start gap-4">
        <div className="w-10 h-10 rounded-lg bg-surface-container flex items-center justify-center text-primary flex-shrink-0">
          <span className="material-symbols-outlined">payments</span>
        </div>
        <div>
          <h2 className="font-headline-sm text-headline-sm text-on-surface">Conta Corrente BRL sem anuidade</h2>
          <p className="font-body-sm text-body-sm text-on-surface-variant mt-0.5">
            Zero mensalidade de manutenção, extratos transparentes e rendimento automático sobre o saldo.
          </p>
        </div>
      </div>
      <div className="bg-surface-container-lowest p-5 rounded-xl shadow-sm flex items-start gap-4">
        <div className="w-10 h-10 rounded-lg bg-surface-container flex items-center justify-center text-primary flex-shrink-0">
          <span className="material-symbols-outlined">bolt</span>
        </div>
        <div>
          <h2 className="font-headline-sm text-headline-sm text-on-surface">Transferências e pagamentos instantâneos</h2>
          <p className="font-body-sm text-body-sm text-on-surface-variant mt-0.5">
            Pix 24/7 sem limites ocultos, TEDs instantâneas e agendamento financeiro inteligente.
          </p>
        </div>
      </div>
      <div className="bg-surface-container-lowest p-5 rounded-xl shadow-sm flex items-start gap-4">
        <div className="w-10 h-10 rounded-lg bg-surface-container flex items-center justify-center text-primary flex-shrink-0">
          <span className="material-symbols-outlined">shield</span>
        </div>
        <div>
          <h2 className="font-headline-sm text-headline-sm text-on-surface">Garantia e segurança bancária de ponta</h2>
          <p className="font-body-sm text-body-sm text-on-surface-variant mt-0.5">
            Proteção com biometria facial, autenticação de múltiplos fatores e conformidade estrita com o Banco Central.
          </p>
        </div>
      </div>
    </div>
  );
}
