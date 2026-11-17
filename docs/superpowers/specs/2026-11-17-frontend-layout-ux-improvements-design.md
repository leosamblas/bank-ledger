# Design Doc: Nexus Bank Frontend Layout & UX/UI Usability Improvements

> **Autor:** Antigravity AI (com base em `@[plugin:ui-ux-pro-max]`)  
> **Data:** 17 de Novembro de 2026  
> **Status:** Proposta de Arquitetura & Design de Interface  
> **Alvo:** Aplicação Web Banking (`web/`)  

---

## 1. Contexto e Diagnóstico Atual

A interface do **Nexus Bank** (`web/`) foi construída com **Next.js 16 (App Router)**, **React 19** e **Tailwind CSS v4**, seguindo uma identidade visual institucional elegante ("Deep Navy", "Emerald" e "Gold"). O sistema já possui boa maturidade com layout desktop estruturado, simulação interativa na landing page, máscaras monetárias/CPF e integração com as APIs de core banking.

Contudo, uma auditoria aprofundada baseada nos padrões do **`ui-ux-pro-max`** (diretrizes de Acessibilidade WCAG 2.2 AA, Ergonomia Touch/Mobile, Feedback do Sistema e Consistência Visual) identificou oportunidades críticas de melhoria de usabilidade e qualidade.

---

## 2. Auditoria de Layout e Usabilidade por Tela

### 2.1 Landing Page (`/`)
* **Estado Atual:** Rica em seções institucionais (`HeroSection`, `TrustTicker`, `CoreFeatures`, `Simulator`, `OmnichannelSection`, `FaqSection`, `CtaSection`).
* **Pontos de Atenção:**
  1. *Header Fixo em Telas Pequenas:* O cabeçalho ocupa 80px a 96px fixos, restringindo a área útil vertical em smartphones.
  2. *Feedback do Simulador:* Os cálculos de rendimento variam conforme o slider/chips, mas leitores de tela não anunciam a atualização em tempo real (ausência de `aria-live="polite"` no valor resultante).
  3. *Acessibilidade do FAQ:* Abertura/fechamento com `<details>` e `<summary>` nativos, mas sem transição suave e com contraste sutil do ícone de expansão.

### 2.2 Autenticação (`/login`)
* **Estado Atual:** Formulário limpo com validação de CPF, controle de lockout por força bruta e feedback de tentativas restantes.
* **Pontos de Atenção & Melhorias Solicitadas:**
  1. *Alternância de Visibilidade de Senha (Padrão de Mercado):*
     - Botão de exibir/ocultar senha com área de toque mínima de $44 \times 44$ px.
     - Atributos dinâmicos de acessibilidade: `aria-label={showPassword ? "Ocultar senha" : "Exibir senha"}` e `aria-pressed={showPassword}`.
     - Ícones SVG claros (`visibility` / `visibility_off`).
  2. *Atributos de Preenchimento Automático e Contexto:*
     - `autoComplete="current-password"`, `spellCheck={false}`, `autoCorrect="off"`, `autoCapitalize="none"`.
     - Campo de CPF com `autoComplete="username"` e `inputMode="numeric"`.
  3. *Alertas de Erro e Lockout:* A contagem regressiva e os erros de autenticação com `role="alert"` e `aria-live="assertive"`.
  4. *Foco Automático Inteligente:* Foco automático no CPF quando não preenchido, ou foco imediato no campo de Senha caso o CPF já esteja salvo via "Lembrar CPF".

### 2.3 Abertura de Conta (`/signup`)
* **Estado Atual:** Stepper no topo (`Stepper.tsx`) e formulário com validação de idade ($\ge 18$ anos) e medidor de força de senha (`OpenAccountForm.tsx`).
* **Gargalos Críticos de Usabilidade & Melhorias Solicitadas:**
  1. *Alternância de Visibilidade de Senha e Confirmação de Senha:*
     - Os campos de "Senha de Acesso" e "Confirmar Senha" atualmente são forçados como `type="password"` sem botão de alternância.
     - Implementar botões de exibição/ocultação independentes em ambos os campos (`showPassword` e `showConfirmPassword`) com os ícones adequados e suporte de acessibilidade.
  2. *Ergonomia de Preenchimento Contextual por Campo:*
     - **Nome Completo:** `autoComplete="name"`, `autoCapitalize="words"`, `autoCorrect="off"`, hint contextual claro ("Nome completo conforme documento oficial de identificação - RG/CNH").
     - **CPF:** `autoComplete="username"`, `inputMode="numeric"`, validação Módulo 11 em tempo real com indicador visual.
     - **Data de Nascimento:** `autoComplete="bday"`, atributo `max` fixado em 18 anos atrás da data atual, cálculo dinâmico de elegibilidade.
     - **E-mail:** `autoComplete="email"`, `inputMode="email"`, `autoCapitalize="none"`, `spellCheck={false}`.
     - **Telefone:** `autoComplete="tel"`, `inputMode="tel"`, máscara reativa `(11) 98765-4321`.
     - **Senha & Confirmação:** `autoComplete="new-password"`, medidor visual de complexidade (tamanho, maiúscula, número, caractere especial) e validação em tempo real de correspondência de senhas.
  3. *Divulgação Progressiva (Progressive Disclosure):*
     - Sincronização real do `Stepper.tsx` com o `OpenAccountForm.tsx`, dividindo a jornada em 3 etapas claras (Dados Pessoais $\rightarrow$ Contato $\rightarrow$ Segurança & Termos).

### 2.4 Dashboard / Visão Geral (`/dashboard`)
* **Estado Atual:** Grid estruturado (65% painel esquerdo / 35% lateral), saldo consolidado, atalhos rápidos e cartão virtual Nexus Black.
* **Oportunidades de Alto Impacto:**
  1. *Privacidade de Saldo (Eye Toggle):* Ausência do recurso indispensável em apps bancários modernos: botão para ocultar/revelar valores financeiros em ambientes públicos (`••••••••` vs `R$ 15.420,50`).
  2. *Estados de Carregamento:* O refresh de dados usa apenas um spinner simples. A adoção de **Skeleton Screens (Shimmer)** reduz o tempo de percepção de espera (*Perceived Performance*).
  3. *Ações com `alert()` Nativo:* O sino de notificações na `BankingHeader` aciona `alert('Você não possui novas notificações...')`. O uso de alerts nativos bloqueantes quebra a imersão de um banco corporativo de alto nível.

### 2.5 Extrato Bancário (`/dashboard/statement`)
* **Estado Atual:** Filtros por categoria (Crédito, Débito, Transferência, etc.), busca textual, modal de recibo detalhado e exportação (CSV/OFX).
* **Pontos de Atenção:**
  1. *Feedback de Exportação:* Mensagens de sucesso de exportação usam estados temporários sem animação de saída suave.
  2. *Cópia Rápida de Identificadores:* Facilitar a cópia de UUIDs de transação (`transactionId`) e dados de contraparte com botão 1-clique e feedback "Copiado!".
  3. *Responsividade da Tabela em Telas Pequenas:* Em telas menores que 640px, a listagem de lançamentos pode sofrer compressão lateral.

### 2.6 Transferência entre Contas (`/dashboard/transfer`)
* **Estado Atual:** Pesquisa de favorecido com máscara de nome, chips rápidos de valor (+R$ 50, +R$ 100), validação de saldo e limites com dados técnicos colapsáveis.
* **Pontos de Atenção:**
  1. *Confirmação Segura:* O envio da transferência ocorre direto ao clicar em "Confirmar Transferência", sem um passo explícito de revisão/resumo prévio (Modal de Confirmação com favorecido, valor e saldo projetado).
  2. *Teclado Monetário:* Suporte aprimorado a digitação monetária em dispositivos touch.

### 2.7 Operações de Caixa (`/dashboard/operations`)
* **Estado Atual:** Abas alternadas para "Depositar" e "Sacar", limite diário, saldo projetado dinâmico e botão "Sacar Tudo".
* **Pontos de Atenção:**
  1. *Feedback Visual:* As abas de depósito/saque merecem transição suave e clara separação visual entre entrada de capital (Emerald) e retirada (Navy/Amber).

---

## 3. Diretrizes de Qualidade e Usabilidade (`ui-ux-pro-max`)

Com base nas prioridades de 1 a 10 do `ui-ux-pro-max`:

### P1. Acessibilidade (CRITICAL)
* **Contrast Ratio:** Garantir $\ge 4.5:1$ em todos os textos normais e $\ge 3:1$ em elementos gráficos e de foco.
* **Visible Focus:** Aneis de foco nítidos (`focus-visible:ring-2 focus-visible:ring-secondary focus-visible:outline-none`) em todos os botões, links e inputs.
* **Accessible Names:** Todos os botões que contêm apenas ícones (ex: olho de ocultar saldo, sino de notificação, botão fechar modal, copiar) devem possuir `aria-label` descritivo.
* **ARIA Live Regions:** Alertas de erro, contadores de tempo e atualizações de saldo marcados com `aria-live="polite"` ou `role="alert"`.

### P2. Touch & Interação Mobile (CRITICAL)
* **Target Size:** Área mínima de toque de **$44 \times 44$ px** para todos os botões e controles clicáveis.
* **Bottom Navigation Bar (Navegação Inferior):**
  * Em telas móveis (`< 768px`), o menu superior do Web Banking é ergonomicamente distante do polegar.
  * Implementação de uma barra de navegação inferior flutuante/fixada para acesso rápido aos 4 módulos centrais (Início, Transferir, Caixa, Extrato).
* **Safe Area Insets:** Respeitar `padding-bottom: env(safe-area-inset-bottom)` em barras fixas para evitar sobreposição com a barra inicial do iOS/Android.

### P3. Performance & Percepção Visual (HIGH)
* **Otimização de Ícones e Fontes:** Eliminar as chamadas duplicadas de Material Symbols no `<head>` do `layout.tsx` e `globals.css`.
* **Skeleton Shimmer Loaders:** Substituir telas cinzas ou textos "Carregando..." por blocos esqueletos pulsantes com as exatas dimensões dos cards de saldo e extrato.

### P4. Padrões de Formulários & Feedback (MEDIUM)
* **Onboarding Progressivo (Progressive Stepper):** Dividir a abertura de conta em 3 passos reais:
  1. *Passo 1:* Identificação & Titularidade (Nome Completo, CPF, Data de Nascimento).
  2. *Passo 2:* Contato & Acesso (E-mail, Telefone Celular).
  3. *Passo 3:* Credenciais & Segurança (Senha com medidor de força, Confirmação e Termos).
* **Toast System Acessível:** Criação de um provedor de feedback flutuante (Toasts de sucesso, aviso e erro) substituindo completamente o uso de `window.alert()`.
* **Privacy Toggle (Modo Privacidade):** Armazenar a preferência no `localStorage` para manter saldos ocultos ou visíveis entre sessões.

---

## 4. Arquitetura de Componentes Proposta

```text
web/src/
├── components/
│   ├── banking/
│   │   ├── BankingHeader.tsx         # Atualizado com suporte a perfil e logout sem alert
│   │   ├── BottomNav.tsx             # [NOVO] Barra de navegação inferior para mobile (< 768px)
│   │   ├── PrivacyToggle.tsx         # [NOVO] Botão de ocultar/exibir saldo com aria-label
│   │   └── Toast.tsx                 # [NOVO] Sistema de feedback acessível (Toasts flutuantes)
│   ├── forms/
│   │   └── OpenAccountForm.tsx       # Refatorado para suporte multi-etapa integrado ao Stepper
│   ├── ui/
│   │   ├── Stepper.tsx               # Atualizado para receber `currentStep`, `onStepClick` e estados ativos
│   │   ├── SkeletonCard.tsx          # [NOVO] Componente reutilizável de esqueleto pulsante
│   │   └── CopyButton.tsx            # [NOVO] Botão de cópia rápida 1-clique com tooltip "Copiado!"
```

---

## 5. Próximos Passos
Esta especificação serve como base para o plano de implementação detalhado em `docs/superpowers/plans/2026-11-17-frontend-layout-ux-improvements.md`.
