import React from 'react';

export type StepState = 'completed' | 'active' | 'upcoming';

export function computeStepState(stepNumber: number, currentStep: number): StepState {
  if (stepNumber < currentStep) return 'completed';
  if (stepNumber === currentStep) return 'active';
  return 'upcoming';
}

export interface StepperProps {
  currentStep?: number;
  onStepClick?: (step: number) => void;
}

const STEPS = [
  { id: 1, title: 'Dados Pessoais' },
  { id: 2, title: 'Contato & Acesso' },
  { id: 3, title: 'Segurança & Termos' },
];

export function Stepper({ currentStep = 1, onStepClick }: StepperProps) {
  return (
    <div className="py-8" aria-label="Progresso do cadastro">
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        {STEPS.map((step) => {
          const state = computeStepState(step.id, currentStep);
          const isCompleted = state === 'completed';
          const isActive = state === 'active';
          const isClickable = isCompleted && Boolean(onStepClick);

          return (
            <div
              key={step.id}
              role={isClickable ? 'button' : undefined}
              tabIndex={isClickable ? 0 : undefined}
              onClick={isClickable ? () => onStepClick?.(step.id) : undefined}
              onKeyDown={
                isClickable
                  ? (e) => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault();
                        onStepClick?.(step.id);
                      }
                    }
                  : undefined
              }
              aria-current={isActive ? 'step' : undefined}
              aria-label={
                isClickable
                  ? `Etapa ${step.id}: ${step.title} (Concluída - Clique para voltar)`
                  : `Etapa ${step.id}: ${step.title}`
              }
              className={`p-4 rounded-xl flex items-center gap-4 transition-all duration-200 ${
                isCompleted
                  ? `bg-surface-container-lowest border border-emerald-500/20 shadow-xs ${
                      isClickable
                        ? 'cursor-pointer hover:border-emerald-500/40 hover:bg-emerald-50/20 active:scale-[0.99]'
                        : ''
                    }`
                  : isActive
                  ? 'bg-surface-container-lowest border-2 border-primary/20 shadow-sm'
                  : 'bg-surface-container-low opacity-75'
              }`}
            >
              {isCompleted ? (
                <div className="w-9 h-9 rounded-full bg-emerald-600 text-white flex items-center justify-center font-headline-sm text-headline-sm shrink-0 shadow-xs">
                  <span className="material-symbols-outlined text-xl" style={{ fontVariationSettings: "'FILL' 1" }}>
                    check
                  </span>
                </div>
              ) : isActive ? (
                <div className="w-9 h-9 rounded-full bg-primary text-on-primary flex items-center justify-center font-headline-sm text-headline-sm font-bold shrink-0 shadow-xs">
                  {step.id}
                </div>
              ) : (
                <div className="w-9 h-9 rounded-full bg-surface-container-highest text-on-surface-variant flex items-center justify-center font-headline-sm text-headline-sm shrink-0">
                  {step.id}
                </div>
              )}

              <div className="min-w-0 flex-1">
                <span
                  className={`block font-label-caps text-label-caps uppercase ${
                    isCompleted
                      ? 'text-emerald-700 font-semibold'
                      : isActive
                      ? 'text-secondary font-semibold'
                      : 'text-on-surface-variant'
                  }`}
                >
                  {isCompleted ? 'Concluído' : isActive ? 'Passo Atual' : 'Próxima Etapa'}
                </span>
                <span
                  className={`font-headline-sm text-headline-sm truncate block ${
                    isActive ? 'text-on-surface font-bold' : isCompleted ? 'text-on-surface font-medium' : 'text-on-surface'
                  }`}
                >
                  {step.title}
                </span>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
