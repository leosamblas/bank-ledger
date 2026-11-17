"use client";

import React, { useState, useEffect, useCallback } from 'react';

export type ToastType = 'success' | 'info' | 'warning' | 'error';

export interface ToastItem {
  id: string;
  message: string;
  type: ToastType;
  duration?: number;
}

type ToastListener = (toast: ToastItem) => void;

let idCounter = 0;
function generateToastId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `toast-${Date.now()}-${++idCounter}`;
}

class ToastManager {
  private listeners = new Set<ToastListener>();

  subscribe(listener: ToastListener): () => void {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  notify(message: string, type: ToastType = 'info', duration: number = 4000): void {
    const toast: ToastItem = {
      id: generateToastId(),
      message,
      type,
      duration,
    };
    this.listeners.forEach((listener) => {
      try {
        listener(toast);
      } catch (err) {
        console.error('Error in toast listener:', err);
      }
    });
  }

  success(message: string, duration?: number): void {
    this.notify(message, 'success', duration);
  }

  info(message: string, duration?: number): void {
    this.notify(message, 'info', duration);
  }

  warning(message: string, duration?: number): void {
    this.notify(message, 'warning', duration);
  }

  error(message: string, duration?: number): void {
    this.notify(message, 'error', duration);
  }
}

export const toastManager = new ToastManager();

const toastConfig: Record<
  ToastType,
  {
    icon: string;
    iconClass: string;
    borderClass: string;
  }
> = {
  success: {
    icon: 'check_circle',
    iconClass: 'text-emerald-600',
    borderClass: 'border-l-4 border-l-emerald-600 border-surface-container',
  },
  info: {
    icon: 'info',
    iconClass: 'text-secondary',
    borderClass: 'border-l-4 border-l-secondary border-surface-container',
  },
  warning: {
    icon: 'warning',
    iconClass: 'text-amber-600',
    borderClass: 'border-l-4 border-l-amber-500 border-surface-container',
  },
  error: {
    icon: 'error',
    iconClass: 'text-error',
    borderClass: 'border-l-4 border-l-error border-surface-container',
  },
};

function ToastCard({
  toast,
  onDismiss,
}: {
  toast: ToastItem;
  onDismiss: (id: string) => void;
}) {
  const { id, message, type, duration = 4000 } = toast;
  const config = toastConfig[type] || toastConfig.info;

  useEffect(() => {
    if (duration <= 0) return;
    const timer = setTimeout(() => {
      onDismiss(id);
    }, duration);
    return () => clearTimeout(timer);
  }, [id, duration, onDismiss]);

  return (
    <div
      role={type === 'error' ? 'alert' : 'status'}
      aria-live={type === 'error' ? 'assertive' : 'polite'}
      className={`pointer-events-auto flex items-start gap-3 p-4 rounded-xl bg-surface-container-lowest border shadow-[0_4px_16px_rgba(11,28,48,0.12)] transition-all duration-300 ease-out animate-fadeIn ${config.borderClass}`}
    >
      <span
        className={`material-symbols-outlined text-[20px] flex-shrink-0 mt-0.5 ${config.iconClass}`}
        aria-hidden="true"
      >
        {config.icon}
      </span>
      <div className="flex-1 text-sm font-body-md text-on-surface leading-snug">
        {message}
      </div>
      <button
        type="button"
        onClick={() => onDismiss(id)}
        aria-label="Fechar notificação"
        className="min-w-[44px] min-h-[44px] -mr-2 -mt-2 flex items-center justify-center text-on-surface-variant hover:text-on-surface hover:bg-surface-container rounded-lg transition-colors cursor-pointer"
      >
        <span className="material-symbols-outlined text-[18px]" aria-hidden="true">
          close
        </span>
      </button>
    </div>
  );
}

export function ToastContainer() {
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  useEffect(() => {
    const unsubscribe = toastManager.subscribe((newToast) => {
      setToasts((prev) => [...prev, newToast]);
    });
    return () => {
      unsubscribe();
    };
  }, []);

  const handleDismiss = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  if (toasts.length === 0) {
    return null;
  }

  return (
    <aside
      aria-label="Notificações do sistema"
      className="fixed top-24 right-4 z-[9999] flex flex-col gap-2.5 max-w-sm w-full pointer-events-none px-4 sm:px-0"
    >
      {toasts.map((toast) => (
        <ToastCard key={toast.id} toast={toast} onDismiss={handleDismiss} />
      ))}
    </aside>
  );
}
