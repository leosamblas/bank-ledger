"use client";

import React, { ChangeEvent } from 'react';
import { formatCpf, cleanCpf, isValidCpf, validateCpfStatus, CpfValidationResult } from '@/utils/cpf';

export interface CpfInputProps {
  id?: string;
  name?: string;
  value: string;
  onChange: (value: string, isValid: boolean) => void;
  label?: string;
  placeholder?: string;
  required?: boolean;
  autoFocus?: boolean;
  disabled?: boolean;
  className?: string;
  variant?: 'login' | 'form';
  showStatusMessage?: boolean;
  errorMessage?: string;
}

export function CpfInput({
  id = 'cpfInput',
  name = 'cpf',
  value,
  onChange,
  label,
  placeholder = '000.000.000-00',
  required = true,
  autoFocus = false,
  disabled = false,
  className = '',
  variant = 'login',
  showStatusMessage = true,
  errorMessage,
}: CpfInputProps) {
  const digits = cleanCpf(value);
  const isFilled = digits.length > 0;
  const isComplete = digits.length === 11;
  const validation: CpfValidationResult = validateCpfStatus(value);

  // Compute default label according to variant if not explicitly provided
  const displayLabel = label !== undefined ? label : (variant === 'login' ? 'CPF do Titular' : 'CPF');

  const handleChange = (e: ChangeEvent<HTMLInputElement>) => {
    const formatted = formatCpf(e.target.value);
    const newDigits = cleanCpf(formatted);
    const valid = newDigits.length === 11 ? isValidCpf(newDigits) : false;
    onChange(formatted, valid);
  };

  const handleClear = () => {
    onChange('', false);
  };

  // Border & Ring classes based on validation state
  const getBorderClass = () => {
    if (errorMessage) {
      return 'border-error ring-1 ring-error focus:border-error focus:ring-error';
    }
    if (!isComplete) {
      return 'border-transparent focus:border-secondary focus:ring-2 focus:ring-secondary/20';
    }
    if (validation.isValid) {
      return 'border-emerald-500/80 ring-1 ring-emerald-500/40 focus:border-emerald-600 focus:ring-emerald-500/40';
    }
    return 'border-error/80 ring-1 ring-error/40 focus:border-error focus:ring-error/40';
  };

  return (
    <div className={`space-y-1.5 ${className}`}>
      {displayLabel && (
        <div className="flex items-center justify-between">
          <label
            htmlFor={id}
            className={
              variant === 'login'
                ? "block font-label-caps text-label-caps uppercase text-on-surface-variant tracking-wider font-semibold"
                : "block font-body-sm text-body-sm font-semibold text-on-surface"
            }
          >
            {displayLabel}
          </label>
          {isComplete && (
            <span
              className={`text-[11px] font-mono font-semibold uppercase tracking-wider ${
                validation.isValid ? 'text-emerald-700' : 'text-error'
              }`}
            >
              {validation.isValid ? '✓ Válido' : '✕ Inválido'}
            </span>
          )}
        </div>
      )}

      <div className="relative flex items-center">
        {variant === 'login' && (
          <span className="absolute left-3.5 text-on-surface-variant material-symbols-outlined text-headline-sm pointer-events-none">
            badge
          </span>
        )}

        <input
          id={id}
          name={name}
          type="text"
          value={value}
          onChange={handleChange}
          placeholder={placeholder}
          maxLength={14}
          required={required}
          autoFocus={autoFocus}
          disabled={disabled}
          autoComplete="username"
          inputMode="numeric"
          className={`w-full ${
            variant === 'login'
              ? 'h-12 pl-11 pr-16 font-code-md text-code-md'
              : 'px-4 py-3 pr-16 font-code-md text-code-md'
          } bg-surface-container-low text-on-surface rounded-xl border ${getBorderClass()} focus:outline-none focus:bg-surface-container transition-all`}
        />

        {/* Action / Validation Status Badges on the right */}
        <div className="absolute right-3 flex items-center gap-1.5">
          {isComplete && (
            <span
              title={validation.message}
              className={`material-symbols-outlined text-[20px] ${
                validation.isValid ? 'text-emerald-600' : 'text-error'
              }`}
              style={{ fontVariationSettings: "'FILL' 1" }}
            >
              {validation.isValid ? 'check_circle' : 'error'}
            </span>
          )}

          {isFilled && !disabled && (
            <button
              type="button"
              onClick={handleClear}
              aria-label="Limpar CPF"
              className="text-on-surface-variant hover:text-on-surface transition-colors cursor-pointer p-0.5"
            >
              <span className="material-symbols-outlined text-[18px]">cancel</span>
            </button>
          )}
        </div>
      </div>

      {showStatusMessage && (
        <p
          className={`font-body-sm text-body-sm flex items-center gap-1.5 transition-colors ${
            errorMessage
              ? 'text-error font-medium'
              : !isComplete
              ? 'text-on-surface-variant'
              : validation.isValid
              ? 'text-emerald-700 font-medium'
              : 'text-error font-medium'
          }`}
        >
          {errorMessage ? (
            <>
              <span className="material-symbols-outlined text-[15px] shrink-0">error</span>
              <span>{errorMessage}</span>
            </>
          ) : isComplete ? (
            <>
              <span className="material-symbols-outlined text-[15px] shrink-0">
                {validation.isValid ? 'verified' : 'warning'}
              </span>
              <span>{validation.message}</span>
            </>
          ) : (
            <span>Validação de dígitos verificadores (Módulo 11) em tempo real.</span>
          )}
        </p>
      )}
    </div>
  );
}
