# Nexus Bank — Frontend Web Banking & Core Ledger UI

Interface web institucional e de Web Banking corporativo do **Nexus Bank**, desenvolvida sobre arquitetura moderna de Core Bancário com conciliação contábil em tempo real, consistência eventual e princípio contábil de partidas dobradas (Double-Entry Ledger).

---

## 🛠️ Stack Tecnológica

- **Framework:** Next.js 16.4.3 (App Router com Turbopack, Partial Prerendering e Cache Components)
- **Biblioteca Base:** React 19.3.0
- **Linguagem:** TypeScript 5
- **Estilização & Design System:** Tailwind CSS v4 (`@theme`) integrado aos tokens do Material Design 3 Institucional
- **Tipografia:** `Inter` (UI e números executivos) e `JetBrains Mono` (identificadores, TxID e valores contábeis)
- **Ícones:** Material Symbols Outlined (Google Fonts)

---

## 🗺️ Mapa de Rotas e Telas Implementadas

| Rota | Tipo | Descrição |
| :--- | :--- | :--- |
| **`/`** | Pública | **Landing Page Institucional:** Hero com simulador de 102% CDI, ticker regulatório Bacen, recursos de conta soberana, omnichannel, FAQ e rodapé. |
| **`/login`** | Pública / Auth | **Acesso Seguro:** Autenticação de 2 colunas com validação matemática de CPF (RFB), senha eletrônica, feedback de ambiente seguro e redirecionamento automático para o dashboard. |
| **`/signup`** | Pública / Auth | **Abertura de Conta Digital:** Stepper guiado em 3 etapas com live-preview reativo do cartão Nexus Black refletindo o nome digitado em tempo real. |
| **`/dashboard`** | Autenticada | **Visão Geral & Saldo:** Card de saldo disponível amplo em tempo real, sub-métricas de saldo bloqueado e limites diários operacionais com barra de progresso, atalhos de transação rápida, histórico recente com status e cartão Nexus Black interativo. |
| **`/dashboard/extrato`** | Autenticada | **Extrato Oficial & Auditoria:** Histórico detalhado com filtros temporais rápidos (7, 15, 30, 90 dias, personalizado), busca textual de contraparte, filtros por natureza (Créditos, Débitos, Rejeitados), paginação e modal de comprovante com código de autenticação. |
| **`/dashboard/transferir`** | Autenticada | **Transferência Entre Contas:** Consulta em tempo real (Lookup) de contraparte com nome mascarado (conformidade LGPD/RFC), validação de limite diário e saldo, gaveta de protocolo de segurança da operação e recibo instantâneo. |
| **`/dashboard/operacoes`** | Autenticada | **Depositar & Sacar:** Interface unificada em abas com chips de valores rápidos (+R$ 100, +R$ 500, +R$ 1.000, +R$ 5.000, Máximo), simulador visual de saldo antes da confirmação e comprovante de terminal autorizado. |

---

## 🏛️ Princípios de Design e Engenharia Financeira

1. **Manipulação Monetária Estrita em Centavos (`long`):**
   - Todos os cálculos de saldo, débitos e créditos são executados exclusivamente em centavos inteiros (`utils/currency.ts`), eliminando imprecisões e anomalias de ponto flutuante (`float`/`double`).
2. **Linguagem Natural e Experiência Bancária Amigável (Zero Jargão Técnico):**
   - O design do frontend oculta complexidades internas de banco de dados e arquitetura. Termos como *idempotência*, *CQRS*, *partidas dobradas*, *shards*, *invariante contábil* e *hashes criptográficos* são apresentados ao usuário final como conceitos bancários amigáveis: **Protocolo da Operação**, **Extrato Oficial**, **Terminal Autorizado**, **Balanço Verificado** e **Código de Autenticação**.
3. **Idempotência Transparente no Cliente:**
   - Geração automática de identificador único de protocolo por tentativa de transferência, depósito ou saque, garantindo conformidade com a RFC 7231 e prevenindo duplicidades em falhas transitórias de rede sem sobrecarregar o usuário com jargões técnicos.
4. **Lookup e Privacidade (RFC & LGPD Compliance):**
   - Na transferência entre contas, o nome do destinatário é verificado e mascarado antes do envio (`Jo** Si*** dos Sa****`), conferindo segurança contra fraudes e conformidade com a LGPD.
5. **Shell Autenticado e Estado Reativo Independente:**
   - O `mockBankingStore.ts` e o hook `useBanking.ts` utilizam o padrão moderno `useSyncExternalStore` do React 19 para gerenciar as movimentações e atualizar em tempo real o saldo, limites e extrato em todas as abas e componentes da aplicação de forma determinística, permitindo o funcionamento 100% autônomo mesmo antes da disponibilidade dos serviços de backend.

---

## 🚀 Executando Localmente

### Pré-requisitos
- Node.js 18+ (recomendado Node.js 20+)
- npm ou pnpm

### Instalação e Execução
```bash
# 1. Instalar dependências
npm install

# 2. Executar o servidor de desenvolvimento
npm run dev

# 3. Executar o build de produção estática
npm run build

# 4. Iniciar o servidor de produção
npm run start
```

Acesse [http://localhost:3000](http://localhost:3000) no seu navegador.

---

## 🐳 Executando com Docker

### 1. Build e Execução Standalone (Imagem Otimizada ~80MB)
```bash
# Build da imagem de produção
docker build -t bank-ledger-web .

# Execução do container
docker run -d --name bank-ledger-web -p 3000:3000 bank-ledger-web

# Logs e status
docker logs -f bank-ledger-web
```

### 2. Execução Integrada via Docker Compose
Na raiz do repositório:
```bash
docker compose -f infra/docker/docker-compose.yml up -d web
```
