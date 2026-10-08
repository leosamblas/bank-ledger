"use client";

import React, { useState } from 'react';

interface FaqItem {
  id: string;
  question: string;
  answer: string;
}

const FAQS: FaqItem[] = [
  {
    id: 'abrir-conta',
    question: 'Como faço para abrir minha conta digital?',
    answer:
      'O processo é 100% digital e leva cerca de 3 minutos. Basta clicar em "Abrir Conta", preencher seus dados básicos (Nome, CPF, Telefone e Data de Nascimento) e realizar a validação segura em seu smartphone ou computador. Sem filas e sem envio de documentos físicos.',
  },
  {
    id: 'tarifas',
    question: 'Existe alguma tarifa de manutenção ou surpresas na fatura?',
    answer:
      'Absolutamente nenhuma. Não cobramos mensalidade de conta corrente, taxas de transferência via Pix ou TED e nem anuidade no Cartão Nexus Black. Nosso modelo operacional baseia-se em eficiência de custos e liquidação institucional.',
  },
  {
    id: 'fgc',
    question: 'Como funciona a garantia do Fundo Garantidor de Créditos (FGC)?',
    answer:
      'O Nexus Bank é uma instituição participante ordinária do FGC (Fundo Garantidor de Créditos). Todos os depósitos à vista e aplicações em CDB emitidas pelo banco contam com garantia legal de até R$ 250.000,00 por CPF ou CNPJ.',
  },
  {
    id: 'rentabilidade',
    question: 'Quanto meu saldo rende parado na conta corrente?',
    answer:
      'Todo saldo em conta rende automaticamente 102% do CDI todos os dias úteis, com liquidez diária imediata. Você pode utilizar o dinheiro a qualquer instante via Pix ou cartão de débito sem perder os rendimentos já acumulados.',
  },
  {
    id: 'cartao-black',
    question: 'Como solicito o Cartão Nexus Black físico e virtual?',
    answer:
      'Ao concluir a abertura da sua conta, o cartão virtual é gerado instantaneamente no aplicativo para compras online. O cartão físico em metal escovado é emitido e enviado ao seu endereço cadastrado com frete gratuito.',
  },
  {
    id: 'pj-pf',
    question: 'Posso movimentar contas de Pessoa Jurídica (PJ) e Pessoa Física no mesmo app?',
    answer:
      'Sim! Nosso Internet Banking conta com o recurso de comutação ágil de contas (Single Sign-On Corporativo), permitindo que você alterne instantaneamente entre seu perfil PF e suas empresas sem necessidade de novo login.',
  },
];

export function FaqSection() {
  const [openId, setOpenId] = useState<string | null>('abrir-conta');

  const toggle = (id: string) => {
    setOpenId((prev) => (prev === id ? null : id));
  };

  return (
    <section className="w-full py-space-xl bg-surface-container-low scroll-mt-24 md:scroll-mt-28" id="faq">
      <div className="w-full px-margin mx-auto max-w-4xl">
        {/* Section Header */}
        <div className="text-center mb-space-lg">
          <span className="font-label-caps text-label-caps uppercase tracking-wider text-secondary font-semibold">
            Transparência Regulada
          </span>
          <h2 className="font-headline-lg text-headline-lg text-on-surface font-bold tracking-tight mt-space-xs">
            Perguntas Frequentes
          </h2>
          <p className="font-body-md text-body-md text-on-surface-variant max-w-lg mx-auto mt-1">
            Respostas claras para as principais dúvidas sobre conta digital, segurança e serviços Nexus.
          </p>
        </div>

        {/* Clean Accordion List */}
        <div className="flex flex-col gap-3">
          {FAQS.map((faq) => {
            const isOpen = openId === faq.id;
            return (
              <div
                key={faq.id}
                className="bg-surface-container-lowest rounded-xl shadow-xs border border-surface-container hover:border-surface-container-high transition-all overflow-hidden"
              >
                <button
                  type="button"
                  onClick={() => toggle(faq.id)}
                  className="w-full p-space-md sm:p-5 flex items-center justify-between gap-4 text-left cursor-pointer focus:outline-none"
                  aria-expanded={isOpen}
                >
                  <span className="font-headline-sm text-headline-sm text-on-surface font-semibold text-[15px] sm:text-[16px]">
                    {faq.question}
                  </span>
                  <span
                    className={`material-symbols-outlined text-[20px] text-on-surface-variant flex-shrink-0 transition-transform duration-200 ${
                      isOpen ? 'rotate-180 text-on-surface' : ''
                    }`}
                  >
                    expand_more
                  </span>
                </button>

                {isOpen && (
                  <div className="px-space-md sm:px-5 pb-space-md sm:pb-5 pt-0 text-on-surface-variant font-body-md text-body-md leading-relaxed border-t border-surface-container/60">
                    <p className="pt-3">{faq.answer}</p>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
