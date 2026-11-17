"use client";

import React from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';

export interface BottomNavItem {
  label: string;
  href: string;
  icon: string;
}

export const BOTTOM_NAV_ITEMS: BottomNavItem[] = [
  { label: 'Início', href: '/dashboard', icon: 'dashboard' },
  { label: 'Transferir', href: '/dashboard/transfer', icon: 'sync_alt' },
  { label: 'Caixa', href: '/dashboard/operations', icon: 'account_balance_wallet' },
  { label: 'Extrato', href: '/dashboard/statement', icon: 'receipt_long' },
];

export function BottomNav() {
  const pathname = usePathname();

  return (
    <nav
      aria-label="Navegação rápida mobile"
      className="fixed bottom-0 left-0 right-0 z-40 bg-surface-container-lowest/95 backdrop-blur-md border-t border-surface-container md:hidden pb-[env(safe-area-inset-bottom)]"
    >
      <div className="grid grid-cols-4 h-16 items-center max-w-lg mx-auto">
        {BOTTOM_NAV_ITEMS.map((item) => {
          const isActive =
            item.href === '/dashboard'
              ? pathname === '/dashboard'
              : pathname.startsWith(item.href);

          return (
            <Link
              key={item.href}
              href={item.href}
              aria-current={isActive ? 'page' : undefined}
              className={`min-h-[48px] min-w-[48px] flex flex-col items-center justify-center gap-0.5 text-[11px] relative no-underline transition-colors ${
                isActive
                  ? 'text-primary font-bold'
                  : 'text-on-surface-variant font-medium hover:text-on-surface'
              }`}
            >
              <span className="material-symbols-outlined text-[22px]">
                {item.icon}
              </span>
              <span>{item.label}</span>
              <span
                className={`w-1 h-1 rounded-full transition-all duration-200 ${
                  isActive ? 'bg-emerald-500 scale-100' : 'bg-transparent scale-0'
                }`}
                aria-hidden="true"
              />
            </Link>
          );
        })}
      </div>
    </nav>
  );
}

export default BottomNav;
