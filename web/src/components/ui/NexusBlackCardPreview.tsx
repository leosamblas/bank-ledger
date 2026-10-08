"use client";

import React from 'react';
import { NexusBlackCard } from './NexusBlackCard';

interface Props {
  cardHolderName: string;
}

export function NexusBlackCardPreview({ cardHolderName }: Props) {
  const holder = cardHolderName.trim() ? cardHolderName.toUpperCase() : "SEU NOME AQUI";

  return (
    <div className="bg-surface-container-lowest p-6 rounded-2xl shadow-sm border border-surface-container flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <span className="w-2.5 h-2.5 rounded-full bg-amber-400 animate-pulse"></span>
          <span className="font-label-caps text-label-caps uppercase tracking-wider text-secondary font-semibold">
            Prévia do Seu Cartão Black
          </span>
        </div>
        <span className="font-code-sm text-code-sm text-on-surface-variant bg-surface-container px-2 py-0.5 rounded">
          Metal • Zero Anuidade
        </span>
      </div>

      <NexusBlackCard
        cardHolder={holder}
        cardNumber="5428 9012 4431 9012"
        validThru="09/31"
        interactive={false}
      />

      <div className="flex items-center justify-between text-body-sm text-on-surface-variant pt-2 border-t border-surface-container">
        <div className="flex items-center gap-1.5">
          <span className="material-symbols-outlined text-[17px] text-secondary">
            verified
          </span>
          <span>Bandeira Global Black</span>
        </div>
        <div className="flex items-center gap-1.5">
          <span className="material-symbols-outlined text-[17px] text-secondary">
            contactless
          </span>
          <span>Apple Pay & Google Pay</span>
        </div>
      </div>
    </div>
  );
}
