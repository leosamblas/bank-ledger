"use client";

import React, { useState } from 'react';
import { Header } from '@/components/ui/Header';
import { Stepper } from '@/components/ui/Stepper';
import { Footer } from '@/components/ui/Footer';
import { NexusBlackCardPreview } from '@/components/ui/NexusBlackCardPreview';
import { Highlights } from '@/components/ui/Highlights';
import { TrustBanner } from '@/components/ui/TrustBanner';
import { OpenAccountForm } from '@/components/forms/OpenAccountForm';

export default function AbrirConta() {
  const [cardHolderName, setCardHolderName] = useState("");
  const [currentStep, setCurrentStep] = useState(1);

  return (
    <main className="w-full min-h-screen flex flex-col justify-between bg-surface">
      <div className="w-full max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pt-6 sm:pt-8 pb-8 flex-1 flex flex-col">
        <Header currentPage="signup" />

        <Stepper currentStep={currentStep} onStepClick={setCurrentStep} />

        {/* Main Content 2-Column Grid */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 lg:gap-12 items-start my-auto py-2">
          {/* Left Column: Form */}
          <div className="lg:col-span-7 bg-surface-container-lowest p-6 sm:p-10 rounded-2xl shadow-sm">
            <div className="mb-8">
              <span className="font-label-caps text-label-caps uppercase text-secondary tracking-wider block mb-2">
                Abertura Simplificada
              </span>
              <h1 className="font-headline-lg text-headline-lg text-on-surface tracking-tight mb-2">
                Abra sua conta digital Nexus Bank em poucos minutos
              </h1>
              <p className="font-body-lg text-body-lg text-on-surface-variant">
                Sem tarifas ocultas, 100% digital e com total segurança regulada pelo Banco Central do Brasil.
              </p>
            </div>

            <OpenAccountForm
              onNameChange={setCardHolderName}
              currentStep={currentStep}
              onStepChange={setCurrentStep}
            />
          </div>

          {/* Right Column: Visual Showcase & Trust Banner */}
          <div className="lg:col-span-5 space-y-6">
            <NexusBlackCardPreview cardHolderName={cardHolderName} />
            <Highlights />
            <TrustBanner />
          </div>
        </div>
      </div>

      {/* Standardized Global Footer */}
      <Footer />
    </main>
  );
}
