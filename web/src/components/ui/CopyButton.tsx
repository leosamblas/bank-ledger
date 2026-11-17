"use client";

import React, { useState, useEffect, useCallback } from 'react';

export function getCopyAriaLabel(label: string): string {
  return `Copiar ${label}`;
}

export interface CopyButtonProps {
  textToCopy: string;
  label: string;
  className?: string;
}

export function CopyButton({ textToCopy, label, className = '' }: CopyButtonProps) {
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (!copied) return;
    const timer = setTimeout(() => {
      setCopied(false);
    }, 2000);
    return () => clearTimeout(timer);
  }, [copied]);

  const handleCopy = useCallback(async () => {
    try {
      if (typeof navigator !== 'undefined' && navigator.clipboard && navigator.clipboard.writeText) {
        await navigator.clipboard.writeText(textToCopy);
        setCopied(true);
      } else if (typeof document !== 'undefined') {
        const textArea = document.createElement('textarea');
        textArea.value = textToCopy;
        textArea.style.position = 'fixed';
        textArea.style.left = '-9999px';
        document.body.appendChild(textArea);
        textArea.focus();
        textArea.select();
        try {
          document.execCommand('copy');
          setCopied(true);
        } finally {
          document.body.removeChild(textArea);
        }
      }
    } catch (err) {
      console.error('Falha ao copiar texto:', err);
    }
  }, [textToCopy]);

  return (
    <button
      type="button"
      onClick={handleCopy}
      aria-label={getCopyAriaLabel(label)}
      title={copied ? 'Copiado!' : getCopyAriaLabel(label)}
      className={`min-h-[44px] min-w-[44px] p-2 inline-flex items-center justify-center gap-1.5 rounded-lg hover:bg-surface-container transition-all cursor-pointer text-on-surface-variant hover:text-on-surface ${className}`}
    >
      <span
        className={`material-symbols-outlined text-[18px] transition-colors ${
          copied ? 'text-emerald-600 font-bold' : ''
        }`}
      >
        {copied ? 'check' : 'content_copy'}
      </span>
      {copied && (
        <span className="text-xs font-semibold text-emerald-600 animate-fadeIn">
          Copiado!
        </span>
      )}
      <span className="sr-only" aria-live="polite">
        {copied ? `${label} copiado para a área de transferência` : ''}
      </span>
    </button>
  );
}

export default CopyButton;
