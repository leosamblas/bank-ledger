import React from 'react';

export function getSkeletonAriaAttributes(label = 'Carregando...') {
  return {
    'aria-busy': true as const,
    'aria-label': label,
    role: 'status',
  };
}

export function Skeleton({ className = '', ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={`animate-pulse rounded-lg bg-surface-container ${className}`}
      {...getSkeletonAriaAttributes()}
      {...props}
    />
  );
}

export function BalanceCardSkeleton() {
  return (
    <div
      className="w-full p-6 sm:p-8 rounded-2xl bg-surface-container-lowest border border-surface-container flex flex-col gap-6"
      {...getSkeletonAriaAttributes('Carregando informações financeiras')}
    >
      {/* Top row placeholder: title and badge */}
      <div className="flex items-center justify-between flex-wrap gap-2">
        <Skeleton className="h-5 w-48" />
        <Skeleton className="h-6 w-36 rounded-md" />
      </div>

      {/* Large balance placeholder and secondary balance placeholder */}
      <div className="flex flex-col gap-2">
        <Skeleton className="h-10 w-64 bg-surface-container" />
        <Skeleton className="h-5 w-40 bg-surface-container-low" />
      </div>

      {/* Sub-metrics placeholders */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-1">
        <div className="bg-surface-container-low p-4 rounded-xl border border-surface-container flex flex-col gap-2">
          <Skeleton className="h-4 w-36" />
          <Skeleton className="h-6 w-28" />
        </div>
        <div className="bg-surface-container-low p-4 rounded-xl border border-surface-container flex flex-col gap-2">
          <Skeleton className="h-4 w-40" />
          <Skeleton className="h-6 w-32" />
        </div>
      </div>

      {/* 4 quick actions placeholder buttons */}
      <div className="flex items-center gap-3 flex-wrap pt-2 border-t border-surface-container">
        <Skeleton className="h-11 w-44 rounded-xl" />
        <Skeleton className="h-11 w-40 rounded-xl" />
        <Skeleton className="h-11 w-36 rounded-xl" />
        <Skeleton className="h-11 w-44 rounded-xl sm:ml-auto" />
      </div>
    </div>
  );
}

export function StatementRowSkeleton() {
  return (
    <div
      className="p-4 rounded-xl bg-surface-container-lowest border border-surface-container flex items-center justify-between gap-4"
      {...getSkeletonAriaAttributes('Carregando transação')}
    >
      {/* Left: circle icon w-10 h-10 rounded-full bg-surface-container and two text lines (h-4 w-40, h-3 w-24) */}
      <div className="flex items-center gap-3">
        <Skeleton className="w-10 h-10 rounded-full bg-surface-container shrink-0" />
        <div className="flex flex-col gap-2">
          <Skeleton className="h-4 w-40" />
          <Skeleton className="h-3 w-24" />
        </div>
      </div>

      {/* Right: amount box h-5 w-24 and date line h-3 w-16 */}
      <div className="flex flex-col items-end gap-2">
        <Skeleton className="h-5 w-24" />
        <Skeleton className="h-3 w-16" />
      </div>
    </div>
  );
}
