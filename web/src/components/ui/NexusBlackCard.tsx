"use client";

import React, { useState } from 'react';

interface NexusBlackCardProps {
  cardHolder?: string;
  cardNumber?: string;
  validThru?: string;
  cvv?: string;
  isVirtual?: boolean;
  interactive?: boolean;
  compact?: boolean;
  className?: string;
}

export function NexusBlackCard({
  cardHolder = "CARLOS EDUARDO SILVA",
  cardNumber = "5428 9012 4431 9012",
  validThru = "09/29",
  cvv = "832",
  isVirtual = false,
  interactive = true,
  compact = false,
  className = "",
}: NexusBlackCardProps) {
  const [showDetails, setShowDetails] = useState(false);
  const [isLocked, setIsLocked] = useState(false);
  const [copied, setCopied] = useState(false);

  const formattedMaskedNumber = "•••• •••• •••• " + cardNumber.slice(-4);
  const displayCardNumber = showDetails ? cardNumber : formattedMaskedNumber;

  const handleCopy = () => {
    if (navigator.clipboard) {
      navigator.clipboard.writeText(cardNumber.replace(/\s+/g, ''));
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  return (
    <div className={`flex flex-col gap-3 w-full ${className}`}>
      {/* Physical / Virtual Black Card Canvas (ISO/IEC 7810 ID-1 standard: 85.60mm x 53.98mm) */}
      <div
        className="relative w-full aspect-[85.6/53.98] rounded-2xl overflow-hidden transition-all duration-300 select-none group border border-neutral-700/80 shadow-[0_20px_50px_rgba(0,0,0,0.85),0_0_0_1px_rgba(255,255,255,0.06),inset_0_1px_1px_rgba(255,255,255,0.25)]"
        style={{
          aspectRatio: '85.6 / 53.98',
          background: 'radial-gradient(120% 120% at 85% 15%, #22242b 0%, #111215 45%, #08080a 100%)',
        }}
      >
        {/* Brushed Metal & Specular Light Sheen */}
        <div
          className="absolute inset-0 pointer-events-none opacity-40 mix-blend-overlay"
          style={{
            backgroundImage:
              'repeating-linear-gradient(45deg, rgba(255,255,255,0.02) 0px, rgba(255,255,255,0.02) 1px, transparent 1px, transparent 6px)',
          }}
        />

        {/* Diagonal Specular Reflection Highlight */}
        <div
          className="absolute inset-0 pointer-events-none opacity-60"
          style={{
            background:
              'linear-gradient(115deg, rgba(255,255,255,0.12) 0%, rgba(255,255,255,0.03) 28%, transparent 50%, rgba(212,175,55,0.05) 80%, transparent 100%)',
          }}
        />

        {/* Subtle Guilloché / Watermark Security Emblem */}
        <div className="absolute right-4 bottom-3 opacity-[0.04] pointer-events-none select-none text-white">
          <span className="material-symbols-outlined text-[200px]">shield</span>
        </div>

        {/* Card Content Layout */}
        <div className={`relative z-10 h-full ${compact ? 'p-4 sm:p-5' : 'p-5 sm:p-6'} flex flex-col justify-between text-white`}>
          {/* Header Row: Bank Name, Sovereign Black Tier & Contactless */}
          <div className="flex items-start justify-between">
            <div className="flex items-center gap-2.5">
              <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-neutral-800 to-black border border-neutral-700/70 flex items-center justify-center shadow-inner">
                <span className="material-symbols-outlined text-[19px] text-amber-300">
                  account_balance
                </span>
              </div>
              <div className="flex flex-col">
                <span className="font-bold tracking-tight text-[15px] sm:text-[16px] text-neutral-100 flex items-center gap-1.5 leading-none">
                  Nexus Bank
                  <span className="text-[9px] px-1.5 py-0.5 rounded bg-amber-400/15 border border-amber-400/30 text-amber-300 font-mono font-semibold tracking-wider">
                    BLACK
                  </span>
                </span>
                <span className="text-[10px] tracking-[0.18em] text-neutral-400 uppercase font-medium mt-1">
                  {isVirtual ? "Virtual Dedicado" : "Sovereign Metal"}
                </span>
              </div>
            </div>

            <div className="flex items-center gap-2">
              <span className="material-symbols-outlined text-[22px] text-neutral-300/80 hover:text-white transition-colors" title="Contactless Ativo">
                contactless
              </span>
              <div className="hidden sm:flex items-center gap-1 px-2 py-0.5 rounded-full bg-white/5 border border-white/10 text-[10px] text-neutral-300 font-mono">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse"></span>
                <span>FGC 250k</span>
              </div>
            </div>
          </div>

          {/* Middle Row: Realistic Gold EMV Chip & Card Number */}
          <div className="my-auto flex items-center justify-between gap-4 pt-1">
            <div className="flex items-center gap-3">
              {/* Realistic Gold EMV Smart Chip */}
              <div
                className="w-11 h-8 sm:w-12 sm:h-9 rounded-md relative overflow-hidden shadow-[0_2px_6px_rgba(0,0,0,0.6),inset_0_1px_1px_rgba(255,255,255,0.4)] border border-amber-500/60"
                style={{
                  background: 'linear-gradient(135deg, #F9E79F 0%, #D4AC0D 50%, #B7950B 100%)',
                }}
              >
                {/* Circuit contact etchings */}
                <div className="absolute inset-0 grid grid-cols-3 grid-rows-2 p-[2px] gap-[1px] opacity-70">
                  <div className="border-r border-b border-amber-900/50 rounded-tl-sm"></div>
                  <div className="border-b border-amber-900/50"></div>
                  <div className="border-l border-b border-amber-900/50 rounded-tr-sm"></div>
                  <div className="border-r border-amber-900/50 rounded-bl-sm"></div>
                  <div className="border-amber-900/50"></div>
                  <div className="border-l border-amber-900/50 rounded-br-sm"></div>
                </div>
                {/* Central microchip core */}
                <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-4 h-3 rounded-[3px] border border-amber-900/60 bg-amber-400/40"></div>
              </div>

              {/* Holographic Security Micro-Seal */}
              <div
                className="w-6 h-5 rounded-sm border border-neutral-600/60 flex items-center justify-center opacity-80 shadow-xs"
                style={{
                  background: 'linear-gradient(135deg, #4A5568 0%, #CBD5E0 25%, #ECC94B 50%, #9F7AEA 75%, #4299E1 100%)',
                }}
              >
                <span className="text-[7px] font-bold text-neutral-900 font-mono">NX</span>
              </div>
            </div>

            {/* Embossed Card Number */}
            <div className="flex items-center gap-2">
              <span
                className="font-mono text-[16px] sm:text-[19px] tracking-[0.18em] sm:tracking-[0.22em] text-neutral-100 font-semibold drop-shadow-[0_1px_2px_rgba(0,0,0,0.9)]"
                style={{
                  textShadow: '0 1px 2px rgba(0,0,0,0.95), 0 -1px 0 rgba(255,255,255,0.2)',
                }}
              >
                {displayCardNumber}
              </span>
              {showDetails && (
                <button
                  type="button"
                  onClick={handleCopy}
                  className="p-1 rounded bg-white/10 hover:bg-white/20 text-neutral-300 transition-colors"
                  title="Copiar número do cartão"
                >
                  <span className="material-symbols-outlined text-[15px]">
                    {copied ? 'check' : 'content_copy'}
                  </span>
                </button>
              )}
            </div>
          </div>

          {/* Bottom Row: Holder, Expiration, CVV and Mastercard / Sovereign Logo */}
          <div className="flex items-end justify-between pt-2 border-t border-white/10">
            <div className="flex flex-col">
              <span className="text-[8px] sm:text-[9px] uppercase tracking-[0.2em] text-neutral-400 font-medium">
                Titular
              </span>
              <span
                className="font-sans text-[12px] sm:text-[13px] font-semibold tracking-wider text-neutral-100 uppercase truncate max-w-[190px] sm:max-w-[220px]"
                style={{
                  textShadow: '0 1px 1px rgba(0,0,0,0.9)',
                }}
              >
                {cardHolder}
              </span>
            </div>

            <div className="flex items-center gap-4">
              <div className="flex flex-col items-center">
                <span className="text-[8px] sm:text-[9px] uppercase tracking-[0.2em] text-neutral-400 font-medium">
                  Validade
                </span>
                <span className="font-mono text-[12px] sm:text-[13px] text-neutral-100 font-medium">
                  {validThru}
                </span>
              </div>

              {showDetails && (
                <div className="flex flex-col items-center">
                  <span className="text-[8px] sm:text-[9px] uppercase tracking-[0.2em] text-amber-300 font-medium">
                    CVV
                  </span>
                  <span className="font-mono text-[12px] sm:text-[13px] text-amber-300 font-bold">
                    {cvv}
                  </span>
                </div>
              )}

              {/* Master / Sovereign Black Rings Symbol */}
              <div className="flex items-center relative pl-2">
                <div className="w-6 h-6 sm:w-7 sm:h-7 rounded-full bg-neutral-700/80 border border-neutral-500/60 shadow-inner"></div>
                <div className="w-6 h-6 sm:w-7 sm:h-7 rounded-full bg-neutral-400/80 border border-neutral-300/80 -ml-3 mix-blend-screen shadow-inner"></div>
              </div>
            </div>
          </div>
        </div>

        {/* Lock Overlay (when card is frozen) */}
        {isLocked && (
          <div className="absolute inset-0 bg-neutral-950/85 backdrop-blur-[3px] z-20 flex flex-col items-center justify-center gap-2 text-center p-4">
            <div className="w-12 h-12 rounded-full bg-error-container/20 border border-error/50 flex items-center justify-center text-error">
              <span className="material-symbols-outlined text-[26px]">lock</span>
            </div>
            <span className="font-semibold text-white text-[14px]">
              Cartão Temporariamente Bloqueado
            </span>
            <span className="text-[12px] text-neutral-400 max-w-xs">
              Transações físicas e online suspensas com sucesso para sua segurança.
            </span>
          </div>
        )}
      </div>

      {/* Interactive Controls Bar */}
      {interactive && (
        <div className="flex items-center gap-2 w-full pt-1">
          <button
            type="button"
            onClick={() => setShowDetails(!showDetails)}
            className="flex-1 py-2 px-3 rounded-lg bg-surface-container-high hover:bg-surface-variant text-on-surface font-headline-sm text-[12px] font-semibold transition-all flex items-center justify-center gap-1.5 shadow-xs"
          >
            <span className="material-symbols-outlined text-[16px]">
              {showDetails ? 'visibility_off' : 'visibility'}
            </span>
            <span>{showDetails ? 'Ocultar Dados' : 'Ver Dados do Cartão'}</span>
          </button>

          <button
            type="button"
            onClick={() => setIsLocked(!isLocked)}
            className={`flex-1 py-2 px-3 rounded-lg font-headline-sm text-[12px] font-semibold transition-all flex items-center justify-center gap-1.5 shadow-xs ${
              isLocked
                ? 'bg-emerald-600 hover:bg-emerald-700 text-white'
                : 'bg-surface-container-high hover:bg-error-container hover:text-error text-on-surface'
            }`}
          >
            <span className="material-symbols-outlined text-[16px]">
              {isLocked ? 'lock_open' : 'lock'}
            </span>
            <span>{isLocked ? 'Desbloquear' : 'Bloquear Cartão'}</span>
          </button>
        </div>
      )}
    </div>
  );
}
