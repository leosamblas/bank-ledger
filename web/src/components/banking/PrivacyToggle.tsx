"use client";

import React, { useState, useEffect, useCallback } from 'react';

/**
 * Utilitário para mascarar strings de valores monetários.
 * Quando isPrivate for true, substitui o valor por •••••••• mantendo o prefixo monetário se houver.
 */
export function maskFinancialValue(val: string, isPrivate: boolean): string {
  if (!isPrivate) return val;
  if (!val) return '';
  const trimmed = val.trim();
  if (trimmed.startsWith('R$')) {
    return 'R$ ••••••••';
  }
  return '••••••••';
}

/**
 * Hook de persistência da preferência de privacidade de saldo no localStorage.
 * Trata hidratação SSR com segurança iniciando com false até montagem no cliente.
 */
export function useBalancePrivacy() {
  const [isPrivate, setIsPrivate] = useState<boolean>(false);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    try {
      const stored = localStorage.getItem('nexus_balance_hidden');
      if (stored === 'true') {
        setIsPrivate(true);
      }
    } catch {
      // localStorage indisponível ou desabilitado
    }

    const handleStorage = (event: StorageEvent) => {
      if (event.key === 'nexus_balance_hidden') {
        setIsPrivate(event.newValue === 'true');
      }
    };

    window.addEventListener('storage', handleStorage);
    return () => window.removeEventListener('storage', handleStorage);
  }, []);

  const togglePrivacy = useCallback(() => {
    setIsPrivate((prev) => {
      const next = !prev;
      if (typeof window !== 'undefined') {
        try {
          localStorage.setItem('nexus_balance_hidden', String(next));
        } catch {
          // localStorage indisponível ou desabilitado
        }
      }
      return next;
    });
  }, []);

  return { isPrivate, togglePrivacy };
}

export interface PrivacyToggleProps {
  isPrivate: boolean;
  onToggle: () => void;
  className?: string;
}

export function PrivacyToggle({ isPrivate, onToggle, className = '' }: PrivacyToggleProps) {
  return (
    <button
      type="button"
      onClick={onToggle}
      aria-label={isPrivate ? "Exibir valores financeiros" : "Ocultar valores financeiros"}
      aria-pressed={isPrivate}
      title={isPrivate ? "Exibir valores financeiros" : "Ocultar valores financeiros"}
      className={`min-h-[44px] min-w-[44px] flex items-center justify-center rounded-lg hover:bg-surface-container text-secondary transition-colors cursor-pointer ${className}`}
    >
      <span className="material-symbols-outlined text-[20px]">
        {isPrivate ? 'visibility_off' : 'visibility'}
      </span>
    </button>
  );
}

export default PrivacyToggle;
