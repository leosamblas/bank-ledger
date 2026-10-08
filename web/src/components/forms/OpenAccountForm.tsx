"use client";

import React, { useState, ChangeEvent, FormEvent } from 'react';
import Link from 'next/link';
import { CpfInput } from '@/components/ui/CpfInput';
import { isValidCpf } from '@/utils/cpf';
import { accountService, AccountResponse, ApiError } from '@/services';

interface Props {
  onNameChange: (name: string) => void;
}

export function OpenAccountForm({ onNameChange }: Props) {
  const [fullName, setFullName] = useState("");
  const [nameValid, setNameValid] = useState(false);

  const [cpf, setCpf] = useState("");
  const [isCpfValid, setIsCpfValid] = useState(false);
  const [cpfError, setCpfError] = useState("");

  const [birthDate, setBirthDate] = useState("");
  const [ageValid, setAgeValid] = useState<boolean | null>(null);
  const [ageMessage, setAgeMessage] = useState("Obrigatório ter 18 anos ou mais.");

  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");

  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isSuccess, setIsSuccess] = useState(false);
  const [accountCreated, setAccountCreated] = useState<AccountResponse | null>(null);
  const [formError, setFormError] = useState("");

  const handleNameChange = (e: ChangeEvent<HTMLInputElement>) => {
    const val = e.target.value;
    setFullName(val);
    onNameChange(val);
    const parts = val.trim().split(/\s+/);
    setNameValid(parts.length >= 2 && parts[0].length >= 2 && parts[1].length >= 2);
  };

  const handleBirthDateChange = (e: ChangeEvent<HTMLInputElement>) => {
    const val = e.target.value;
    setBirthDate(val);
    if (!val) {
      setAgeValid(null);
      setAgeMessage("Obrigatório ter 18 anos ou mais.");
      return;
    }
    const birth = new Date(val);
    const today = new Date();
    let age = today.getFullYear() - birth.getFullYear();
    const m = today.getMonth() - birth.getMonth();
    if (m < 0 || (m === 0 && today.getDate() < birth.getDate())) {
      age--;
    }

    if (age >= 18) {
      setAgeValid(true);
      setAgeMessage(`Idade verificada (${age} anos). Titular elegível.`);
    } else {
      setAgeValid(false);
      setAgeMessage("Abertura permitida apenas para maiores de 18 anos.");
    }
  };

  const handlePhoneChange = (e: ChangeEvent<HTMLInputElement>) => {
    let v = e.target.value.replace(/\D/g, '');
    if (v.length > 11) v = v.substring(0, 11);

    let masked = v;
    if (v.length > 10) {
      masked = v.replace(/^(\d{2})(\d{5})(\d{4})$/, '($1) $2-$3');
    } else if (v.length > 6) {
      masked = v.replace(/^(\d{2})(\d{4})(\d{0,4})$/, '($1) $2-$3');
    } else if (v.length > 2) {
      masked = v.replace(/^(\d{2})(\d{0,5})$/, '($1) $2');
    }
    setPhone(masked);
  };

  const hasLen = password.length >= 8;
  const hasUpper = /[A-Z]/.test(password);
  const hasNum = /[0-9]/.test(password);
  const hasSym = /[^A-Za-z0-9]/.test(password);
  const score = [hasLen, hasUpper, hasNum, hasSym].filter(Boolean).length;

  const getStrengthText = () => {
    if (score === 0) return { text: "Não informada", colorClass: "text-on-surface-variant" };
    if (score === 1) return { text: "Fraca", colorClass: "text-error" };
    if (score === 2) return { text: "Média", colorClass: "text-amber-600" };
    if (score === 3) return { text: "Boa", colorClass: "text-emerald-600" };
    return { text: "Excelente", colorClass: "text-emerald-700" };
  };

  const getBarColors = () => {
    if (score === 0) return ["bg-surface-container", "bg-surface-container", "bg-surface-container", "bg-surface-container"];
    if (score === 1) return ["bg-error", "bg-surface-container", "bg-surface-container", "bg-surface-container"];
    if (score === 2) return ["bg-amber-500", "bg-amber-500", "bg-surface-container", "bg-surface-container"];
    if (score === 3) return ["bg-emerald-500", "bg-emerald-500", "bg-emerald-500", "bg-surface-container"];
    return ["bg-emerald-600", "bg-emerald-600", "bg-emerald-600", "bg-emerald-600"];
  };

  const pwdMatchError = confirmPassword && password !== confirmPassword;

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setCpfError("");
    setFormError("");

    if (!nameValid) {
      alert("Por favor, preencha seu nome completo antes de prosseguir.");
      return;
    }

    if (!isValidCpf(cpf)) {
      setCpfError("CPF inválido. Verifique os dígitos informados para criar sua conta.");
      return;
    }

    if (ageValid === false) {
      alert("Abertura permitida apenas para maiores de 18 anos.");
      return;
    }

    if (password !== confirmPassword) {
      return;
    }

    setIsSubmitting(true);

    try {
      const newAccount = await accountService.createAccount({
        fullName,
        cpf,
        birthDate,
        email,
        phone,
        password,
      });

      setAccountCreated(newAccount);
      setIsSuccess(true);
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setFormError(err.problem.detail || err.problem.title || 'Falha ao processar abertura de conta.');
      } else {
        setFormError('Erro inesperado ao conectar ao serviço de abertura de contas.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  if (isSuccess && accountCreated) {
    return (
      <div className="space-y-6 animate-fadeIn">
        <div className="p-6 sm:p-8 rounded-2xl bg-emerald-500/10 border border-emerald-500/30 text-emerald-950 space-y-5">
          <div className="flex items-center gap-3.5">
            <div className="w-12 h-12 rounded-xl bg-emerald-600 text-white flex items-center justify-center shrink-0 shadow-md">
              <span className="material-symbols-outlined text-2xl" style={{ fontVariationSettings: "'FILL' 1" }}>
                check_circle
              </span>
            </div>
            <div>
              <span className="text-[11px] font-mono font-bold uppercase tracking-wider text-emerald-800">
                Conta Aprovada & Registrada
              </span>
              <h2 className="text-xl sm:text-2xl font-bold text-emerald-950">
                Parabéns, {fullName.split(' ')[0]}!
              </h2>
            </div>
          </div>

          <p className="text-body-md text-emerald-900 leading-relaxed">
            Sua conta digital Nexus Bank foi criada e já está pré-ativada em nossa infraestrutura soberana.
          </p>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 p-4 rounded-xl bg-surface-container-lowest border border-emerald-500/20 text-on-surface shadow-xs">
            <div>
              <p className="font-label-caps text-label-caps text-on-surface-variant uppercase font-semibold">Agência</p>
              <p className="font-code-lg text-code-lg font-bold text-on-surface">{accountCreated.agency}</p>
            </div>
            <div>
              <p className="font-label-caps text-label-caps text-on-surface-variant uppercase font-semibold">Conta Corrente</p>
              <p className="font-code-lg text-code-lg font-bold text-on-surface">{accountCreated.number}-{accountCreated.digit}</p>
            </div>
            <div>
              <p className="font-label-caps text-label-caps text-on-surface-variant uppercase font-semibold">Status</p>
              <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-emerald-700 bg-emerald-100 px-2 py-0.5 rounded-full mt-1">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-600 animate-pulse"></span>
                Ativa (Mock/Backend)
              </span>
            </div>
          </div>
        </div>

        <div className="space-y-3 pt-2">
          <Link
            href="/login"
            className="w-full h-12 px-6 bg-[#0e1c2f] text-white hover:bg-[#1a2d47] font-headline-sm text-headline-sm rounded-full transition-all duration-200 flex items-center justify-center gap-2 shadow-md hover:shadow-xl hover:shadow-black/15 font-bold group no-underline"
          >
            <span>Fazer Login na Conta</span>
            <span className="material-symbols-outlined text-xl no-underline group-hover:translate-x-1 transition-transform shrink-0">
              arrow_forward
            </span>
          </Link>

          <Link
            href="/"
            className="w-full h-11 px-6 bg-surface-container-low hover:bg-surface-container border border-surface-container hover:border-outline-variant/60 text-on-surface font-body-md text-body-md rounded-full transition-all duration-200 flex items-center justify-center gap-2 group no-underline shadow-2xs hover:shadow-xs"
          >
            <span className="material-symbols-outlined text-lg group-hover:-translate-x-1 transition-transform text-secondary no-underline shrink-0">
              arrow_back
            </span>
            <span className="group-hover:underline">Voltar para a página inicial</span>
          </Link>
        </div>
      </div>
    );
  }

  return (
    <form className="space-y-6" onSubmit={handleSubmit}>
      {/* Nome Completo */}
      <div className="space-y-1.5">
        <label className="block font-body-sm text-body-sm font-semibold text-on-surface" htmlFor="fullName">
          Nome Completo
        </label>
        <div className="relative">
          <input
            className="w-full bg-surface-container-low text-on-surface font-body-md text-body-md px-4 py-3 rounded-lg focus:outline-none focus:bg-surface-container-lowest focus:ring-2 focus:ring-primary-container transition-all"
            id="fullName"
            type="text"
            placeholder="Ex: Carlos Eduardo Silva"
            required
            value={fullName}
            onChange={handleNameChange}
          />
          {nameValid && (
            <span className="material-symbols-outlined absolute right-3 top-3 text-emerald-600 text-xl" style={{ fontVariationSettings: "'FILL' 1" }}>
              check_circle
            </span>
          )}
        </div>
        <p className="font-body-sm text-body-sm text-on-surface-variant">
          Informe seu nome completo conforme documento oficial de identificação.
        </p>
      </div>

      {/* CPF & Data de Nascimento */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <CpfInput
          id="cpf"
          name="cpf"
          label="CPF"
          variant="form"
          value={cpf}
          onChange={(val, valid) => {
            setCpf(val);
            setIsCpfValid(valid);
            if (cpfError) setCpfError("");
          }}
          errorMessage={cpfError}
          required
        />

        <div className="space-y-1.5">
          <label className="block font-body-sm text-body-sm font-semibold text-on-surface" htmlFor="birthDate">
            Data de Nascimento
          </label>
          <div className="relative">
            <input
              className="w-full bg-surface-container-low text-on-surface font-body-md text-body-md px-4 py-3 rounded-lg focus:outline-none focus:bg-surface-container-lowest focus:ring-2 focus:ring-primary-container transition-all"
              id="birthDate"
              type="date"
              required
              value={birthDate}
              onChange={handleBirthDateChange}
            />
          </div>
          <p className={`font-body-sm text-body-sm ${ageValid === true ? 'text-emerald-700' : ageValid === false ? 'text-error' : 'text-on-surface-variant'}`}>
            {ageMessage}
          </p>
        </div>
      </div>

      {/* E-mail & Celular */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div className="space-y-1.5">
          <label className="block font-body-sm text-body-sm font-semibold text-on-surface" htmlFor="email">
            E-mail
          </label>
          <input
            className="w-full bg-surface-container-low text-on-surface font-body-md text-body-md px-4 py-3 rounded-lg focus:outline-none focus:bg-surface-container-lowest focus:ring-2 focus:ring-primary-container transition-all"
            id="email"
            type="email"
            placeholder="nome@empresa.com.br"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
          <p className="font-body-sm text-body-sm text-on-surface-variant">
            Para avisos de conta e comprovantes bancários.
          </p>
        </div>

        <div className="space-y-1.5">
          <label className="block font-body-sm text-body-sm font-semibold text-on-surface" htmlFor="phone">
            Telefone Celular
          </label>
          <input
            className="w-full bg-surface-container-low text-on-surface font-code-md text-code-md px-4 py-3 rounded-lg focus:outline-none focus:bg-surface-container-lowest focus:ring-2 focus:ring-primary-container transition-all"
            id="phone"
            type="tel"
            maxLength={15}
            placeholder="(11) 98765-4321"
            required
            value={phone}
            onChange={handlePhoneChange}
          />
          <p className="font-body-sm text-body-sm text-on-surface-variant">
            Confirmação por SMS e autenticação instantânea.
          </p>
        </div>
      </div>

      {/* Senhas */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div className="space-y-1.5">
          <label className="block font-body-sm text-body-sm font-semibold text-on-surface" htmlFor="password">
            Senha de Acesso
          </label>
          <div className="relative">
            <input
              className="w-full bg-surface-container-low text-on-surface font-body-md text-body-md px-4 py-3 rounded-lg focus:outline-none focus:bg-surface-container-lowest focus:ring-2 focus:ring-primary-container transition-all"
              id="password"
              type="password"
              placeholder="Mínimo 8 caracteres"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>
        </div>

        <div className="space-y-1.5">
          <label className="block font-body-sm text-body-sm font-semibold text-on-surface" htmlFor="confirmPassword">
            Confirmar Senha
          </label>
          <div className="relative">
            <input
              className={`w-full bg-surface-container-low text-on-surface font-body-md text-body-md px-4 py-3 rounded-lg focus:outline-none focus:bg-surface-container-lowest focus:ring-2 focus:ring-primary-container transition-all ${pwdMatchError ? 'ring-2 ring-error focus:ring-error' : ''}`}
              id="confirmPassword"
              type="password"
              placeholder="Repita sua senha"
              required
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
            />
          </div>
        </div>
      </div>

      {/* Força da Senha */}
      <div className="bg-surface-container-low p-4 rounded-xl space-y-3">
        <div className="flex items-center justify-between">
          <span className="font-label-caps text-label-caps uppercase text-on-surface-variant">Segurança da Senha</span>
          <span className={`font-body-sm text-body-sm font-semibold ${getStrengthText().colorClass}`}>
            {getStrengthText().text}
          </span>
        </div>
        <div className="w-full h-1.5 bg-surface-container-highest rounded-full overflow-hidden flex">
          {getBarColors().map((bgClass, idx) => (
            <div key={idx} className={`h-full w-1/4 transition-all ${bgClass}`}></div>
          ))}
        </div>
        <div className="grid grid-cols-2 gap-2 pt-1 font-body-sm text-body-sm text-on-surface-variant">
          <div className={`flex items-center gap-1.5 ${hasLen ? 'text-emerald-700' : 'text-on-surface-variant'}`}>
            <span className={`material-symbols-outlined text-base ${hasLen ? 'text-emerald-600' : ''}`}>
              {hasLen ? 'check_circle' : 'radio_button_unchecked'}
            </span>
            <span>8+ caracteres</span>
          </div>
          <div className={`flex items-center gap-1.5 ${hasUpper ? 'text-emerald-700' : 'text-on-surface-variant'}`}>
            <span className={`material-symbols-outlined text-base ${hasUpper ? 'text-emerald-600' : ''}`}>
              {hasUpper ? 'check_circle' : 'radio_button_unchecked'}
            </span>
            <span>Letra maiúscula</span>
          </div>
          <div className={`flex items-center gap-1.5 ${hasNum ? 'text-emerald-700' : 'text-on-surface-variant'}`}>
            <span className={`material-symbols-outlined text-base ${hasNum ? 'text-emerald-600' : ''}`}>
              {hasNum ? 'check_circle' : 'radio_button_unchecked'}
            </span>
            <span>Pelo menos 1 número</span>
          </div>
          <div className={`flex items-center gap-1.5 ${hasSym ? 'text-emerald-700' : 'text-on-surface-variant'}`}>
            <span className={`material-symbols-outlined text-base ${hasSym ? 'text-emerald-600' : ''}`}>
              {hasSym ? 'check_circle' : 'radio_button_unchecked'}
            </span>
            <span>Símbolo (@, #, $, etc.)</span>
          </div>
        </div>
      </div>

      {/* Termos */}
      <div className="flex items-start gap-3 pt-2">
        <input
          className="mt-1 w-4 h-4 rounded text-primary-container focus:ring-0 cursor-pointer accent-black"
          id="terms"
          type="checkbox"
          required
        />
        <label className="font-body-sm text-body-sm text-on-surface-variant leading-relaxed cursor-pointer" htmlFor="terms">
          Declaro que li e concordo com os{" "}
          <Link className="text-on-surface font-semibold underline hover:text-secondary" href="/terms">Termos de Uso</Link>{" "}
          e a{" "}
          <Link className="text-on-surface font-semibold underline hover:text-secondary" href="/privacy">Política de Privacidade</Link>{" "}
          do Nexus Bank, autorizando o tratamento de dados cadastrais para gestão de conta digital.
        </label>
      </div>

      <div className="p-4 rounded-xl bg-surface-container flex items-start gap-3">
        <span className="material-symbols-outlined text-secondary text-xl mt-0.5">verified_user</span>
        <p className="font-body-sm text-body-sm text-on-surface leading-normal">
          Sua conta é criada instantaneamente com agência 0001 e passa pelo processo ágil de ativação automática em instantes.
        </p>
      </div>

      <div className="pt-4 space-y-4">
        {formError && (
          <div className="p-4 rounded-xl bg-error/10 border border-error/30 text-error text-body-md flex items-center gap-3 animate-fadeIn">
            <span className="material-symbols-outlined text-error shrink-0">error</span>
            <p className="text-sm font-medium">{formError}</p>
          </div>
        )}

        <button
          className={`w-full h-12 px-6 ${
            isSuccess ? 'bg-emerald-700 text-white' : 'bg-[#0e1c2f] text-white hover:bg-[#1a2d47]'
          } font-headline-sm text-headline-sm rounded-full transition-all duration-200 flex items-center justify-center gap-2 shadow-md hover:shadow-xl hover:shadow-black/15 active:scale-[0.99] cursor-pointer disabled:opacity-80 group font-bold`}
          disabled={isSubmitting || isSuccess}
          type="submit"
        >
          {isSubmitting ? (
            <>
              <span className="inline-block w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin shrink-0"></span>
              <span>Processando abertura...</span>
            </>
          ) : isSuccess ? (
            <>
              <span className="material-symbols-outlined text-xl no-underline">check</span>
              <span>Conta Criada com Sucesso!</span>
            </>
          ) : (
            <>
              <span>Concluir Abertura de Conta</span>
              <span className="material-symbols-outlined text-xl no-underline group-hover:translate-x-1 transition-transform shrink-0">
                arrow_forward
              </span>
            </>
          )}
        </button>
        <div className="flex items-center justify-center gap-3 text-center font-body-sm text-body-sm text-on-surface-variant pt-2 flex-wrap">
          <p>
            Já possui uma conta?{" "}
            <Link
              className="font-semibold text-on-surface hover:text-secondary transition-colors underline-offset-2 hover:underline ml-1"
              href="/login"
            >
              Faça login
            </Link>
          </p>
          <span className="hidden sm:inline text-surface-container-highest">•</span>
          <Link
            className="font-semibold text-on-surface-variant hover:text-on-surface transition-colors inline-flex items-center gap-1.5 group no-underline"
            href="/"
          >
            <span className="material-symbols-outlined text-[16px] group-hover:-translate-x-1 transition-transform text-secondary no-underline shrink-0">
              arrow_back
            </span>
            <span className="group-hover:underline">Voltar para a página inicial</span>
          </Link>
        </div>
      </div>
    </form>
  );
}
