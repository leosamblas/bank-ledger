"use client";

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useBanking } from '@/hooks/useBanking';
import { authService } from '@/services';
import { mockBankingStore } from '@/services/mockBankingStore';

export function BankingHeader() {
  const pathname = usePathname();
  const router = useRouter();
  const { account } = useBanking();
  const [mobileOpen, setMobileOpen] = useState(false);

  useEffect(() => {
    if (authService.isAuthenticated()) {
      authService.getCurrentUserAccount()
        .then((data) => {
          if (data) {
            mockBankingStore.updateAccount({
              agency: data.agency,
              accountNumber: data.number,
              digit: data.digit,
              ...(data.holderName ? { holderName: data.holderName } : {}),
            });
          }
        })
        .catch((err) => {
          console.error('[BankingHeader] Falha ao carregar conta do usuário:', err);
        });
    }
  }, []);

  const userInitials = (account.holderName || 'CS')
    .split(' ')
    .filter(Boolean)
    .map((n) => n[0])
    .join('')
    .slice(0, 2)
    .toUpperCase();

  const navItems = [
    { label: 'Visão Geral', href: '/dashboard' },
    { label: 'Transferir', href: '/dashboard/transferir' },
    { label: 'Depositar & Sacar', href: '/dashboard/operacoes' },
    { label: 'Extrato', href: '/dashboard/extrato' },
  ];

  const handleLogout = () => {
    authService.logout();
    router.push('/login');
  };

  return (
    <header className="fixed top-0 w-full z-50 bg-surface-container-lowest shadow-[0_1px_8px_rgba(11,28,48,0.06)] border-b border-surface-container">
      <div className="h-20 w-full px-4 sm:px-6 lg:px-8 max-w-[1600px] mx-auto flex items-center justify-between gap-4">
        {/* Brand / Agency and Account Details */}
        <div className="flex items-center gap-4 flex-shrink-0">
          <Link href="/dashboard" className="flex items-center gap-2.5 no-underline group select-none">
            <div className="w-10 h-10 rounded-xl bg-primary text-on-primary flex items-center justify-center shadow-sm group-hover:scale-105 transition-transform">
              <span className="material-symbols-outlined text-[22px] text-emerald-400">
                account_balance
              </span>
            </div>
            <div className="flex flex-col">
              <span className="font-headline-sm text-headline-sm text-on-surface leading-none font-bold tracking-tight">
                Nexus Bank
              </span>
              <span className="font-code-sm text-[11px] text-on-surface-variant font-medium mt-1">
                Ag. {account.agency} · C/C {account.accountNumber}-{account.digit}
              </span>
            </div>
          </Link>
        </div>

        {/* Center Pill Navigation (Desktop) */}
        <div className="hidden xl:flex items-center justify-center">
          <nav
            aria-label="Navegação do Web Banking"
            className="bg-surface-container p-1 rounded-full border border-surface-container-high shadow-xs flex items-center gap-1"
          >
            {navItems.map((item) => {
              const isActive = pathname === item.href;
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={`px-4 py-1.5 font-body-md text-[13px] rounded-full transition-all no-underline ${
                    isActive
                      ? 'bg-surface-container-lowest text-on-surface font-semibold shadow-sm'
                      : 'text-on-surface-variant hover:text-on-surface hover:bg-surface-container-low font-medium'
                  }`}
                >
                  {item.label}
                </Link>
              );
            })}
          </nav>
        </div>

        {/* Right Section: Security, User Avatar & Logout */}
        <div className="flex items-center gap-2 sm:gap-3 flex-shrink-0">
          <div className="hidden md:flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-surface-container-low text-on-surface font-body-sm text-body-sm border border-surface-container">
            <span className="material-symbols-outlined text-[15px] text-emerald-600">lock</span>
            <span className="font-medium text-[12px]">Ambiente Seguro</span>
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 inline-block animate-pulse ml-0.5"></span>
          </div>

          <button
            aria-label="Notificações"
            className="p-2 text-on-surface-variant hover:text-on-surface hover:bg-surface-container transition-colors rounded-lg flex items-center justify-center relative cursor-pointer"
            type="button"
            onClick={() => alert('Você não possui novas notificações no momento.')}
          >
            <span className="material-symbols-outlined text-[20px]">notifications</span>
            <span className="w-2 h-2 rounded-full bg-error absolute top-2 right-2"></span>
          </button>

          {/* User Avatar & Name */}
          <div className="flex items-center gap-2 pl-2 border-l border-surface-container ml-1">
            <div className="w-9 h-9 rounded-full bg-[#0e1c2f] text-white flex items-center justify-center font-bold text-xs ring-2 ring-surface-container shadow-xs">
              {userInitials}
            </div>
            <div className="hidden lg:flex flex-col text-left">
              <span className="font-headline-sm text-headline-sm text-[13px] text-on-surface font-semibold leading-tight">
                {account.holderName}
              </span>
              <span className="font-label-caps text-label-caps text-on-surface-variant">
                {account.companyName}
              </span>
            </div>
          </div>

          {/* Logout Button */}
          <button
            aria-label="Encerrar Sessão"
            onClick={handleLogout}
            className="hidden sm:flex p-2 text-on-surface-variant hover:text-error hover:bg-error-container/40 transition-colors rounded-lg items-center justify-center cursor-pointer"
            title="Encerrar Sessão"
            type="button"
          >
            <span className="material-symbols-outlined text-[20px]">logout</span>
          </button>

          {/* Mobile Drawer Trigger */}
          <button
            aria-label="Menu Principal"
            className="xl:hidden p-2 text-on-surface-variant hover:text-on-surface hover:bg-surface-container rounded-lg flex items-center justify-center cursor-pointer"
            onClick={() => setMobileOpen(!mobileOpen)}
            type="button"
          >
            <span className="material-symbols-outlined text-[24px]">
              {mobileOpen ? 'close' : 'menu'}
            </span>
          </button>
        </div>
      </div>

      {/* Mobile Drawer */}
      {mobileOpen && (
        <div className="xl:hidden w-full bg-surface-container-lowest shadow-[0_4px_12px_rgba(11,28,48,0.08)] px-4 py-4 border-t border-surface-container animate-fadeIn">
          <div className="flex flex-col gap-1.5">
            {navItems.map((item) => {
              const isActive = pathname === item.href;
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  onClick={() => setMobileOpen(false)}
                  className={`px-4 py-2.5 font-body-md text-body-md rounded-lg no-underline transition-colors ${
                    isActive
                      ? 'bg-surface-container-high text-on-surface font-semibold'
                      : 'text-on-surface-variant hover:bg-surface-container-low'
                  }`}
                >
                  {item.label}
                </Link>
              );
            })}
            <div className="pt-3 mt-1 border-t border-surface-container flex items-center justify-between">
              <span className="text-body-sm text-on-surface-variant font-medium">
                Agência {account.agency} · C/C {account.accountNumber}-{account.digit}
              </span>
              <button
                onClick={handleLogout}
                className="text-error text-body-sm font-semibold flex items-center gap-1 cursor-pointer"
                type="button"
              >
                <span className="material-symbols-outlined text-[16px]">logout</span> Sair
              </button>
            </div>
          </div>
        </div>
      )}
    </header>
  );
}

