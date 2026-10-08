"use client";

import React, { useState } from 'react';
import Link from 'next/link';
import { Header } from '@/components/ui/Header';
import { Footer } from '@/components/ui/Footer';
import { CpfInput } from '@/components/ui/CpfInput';
import { isValidCpf } from '@/utils/cpf';
import { authService, TokenResponse, ApiError } from '@/services';

export default function LoginPage() {
  const [cpf, setCpf] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [rememberCpf, setRememberCpf] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [authSuccess, setAuthSuccess] = useState(false);
  const [authData, setAuthData] = useState<TokenResponse | null>(null);
  const [cpfError, setCpfError] = useState('');
  const [generalError, setGeneralError] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setCpfError('');
    setGeneralError('');

    if (!cpf.trim()) {
      setCpfError('Informe o CPF cadastrado.');
      return;
    }

    if (!isValidCpf(cpf)) {
      setCpfError('CPF inválido. Verifique os dígitos informados antes de prosseguir.');
      return;
    }

    if (!password) {
      setGeneralError('Informe sua senha de acesso.');
      return;
    }

    setIsLoading(true);

    try {
      const response = await authService.login({
        cpf,
        password,
      });

      setAuthData(response);
      setAuthSuccess(true);
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setGeneralError(err.problem.detail || err.problem.title || 'Falha na autenticação.');
      } else {
        setGeneralError('Erro inesperado ao conectar ao serviço de autenticação.');
      }
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <main className="w-full min-h-screen flex flex-col justify-between bg-surface">
      <div className="w-full max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pt-6 sm:pt-8 pb-6 flex-1 flex flex-col">
        {/* Standardized Header */}
        <Header currentPage="login" />

        {/* Breadcrumb Trail */}
        <nav aria-label="Navegação estrutural" className="flex items-center gap-2 text-body-sm text-on-surface-variant my-4">
          <Link
            href="/"
            className="hover:text-on-surface inline-flex items-center gap-1.5 transition-colors font-medium text-secondary hover:text-on-surface group no-underline"
          >
            <span className="material-symbols-outlined text-[17px] text-secondary group-hover:text-on-surface transition-colors shrink-0">
              home
            </span>
            <span className="group-hover:underline">Início</span>
          </Link>
          <span className="material-symbols-outlined text-[14px] text-surface-container-highest">chevron_right</span>
          <span className="text-on-surface font-semibold">Acesso à Conta (Login)</span>
        </nav>

        {/* Main Content: 2-Column Authentication Card */}
        <div className="w-full my-auto flex flex-col lg:flex-row items-stretch bg-surface-container-lowest rounded-2xl shadow-xl border border-surface-container overflow-hidden">
            {/* Left Column: Credential Verification Access */}
            <div className="w-full lg:w-1/2 p-space-lg md:p-space-xl flex flex-col justify-between">
              <div>
                {/* Header Security Pill */}
                <div className="flex items-center justify-between mb-space-md">
                  <span className="font-label-caps text-label-caps uppercase text-secondary tracking-wider font-semibold">
                    Autenticação Segura
                  </span>
                  <div className="flex items-center gap-1.5 px-3 py-1 bg-surface-container-low text-on-surface-variant rounded-full text-label-caps font-label-caps uppercase border border-surface-container">
                    <span
                      className="material-symbols-outlined text-[15px] text-emerald-600"
                      style={{ fontVariationSettings: "'FILL' 1" }}
                    >
                      verified_user
                    </span>
                    <span className="font-semibold">Sessão TLS 1.3</span>
                  </div>
                </div>

                {/* Title & Subtitle */}
                <div className="space-y-space-xs mb-space-lg">
                  <h1 className="font-headline-lg text-headline-lg text-on-surface font-bold tracking-tight">
                    Acesse sua conta Nexus Bank
                  </h1>
                  <p className="font-body-md text-body-md text-on-surface-variant">
                    Entre com seu CPF e senha de acesso cadastrada.
                  </p>
                </div>

            {authSuccess && authData && (
              <div className="mb-6 p-4 rounded-xl bg-emerald-500/10 border border-emerald-500/30 text-emerald-800 text-body-md flex items-start gap-3 animate-fadeIn">
                <span className="material-symbols-outlined text-emerald-600 mt-0.5">check_circle</span>
                <div className="space-y-1">
                  <p className="font-semibold text-emerald-950">
                    Bem-vindo(a), {authData.user?.fullName || 'Cliente Nexus'}!
                  </p>
                  <p className="text-xs text-emerald-700">
                    Sessão criptografada iniciada com sucesso. Token JWT ativo por {Math.floor(authData.expiresIn / 60)} min.
                  </p>
                  <div className="pt-1 flex items-center gap-2">
                    <span className="inline-flex items-center gap-1 text-[11px] font-mono font-medium px-2 py-0.5 rounded bg-emerald-500/20 text-emerald-900 border border-emerald-500/30">
                      <span className="w-1.5 h-1.5 rounded-full bg-emerald-600 animate-pulse"></span>
                      Sessão Ativa (Mock/Backend)
                    </span>
                  </div>
                </div>
              </div>
            )}

            {generalError && (
              <div className="mb-6 p-4 rounded-xl bg-error/10 border border-error/30 text-error text-body-md flex items-center gap-3 animate-fadeIn">
                <span className="material-symbols-outlined text-error shrink-0">error</span>
                <p className="text-sm font-medium">{generalError}</p>
              </div>
            )}

            {/* Authentication Form */}
            <form className="space-y-space-md" onSubmit={handleSubmit}>
              {/* CPF Input Field with Real-Time Validation */}
              <CpfInput
                id="cpfInput"
                name="cpf"
                value={cpf}
                onChange={(val) => {
                  setCpf(val);
                  if (cpfError) setCpfError('');
                }}
                variant="login"
                errorMessage={cpfError}
                required
                autoFocus
              />

              {/* Password Input Field */}
              <div className="space-y-1.5">
                <div className="flex items-center justify-between">
                  <label
                    className="block font-label-caps text-label-caps uppercase text-on-surface-variant tracking-wider font-semibold"
                    htmlFor="passwordInput"
                  >
                    Senha Eletrônica
                  </label>
                  <span className="font-body-sm text-body-sm text-on-surface-variant">
                    Mínimo de 8 caracteres
                  </span>
                </div>
                <div className="relative flex items-center">
                  <span className="absolute left-3.5 text-on-surface-variant material-symbols-outlined text-headline-sm pointer-events-none">
                    lock
                  </span>
                  <input
                    id="passwordInput"
                    name="password"
                    type={showPassword ? 'text' : 'password'}
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="Digite sua senha"
                    minLength={8}
                    required
                    autoComplete="current-password"
                    className="w-full h-12 pl-11 pr-11 bg-surface-container-low text-on-surface font-body-md text-body-md rounded-xl border border-transparent focus:border-secondary focus:outline-none focus:bg-surface-container transition-all"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    aria-label="Alternar exibição de senha"
                    className="absolute right-3.5 text-on-surface-variant hover:text-on-surface transition-colors cursor-pointer"
                  >
                    <span className="material-symbols-outlined text-headline-sm">
                      {showPassword ? 'visibility_off' : 'visibility'}
                    </span>
                  </button>
                </div>
              </div>

              {/* Options Row */}
              <div className="flex items-center justify-between pt-1">
                <label className="flex items-center gap-2 cursor-pointer select-none">
                  <input
                    id="rememberCpf"
                    type="checkbox"
                    checked={rememberCpf}
                    onChange={(e) => setRememberCpf(e.target.checked)}
                    className="w-4 h-4 rounded text-primary focus:ring-0 cursor-pointer accent-primary"
                  />
                  <span className="font-body-sm text-body-sm text-on-surface">
                    Lembrar meu CPF neste dispositivo
                  </span>
                </label>
                <a
                  href="#recuperar-senha"
                  onClick={(e) => {
                    e.preventDefault();
                    alert('Para recuperar sua senha, entre em contato com nosso SAC 24h: 0800 772 0100 ou valide através do aplicativo.');
                  }}
                  className="font-body-sm text-body-sm text-secondary hover:text-primary transition-colors hover:underline"
                >
                  Esqueceu a senha?
                </a>
              </div>

              {/* Security Policy Warning */}
              <div className="flex items-start gap-space-xs p-3.5 bg-surface-container-low rounded-xl border border-surface-container">
                <span
                  className="material-symbols-outlined text-body-lg text-secondary mt-0.5 shrink-0"
                  style={{ fontVariationSettings: "'FILL' 1" }}
                >
                  info
                </span>
                <p className="font-body-sm text-body-sm text-on-surface-variant leading-relaxed">
                  Por medidas de segurança, 5 tentativas incorretas consecutivas bloqueiam temporariamente o acesso aos canais digitais.
                </p>
              </div>

              {/* Submit Button */}
              <button
                type="submit"
                disabled={isLoading}
                id="submitButton"
                className="w-full h-12 bg-[#0e1c2f] text-white font-headline-sm text-headline-sm rounded-full flex items-center justify-center gap-2 hover:bg-[#1a2d47] active:scale-[0.99] transition-all duration-200 shadow-md hover:shadow-xl hover:shadow-black/15 cursor-pointer disabled:opacity-80 disabled:cursor-not-allowed font-semibold group"
              >
                {isLoading ? (
                  <>
                    <span className="material-symbols-outlined animate-spin text-[20px]">
                      progress_activity
                    </span>
                    <span>Autenticando...</span>
                  </>
                ) : (
                  <>
                    <span>Entrar na Conta</span>
                    <span className="material-symbols-outlined text-headline-sm no-underline group-hover:translate-x-1 transition-transform shrink-0">
                      arrow_forward
                    </span>
                  </>
                )}
              </button>
            </form>
          </div>

          {/* Registration & Return to Landing Page Links */}
          <div className="pt-space-md mt-space-md text-center border-t border-surface-container/60 space-y-3">
            <p className="font-body-md text-body-md text-on-surface-variant">
              Ainda não é cliente?{' '}
              <Link
                href="/signup"
                className="font-headline-sm text-body-md text-[#0e1c2f] hover:text-secondary transition-colors ml-1.5 inline-flex items-center gap-1.5 font-bold group no-underline"
              >
                <span className="group-hover:underline">Abra sua conta gratuita</span>
                <span className="material-symbols-outlined text-[16px] no-underline group-hover:translate-x-1 transition-transform shrink-0">
                  arrow_forward
                </span>
              </Link>
            </p>

            <div className="flex items-center justify-center gap-2 pt-1">
              <Link
                href="/"
                className="inline-flex items-center gap-2 text-body-sm text-on-surface-variant hover:text-on-surface font-medium transition-colors group no-underline"
              >
                <span className="material-symbols-outlined text-[16px] text-secondary no-underline group-hover:-translate-x-1 transition-transform shrink-0">
                  arrow_back
                </span>
                <span className="group-hover:underline">Voltar para a página inicial</span>
              </Link>
            </div>
          </div>
        </div>

        {/* Right Column: Institutional Showcase & Stability Overview */}
        <div className="w-full lg:w-1/2 bg-surface-container p-space-lg md:p-space-xl flex flex-col justify-between relative overflow-hidden">
          {/* Background visual accents */}
          <div className="absolute -right-20 -bottom-20 w-96 h-96 rounded-full bg-secondary-container/40 blur-3xl pointer-events-none" />
          <div className="absolute -left-12 -top-12 w-64 h-64 rounded-full bg-surface-variant/50 blur-2xl pointer-events-none" />

          {/* Upper Banner Content */}
          <div className="relative z-10 space-y-space-md">
            <div className="inline-flex items-center gap-2 px-3.5 py-1.5 bg-white/85 backdrop-blur-md rounded-full text-label-caps font-label-caps text-[#0b1c30] border border-white/70 shadow-xs">
              <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
              <span className="font-semibold uppercase tracking-wider text-[11px]">
                SISTEMA OPERACIONAL ATIVO
              </span>
            </div>
            <h2 className="text-2xl lg:text-[28px] text-[#0b1c30] max-w-md font-bold tracking-tight leading-snug">
              O controle do seu patrimônio com agilidade e clareza.
            </h2>
            <p className="text-[15px] text-[#3a526f] max-w-md leading-relaxed">
              A infraestrutura corporativa do Nexus Bank oferece liquidez em tempo real, gestão patrimonial consolidada e proteção contínua dos seus ativos.
            </p>
          </div>

          {/* Simulated Interface Card */}
          <div className="relative z-10 my-space-lg bg-white rounded-2xl p-5 shadow-[0_10px_25px_-5px_rgba(20,45,85,0.08)] border border-[#dbe6f6] space-y-space-sm">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                <div className="w-9 h-9 rounded-xl bg-[#eaf2fd] flex items-center justify-center text-[#0e1c2f] shadow-xs">
                  <span className="material-symbols-outlined text-[20px]">account_balance_wallet</span>
                </div>
                <div>
                  <p className="text-[11px] font-bold text-[#546b86] uppercase tracking-wider">
                    Saldo Disponível
                  </p>
                  <p className="font-mono text-xl sm:text-2xl font-bold text-[#0e1c2f] tracking-tight">
                    R$ 142.850,20
                  </p>
                </div>
              </div>
              <span className="px-2.5 py-1 rounded-md text-[11px] font-semibold bg-[#e6f0fd] text-[#1d4ed8] border border-[#d2e2fa]">
                Verificado
              </span>
            </div>

            <div className="space-y-1.5 pt-2">
              <div className="flex justify-between text-body-sm font-body-sm text-[#546b86]">
                <span>Limite Diário Operacional</span>
                <span className="font-mono text-[12px] text-[#0e1c2f] font-semibold">88% disponível</span>
              </div>
              <div className="w-full bg-[#e3eefc] rounded-full h-1.5 overflow-hidden mt-1.5">
                <div className="bg-[#0e1c2f] h-full w-[88%] rounded-full"></div>
              </div>
            </div>
          </div>

          {/* Value Proposition Feature Badges */}
          <div className="relative z-10 space-y-3">
            <div className="flex items-center gap-2.5 text-[#1b2f47]">
              <span
                className="material-symbols-outlined text-[20px] text-emerald-600"
                style={{ fontVariationSettings: "'FILL' 1" }}
              >
                check_circle
              </span>
              <span className="font-medium text-[14px]">
                Transferências instantâneas com liquidação direta
              </span>
            </div>
            <div className="flex items-center gap-2.5 text-[#1b2f47]">
              <span
                className="material-symbols-outlined text-[20px] text-emerald-600"
                style={{ fontVariationSettings: "'FILL' 1" }}
              >
                check_circle
              </span>
              <span className="font-medium text-[14px]">
                Extrato detalhado e relatórios em tempo real
              </span>
            </div>
            <div className="flex items-center gap-2.5 text-[#1b2f47]">
              <span
                className="material-symbols-outlined text-[20px] text-emerald-600"
                style={{ fontVariationSettings: "'FILL' 1" }}
              >
                check_circle
              </span>
              <span className="font-medium text-[14px]">
                Suporte corporativo prioritário 24/7
              </span>
            </div>
          </div>

          {/* Compliance & Regulatory Footer Notice */}
          <div className="relative z-10 pt-space-md text-right text-[#5f748f] font-mono text-[11px]">
            Instituição autorizada pelo Banco Central do Brasil
          </div>
        </div>
      </div>
    </div>

      {/* Standardized Global Footer */}
      <Footer />
    </main>
  );
}
