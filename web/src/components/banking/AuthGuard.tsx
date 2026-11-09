"use client";

import React, { useEffect, useSyncExternalStore } from 'react';
import { useRouter } from 'next/navigation';
import { authService } from '@/services';

interface AuthGuardProps {
  children: React.ReactNode;
}

function subscribe(callback: () => void) {
  if (typeof window === 'undefined') return () => {};
  window.addEventListener('storage', callback);
  return () => window.removeEventListener('storage', callback);
}

function getSnapshot() {
  return authService.isAuthenticated();
}

function getServerSnapshot() {
  return true;
}

export function AuthGuard({ children }: AuthGuardProps) {
  const router = useRouter();
  const isAuthenticated = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);

  useEffect(() => {
    if (!isAuthenticated) {
      router.replace('/login');
    }
  }, [isAuthenticated, router]);

  if (!isAuthenticated) {
    return (
      <div className="w-full min-h-[60vh] flex flex-col items-center justify-center gap-3.5">
        <div className="w-10 h-10 rounded-full border-3 border-secondary border-t-transparent animate-spin"></div>
        <p className="font-body-sm text-on-surface-variant font-medium animate-pulse">
          Validando credenciais de acesso seguro...
        </p>
      </div>
    );
  }

  return <>{children}</>;
}
