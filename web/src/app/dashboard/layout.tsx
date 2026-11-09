import React from 'react';
import { BankingHeader } from '@/components/banking/BankingHeader';
import { BankingFooter } from '@/components/banking/BankingFooter';
import { AuthGuard } from '@/components/banking/AuthGuard';

export const metadata = {
  title: 'Web Banking · Nexus Bank',
  description: 'Plataforma de gestão financeira corporativa com conciliação contábil em tempo real.',
};

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="min-h-screen flex flex-col justify-between bg-background text-on-surface antialiased">
      <BankingHeader />
      <main className="w-full pt-24 md:pt-28 pb-12 flex-1">
        <AuthGuard>{children}</AuthGuard>
      </main>
      <BankingFooter />
    </div>
  );
}

