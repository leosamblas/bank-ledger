"use client";

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useBanking } from '@/hooks/useBanking';
import { accountService } from '@/services/accountService';
import { UpdateProfileRequest } from '@/services/types';
import { toastManager } from '@/components/ui/Toast';

/**
 * Formatador de telefone celular com máscara (00) 00000-0000 ou fixo (00) 0000-0000
 */
export function formatPhone(value: string): string {
  const digits = value.replace(/\D/g, '').slice(0, 11);
  if (digits.length === 0) return '';
  if (digits.length <= 2) return `(${digits}`;
  if (digits.length <= 6) return `(${digits.slice(0, 2)}) ${digits.slice(2)}`;
  if (digits.length <= 10) {
    return `(${digits.slice(0, 2)}) ${digits.slice(2, 6)}-${digits.slice(6)}`;
  }
  return `(${digits.slice(0, 2)}) ${digits.slice(2, 7)}-${digits.slice(7)}`;
}

/**
 * Formatador de CEP brasileiro com máscara 00000-000
 */
export function formatCep(value: string): string {
  const digits = value.replace(/\D/g, '').slice(0, 8);
  if (digits.length === 0) return '';
  if (digits.length > 5) {
    return `${digits.slice(0, 5)}-${digits.slice(5)}`;
  }
  return digits;
}

/**
 * Validador de formato de e-mail RFC compliant
 */
export function validateEmail(email: string): boolean {
  if (!email || !email.trim()) return false;
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
  return emailRegex.test(email.trim());
}

export function formatDisplayDate(dateStr?: string): string {
  if (!dateStr) return '-';
  const clean = dateStr.trim();
  if (clean.includes('-')) {
    const parts = clean.split('-');
    if (parts.length === 3) {
      const [y, m, d] = parts;
      return `${d.padStart(2, '0')}/${m.padStart(2, '0')}/${y}`;
    }
  }
  return clean;
}

export interface ViaCepResult {
  street: string;
  neighborhood: string;
  city: string;
  state: string;
}

/**
 * Consulta de CEP via API pública do ViaCEP
 */
export async function fetchAddressByCep(cep: string): Promise<ViaCepResult | null> {
  const clean = cep.replace(/\D/g, '');
  if (clean.length !== 8) return null;

  try {
    const res = await fetch(`https://viacep.com.br/ws/${clean}/json/`);
    if (!res.ok) return null;
    const data = await res.json();
    if (data.erro === 'true' || data.erro === true) return null;
    return {
      street: data.logradouro || '',
      neighborhood: data.bairro || '',
      city: data.localidade || '',
      state: data.uf || '',
    };
  } catch {
    return null;
  }
}

export default function ProfilePage() {
  const router = useRouter();
  const { account, updateProfile, refresh, isLoading: isBankingLoading } = useBanking();

  // Contact State
  const [phone, setPhone] = useState('');
  const [email, setEmail] = useState('');

  // Address State
  const [postalCode, setPostalCode] = useState('');
  const [street, setStreet] = useState('');
  const [number, setNumber] = useState('');
  const [complement, setComplement] = useState('');
  const [neighborhood, setNeighborhood] = useState('');
  const [city, setCity] = useState('');
  const [state, setState] = useState('');

  // UI Flow State
  const [isCepLoading, setIsCepLoading] = useState(false);
  const [cepNotice, setCepNotice] = useState<{ message: string; type: 'info' | 'error' | 'success' } | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [feedback, setFeedback] = useState<{ message: string; type: 'success' | 'error' } | null>(null);

  // Load account data into form
  useEffect(() => {
    if (account) {
      if (account.phone) setPhone(formatPhone(account.phone));
      if (account.email) setEmail(account.email);
      if (account.address) {
        if (account.address.postalCode) setPostalCode(formatCep(account.address.postalCode));
        if (account.address.street) setStreet(account.address.street);
        if (account.address.number) setNumber(account.address.number);
        if (account.address.complement) setComplement(account.address.complement);
        if (account.address.neighborhood) setNeighborhood(account.address.neighborhood);
        if (account.address.city) setCity(account.address.city);
        if (account.address.state) setState(account.address.state);
      }
    }
  }, [account]);

  // Handle CEP change and auto-populate
  const handleCepChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const rawVal = e.target.value;
    const formatted = formatCep(rawVal);
    setPostalCode(formatted);
    setCepNotice(null);

    const clean = formatted.replace(/\D/g, '');
    if (clean.length === 8) {
      setIsCepLoading(true);
      try {
        const addr = await fetchAddressByCep(clean);
        if (addr) {
          if (addr.street) setStreet(addr.street);
          if (addr.neighborhood) setNeighborhood(addr.neighborhood);
          if (addr.city) setCity(addr.city);
          if (addr.state) setState(addr.state);
          setCepNotice({
            message: 'Endereço localizado e preenchido automaticamente.',
            type: 'success',
          });
        } else {
          setCepNotice({
            message: 'CEP não localizado. Preencha os campos de endereço manualmente.',
            type: 'info',
          });
        }
      } catch {
        setCepNotice({
          message: 'Falha ao buscar CEP. Preencha os campos manualmente.',
          type: 'error',
        });
      } finally {
        setIsCepLoading(false);
      }
    }
  };

  const handleResetForm = () => {
    if (account) {
      setPhone(formatPhone(account.phone || ''));
      setEmail(account.email || '');
      if (account.address) {
        setPostalCode(formatCep(account.address.postalCode || ''));
        setStreet(account.address.street || '');
        setNumber(account.address.number || '');
        setComplement(account.address.complement || '');
        setNeighborhood(account.address.neighborhood || '');
        setCity(account.address.city || '');
        setState(account.address.state || '');
      }
    }
    setCepNotice(null);
    setFeedback(null);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFeedback(null);

    // Validação de E-mail
    if (!validateEmail(email)) {
      setFeedback({
        message: 'Por favor, informe um endereço de e-mail válido.',
        type: 'error',
      });
      return;
    }

    // Validação de Telefone
    const cleanPhoneDigits = phone.replace(/\D/g, '');
    if (cleanPhoneDigits.length < 10) {
      setFeedback({
        message: 'Por favor, informe um número de telefone com DDD válido (pelo menos 10 dígitos).',
        type: 'error',
      });
      return;
    }

    // Validação de Endereço básico
    const cleanCepDigits = postalCode.replace(/\D/g, '');
    if (cleanCepDigits.length > 0 && cleanCepDigits.length < 8) {
      setFeedback({
        message: 'O CEP informado deve conter exatamente 8 dígitos.',
        type: 'error',
      });
      return;
    }

    setIsSubmitting(true);

    const payload: UpdateProfileRequest = {
      phone: cleanPhoneDigits,
      email: email.trim(),
      postalCode: cleanCepDigits || undefined,
      street: street.trim() || undefined,
      number: number.trim() || undefined,
      complement: complement.trim() || undefined,
      neighborhood: neighborhood.trim() || undefined,
      city: city.trim() || undefined,
      state: state.trim() || undefined,
      address: {
        postalCode: cleanCepDigits,
        street: street.trim(),
        number: number.trim(),
        complement: complement.trim(),
        neighborhood: neighborhood.trim(),
        city: city.trim(),
        state: state.trim(),
      },
    };

    try {
      if (typeof updateProfile === 'function') {
        await updateProfile(payload);
      } else {
        await accountService.updateProfile(payload);
        if (typeof refresh === 'function') {
          await refresh();
        }
      }

      setFeedback({
        message: 'Dados cadastrais atualizados com sucesso!',
        type: 'success',
      });
      toastManager.notify('Dados cadastrais atualizados com sucesso!', 'success');
    } catch (err: unknown) {
      const errorMsg =
        err instanceof Error ? err.message : 'Falha ao atualizar dados cadastrais. Tente novamente.';
      setFeedback({
        message: errorMsg,
        type: 'error',
      });
      toastManager.notify(errorMsg, 'error');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="w-full px-4 sm:px-6 lg:px-8 max-w-[1200px] mx-auto flex flex-col gap-6">
      {/* 1. Header do Perfil */}
      <div className="w-full bg-surface-container-lowest p-5 md:p-6 rounded-2xl shadow-sm border border-surface-container flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div className="flex items-center gap-3.5">
          <div className="w-12 h-12 rounded-2xl bg-primary-container text-on-primary flex items-center justify-center shadow-xs">
            <span className="material-symbols-outlined text-[26px] text-emerald-400">
              person
            </span>
          </div>
          <div className="flex flex-col">
            <h1 className="font-headline-lg text-headline-lg text-on-surface font-bold tracking-tight">
              Meu Perfil e Dados Cadastrais
            </h1>
            <p className="font-body-sm text-[13px] text-on-surface-variant mt-0.5">
              Gerencie suas informações de contato e endereço residencial cadastrados no Nexus Bank.
            </p>
          </div>
        </div>

        <Link
          href="/dashboard"
          className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl text-on-surface-variant hover:text-on-surface hover:bg-surface-container-low transition-colors font-body-sm text-[13px] font-medium no-underline border border-surface-container self-start sm:self-auto"
        >
          <span className="material-symbols-outlined text-[18px]">arrow_back</span>
          Voltar ao Início
        </Link>
      </div>

      {/* Global Feedback Banner */}
      {feedback && (
        <div
          role="alert"
          aria-live="polite"
          className={`p-4 rounded-xl border flex items-center gap-3 animate-fadeIn ${
            feedback.type === 'success'
              ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-900'
              : 'bg-error-container/60 border-error/30 text-error'
          }`}
        >
          <span className="material-symbols-outlined text-[22px]">
            {feedback.type === 'success' ? 'check_circle' : 'error'}
          </span>
          <span className="font-body-sm text-sm font-semibold">{feedback.message}</span>
        </div>
      )}

      <form onSubmit={handleSubmit} className="flex flex-col gap-6">
        {/* 2. Read-only Identity Section */}
        <div className="w-full bg-surface-container-lowest p-6 rounded-2xl shadow-sm border border-surface-container">
          <div className="flex items-center justify-between pb-4 border-b border-surface-container mb-5">
            <div className="flex items-center gap-2">
              <span className="material-symbols-outlined text-[20px] text-secondary">
                shield_person
              </span>
              <h2 className="font-headline-md text-headline-sm text-on-surface font-bold">
                Identidade do Titular (Dados Cadastrais)
              </h2>
            </div>
            <span className="flex items-center gap-1 text-[12px] font-semibold text-secondary bg-surface-container px-2.5 py-1 rounded-full">
              <span className="material-symbols-outlined text-[14px]">lock</span>
              Protegido
            </span>
          </div>

          {/* Informational Badge */}
          <div
            role="note"
            className="flex items-start gap-2.5 p-3.5 rounded-xl bg-amber-500/10 border border-amber-500/20 text-amber-900 text-body-sm text-[13px] mb-5 leading-relaxed"
          >
            <span className="material-symbols-outlined text-[20px] text-amber-700 shrink-0 mt-0.5">
              lock
            </span>
            <span>
              Dados pessoais cadastrais protegidos. Para alteração de titularidade, contate seu gerente.
            </span>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {/* Nome Completo */}
            <div className="flex flex-col gap-1.5">
              <label
                htmlFor="holderName"
                className="font-label-caps text-label-caps text-on-surface-variant font-semibold"
              >
                Nome Completo
              </label>
              <div className="relative">
                <input
                  id="holderName"
                  type="text"
                  readOnly
                  aria-readonly="true"
                  value={account.holderName || 'Carlos Eduardo Silva'}
                  className="w-full px-3.5 py-2.5 bg-surface-container-low text-on-surface-variant font-body-md text-sm rounded-xl border border-surface-container cursor-not-allowed select-none"
                />
                <span className="material-symbols-outlined text-[16px] text-outline absolute right-3 top-3">
                  lock
                </span>
              </div>
            </div>

            {/* CPF */}
            <div className="flex flex-col gap-1.5">
              <label
                htmlFor="cpf"
                className="font-label-caps text-label-caps text-on-surface-variant font-semibold"
              >
                CPF
              </label>
              <div className="relative">
                <input
                  id="cpf"
                  type="text"
                  readOnly
                  aria-readonly="true"
                  value={account.cpf || account.cpfMasked || '-'}
                  className="w-full px-3.5 py-2.5 bg-surface-container-low text-on-surface-variant font-body-md text-sm rounded-xl border border-surface-container cursor-not-allowed select-none"
                />
                <span className="material-symbols-outlined text-[16px] text-outline absolute right-3 top-3">
                  lock
                </span>
              </div>
            </div>

            {/* Data de Nascimento */}
            <div className="flex flex-col gap-1.5">
              <label
                htmlFor="birthDate"
                className="font-label-caps text-label-caps text-on-surface-variant font-semibold"
              >
                Data de Nascimento
              </label>
              <div className="relative">
                <input
                  id="birthDate"
                  type="text"
                  readOnly
                  aria-readonly="true"
                  value={formatDisplayDate(account.birthDate)}
                  className="w-full px-3.5 py-2.5 bg-surface-container-low text-on-surface-variant font-body-md text-sm rounded-xl border border-surface-container cursor-not-allowed select-none"
                />
                <span className="material-symbols-outlined text-[16px] text-outline absolute right-3 top-3">
                  lock
                </span>
              </div>
            </div>

            {/* Agência */}
            <div className="flex flex-col gap-1.5">
              <label
                htmlFor="agency"
                className="font-label-caps text-label-caps text-on-surface-variant font-semibold"
              >
                Agência
              </label>
              <div className="relative">
                <input
                  id="agency"
                  type="text"
                  readOnly
                  aria-readonly="true"
                  value={account.agency || '0001'}
                  className="w-full px-3.5 py-2.5 bg-surface-container-low text-on-surface-variant font-body-md text-sm rounded-xl border border-surface-container cursor-not-allowed select-none"
                />
                <span className="material-symbols-outlined text-[16px] text-outline absolute right-3 top-3">
                  lock
                </span>
              </div>
            </div>

            {/* Conta Corrente */}
            <div className="flex flex-col gap-1.5">
              <label
                htmlFor="accountNumber"
                className="font-label-caps text-label-caps text-on-surface-variant font-semibold"
              >
                Conta Corrente
              </label>
              <div className="relative">
                <input
                  id="accountNumber"
                  type="text"
                  readOnly
                  aria-readonly="true"
                  value={`${account.accountNumber}-${account.digit}`}
                  className="w-full px-3.5 py-2.5 bg-surface-container-low text-on-surface-variant font-body-md text-sm rounded-xl border border-surface-container cursor-not-allowed select-none"
                />
                <span className="material-symbols-outlined text-[16px] text-outline absolute right-3 top-3">
                  lock
                </span>
              </div>
            </div>

            {/* Banco e Instituição */}
            <div className="flex flex-col gap-1.5">
              <label
                htmlFor="bankName"
                className="font-label-caps text-label-caps text-on-surface-variant font-semibold"
              >
                Instituição
              </label>
              <div className="relative">
                <input
                  id="bankName"
                  type="text"
                  readOnly
                  aria-readonly="true"
                  value="001 - Nexus Bank S.A."
                  className="w-full px-3.5 py-2.5 bg-surface-container-low text-on-surface-variant font-body-md text-sm rounded-xl border border-surface-container cursor-not-allowed select-none"
                />
                <span className="material-symbols-outlined text-[16px] text-outline absolute right-3 top-3">
                  lock
                </span>
              </div>
            </div>
          </div>
        </div>

        {/* 3. Editable Contact Section */}
        <div className="w-full bg-surface-container-lowest p-6 rounded-2xl shadow-sm border border-surface-container">
          <div className="flex items-center gap-2 pb-4 border-b border-surface-container mb-5">
            <span className="material-symbols-outlined text-[20px] text-secondary">
              contact_phone
            </span>
            <h2 className="font-headline-md text-headline-sm text-on-surface font-bold">
              Informações de Contato
            </h2>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
            {/* Telefone Celular */}
            <div className="flex flex-col gap-1.5">
              <label
                htmlFor="phone"
                className="font-label-caps text-label-caps text-on-surface font-semibold flex items-center justify-between"
              >
                <span>Telefone Celular</span>
                <span className="text-[11px] text-on-surface-variant font-normal">Obrigatório</span>
              </label>
              <div className="relative">
                <input
                  id="phone"
                  type="tel"
                  required
                  aria-required="true"
                  placeholder="(00) 00000-0000"
                  value={phone}
                  onChange={(e) => setPhone(formatPhone(e.target.value))}
                  className="w-full pl-10 pr-3.5 py-2.5 bg-surface-container-lowest text-on-surface font-body-md text-sm rounded-xl border border-surface-container focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500 transition-all shadow-xs"
                />
                <span className="material-symbols-outlined text-[18px] text-on-surface-variant absolute left-3 top-3">
                  call
                </span>
              </div>
              <span className="text-[11px] text-on-surface-variant">
                Utilizado para confirmações de segurança e notificações bancárias via SMS/WhatsApp.
              </span>
            </div>

            {/* E-mail */}
            <div className="flex flex-col gap-1.5">
              <label
                htmlFor="email"
                className="font-label-caps text-label-caps text-on-surface font-semibold flex items-center justify-between"
              >
                <span>E-mail</span>
                <span className="text-[11px] text-on-surface-variant font-normal">Obrigatório</span>
              </label>
              <div className="relative">
                <input
                  id="email"
                  type="email"
                  required
                  aria-required="true"
                  placeholder="exemplo@nexusbank.com.br"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="w-full pl-10 pr-3.5 py-2.5 bg-surface-container-lowest text-on-surface font-body-md text-sm rounded-xl border border-surface-container focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500 transition-all shadow-xs"
                />
                <span className="material-symbols-outlined text-[18px] text-on-surface-variant absolute left-3 top-3">
                  mail
                </span>
              </div>
              <span className="text-[11px] text-on-surface-variant">
                Utilizado para envio de comprovantes, informes de rendimento e alertas de transação.
              </span>
            </div>
          </div>
        </div>

        {/* 4. Editable Residential Address Section */}
        <div className="w-full bg-surface-container-lowest p-6 rounded-2xl shadow-sm border border-surface-container">
          <div className="flex items-center justify-between pb-4 border-b border-surface-container mb-5">
            <div className="flex items-center gap-2">
              <span className="material-symbols-outlined text-[20px] text-secondary">
                home_pin
              </span>
              <h2 className="font-headline-md text-headline-sm text-on-surface font-bold">
                Endereço Residencial
              </h2>
            </div>
            <span className="text-[12px] text-on-surface-variant hidden sm:inline-block">
              Busca automática via ViaCEP
            </span>
          </div>

          {cepNotice && (
            <div
              role="status"
              aria-live="polite"
              className={`p-3 rounded-xl border flex items-center gap-2 text-[13px] mb-4 animate-fadeIn ${
                cepNotice.type === 'success'
                  ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-900'
                  : cepNotice.type === 'error'
                  ? 'bg-error-container/60 border-error/30 text-error'
                  : 'bg-surface-container text-on-surface-variant border-surface-container-high'
              }`}
            >
              <span className="material-symbols-outlined text-[18px]">
                {cepNotice.type === 'success'
                  ? 'check_circle'
                  : cepNotice.type === 'error'
                  ? 'error'
                  : 'info'}
              </span>
              <span>{cepNotice.message}</span>
            </div>
          )}

          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {/* CEP */}
            <div className="flex flex-col gap-1.5">
              <label
                htmlFor="postalCode"
                className="font-label-caps text-label-caps text-on-surface font-semibold flex items-center justify-between"
              >
                <span>CEP</span>
                {isCepLoading && (
                  <span className="text-[11px] text-emerald-600 flex items-center gap-1">
                    <span className="material-symbols-outlined text-[14px] animate-spin">
                      progress_activity
                    </span>
                    Buscando CEP...
                  </span>
                )}
              </label>
              <div className="relative">
                <input
                  id="postalCode"
                  type="text"
                  placeholder="00000-000"
                  value={postalCode}
                  onChange={handleCepChange}
                  maxLength={9}
                  className="w-full px-3.5 py-2.5 bg-surface-container-lowest text-on-surface font-body-md text-sm rounded-xl border border-surface-container focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500 transition-all shadow-xs"
                />
                <span className="material-symbols-outlined text-[18px] text-on-surface-variant absolute right-3 top-3">
                  search
                </span>
              </div>
            </div>

            {/* Logradouro */}
            <div className="flex flex-col gap-1.5 md:col-span-2">
              <label
                htmlFor="street"
                className="font-label-caps text-label-caps text-on-surface font-semibold"
              >
                Logradouro / Rua / Avenida
              </label>
              <input
                id="street"
                type="text"
                placeholder="Ex: Avenida Paulista"
                value={street}
                onChange={(e) => setStreet(e.target.value)}
                className="w-full px-3.5 py-2.5 bg-surface-container-lowest text-on-surface font-body-md text-sm rounded-xl border border-surface-container focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500 transition-all shadow-xs"
              />
            </div>

            {/* Número */}
            <div className="flex flex-col gap-1.5">
              <label
                htmlFor="number"
                className="font-label-caps text-label-caps text-on-surface font-semibold"
              >
                Número
              </label>
              <input
                id="number"
                type="text"
                placeholder="Ex: 1000"
                value={number}
                onChange={(e) => setNumber(e.target.value)}
                className="w-full px-3.5 py-2.5 bg-surface-container-lowest text-on-surface font-body-md text-sm rounded-xl border border-surface-container focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500 transition-all shadow-xs"
              />
            </div>

            {/* Complemento */}
            <div className="flex flex-col gap-1.5">
              <label
                htmlFor="complement"
                className="font-label-caps text-label-caps text-on-surface font-semibold"
              >
                Complemento
              </label>
              <input
                id="complement"
                type="text"
                placeholder="Ex: Apto 101, Bloco B"
                value={complement}
                onChange={(e) => setComplement(e.target.value)}
                className="w-full px-3.5 py-2.5 bg-surface-container-lowest text-on-surface font-body-md text-sm rounded-xl border border-surface-container focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500 transition-all shadow-xs"
              />
            </div>

            {/* Bairro */}
            <div className="flex flex-col gap-1.5">
              <label
                htmlFor="neighborhood"
                className="font-label-caps text-label-caps text-on-surface font-semibold"
              >
                Bairro
              </label>
              <input
                id="neighborhood"
                type="text"
                placeholder="Ex: Bela Vista"
                value={neighborhood}
                onChange={(e) => setNeighborhood(e.target.value)}
                className="w-full px-3.5 py-2.5 bg-surface-container-lowest text-on-surface font-body-md text-sm rounded-xl border border-surface-container focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500 transition-all shadow-xs"
              />
            </div>

            {/* Cidade */}
            <div className="flex flex-col gap-1.5 md:col-span-2">
              <label
                htmlFor="city"
                className="font-label-caps text-label-caps text-on-surface font-semibold"
              >
                Cidade
              </label>
              <input
                id="city"
                type="text"
                placeholder="Ex: São Paulo"
                value={city}
                onChange={(e) => setCity(e.target.value)}
                className="w-full px-3.5 py-2.5 bg-surface-container-lowest text-on-surface font-body-md text-sm rounded-xl border border-surface-container focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500 transition-all shadow-xs"
              />
            </div>

            {/* UF */}
            <div className="flex flex-col gap-1.5">
              <label
                htmlFor="state"
                className="font-label-caps text-label-caps text-on-surface font-semibold"
              >
                UF / Estado
              </label>
              <input
                id="state"
                type="text"
                maxLength={2}
                placeholder="SP"
                value={state}
                onChange={(e) => setState(e.target.value.toUpperCase())}
                className="w-full px-3.5 py-2.5 bg-surface-container-lowest text-on-surface font-body-md text-sm rounded-xl border border-surface-container focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500 transition-all shadow-xs uppercase"
              />
            </div>
          </div>
        </div>

        {/* 5. Action Buttons */}
        <div className="w-full bg-surface-container-lowest p-5 rounded-2xl shadow-sm border border-surface-container flex flex-col sm:flex-row items-center justify-end gap-3">
          <button
            type="button"
            onClick={handleResetForm}
            className="w-full sm:w-auto px-5 py-2.5 rounded-xl border border-surface-container text-on-surface font-body-sm font-semibold hover:bg-surface-container-low transition-colors cursor-pointer"
          >
            Cancelar
          </button>

          <button
            type="submit"
            disabled={isSubmitting || isBankingLoading}
            className="w-full sm:w-auto px-6 py-2.5 rounded-xl bg-emerald-600 text-white font-body-sm font-semibold hover:bg-emerald-700 transition-all shadow-xs hover:shadow-md flex items-center justify-center gap-2 cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {isSubmitting ? (
              <>
                <span className="material-symbols-outlined text-[18px] animate-spin">
                  progress_activity
                </span>
                <span>Salvando...</span>
              </>
            ) : (
              <>
                <span className="material-symbols-outlined text-[18px]">save</span>
                <span>Salvar Alterações</span>
              </>
            )}
          </button>
        </div>
      </form>
    </div>
  );
}
