# Remoção de Mocks da Área Logada, Transferências e Query Service CQRS Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminar 100% dos mocks da área logada no frontend Next.js, implementando a transferência entre contas no `transaction-service`/`ledger-service` (Slice 3), o microsserviço `query-service` CQRS com saldo inicial zero para novas contas e extrato oficial (Slice 4), e subindo toda a infraestrutura Docker pronta para teste de ponta a ponta.

**Architecture:** 
1. `transaction-service` expõe `POST /api/v1/transactions/transfers` com idempotência e JWT, publicando `PostJournalCommand` no Kafka.
2. `ledger-service` processa o comando atômico de transferência com lock pessimista ordenado por UUID (ADR 0002) debitando a origem e creditando o destino em partidas dobradas, emitindo `JournalPosted` e `EntryPosted`.
3. `query-service` (Porta 8083 / `query_db`) implementa projeções CQRS consumindo `AccountOpened` (garantindo saldo inicial 0) e `EntryPosted` (atualizando saldo e extrato) e expõe `GET /api/v1/queries/accounts/{id}/balance` e `/statement`.
4. `web` remove `mockBankingStore.ts`, conectando `useBanking.ts`, `/dashboard`, `/operacoes`, `/extrato` e `/transferir` exclusivamente aos endpoints da API através do Gateway Nginx (porta 8080).
5. Toda a infraestrutura é construída e iniciada via `docker-compose.yml` com containers em estado `healthy`.

**Tech Stack:** Java 25, Spring Boot 4.1.2, Spring Data JPA, Flyway, Apache Kafka (KRaft), Next.js 16 (App Router), TypeScript, Tailwind CSS, Docker Compose, Nginx.

**Spec:** `docs/plano_desenvolvimento_ledger.md` (Slices 3 e 4) e Documento de Design aprovado.

## Global Constraints
- Valores monetários estritamente inteiros em centavos (`bigint`/`long`), nunca ponto flutuante.
- Saldo inicial de qualquer nova conta cadastrada deve ser estritamente R$ 0,00 (0 centavos).
- Impossibilidade matemática de saldo negativo em contas de clientes.
- Validação estrita de `Idempotency-Key` (UUIDv4) em todas as operações de escrita financeira.
- Respostas de operações assíncronas com HTTP `202 Accepted` e cabeçalho `Location: /api/v1/transactions/{id}`.
- Governança de isolamento arquitetural hexagonal com cobertura de testes automatizados via Testcontainers.

## Review Focus
1. Registro de nova conta seguido de login deve renderizar saldo R$ 0,00 e extrato vazio (sem dados pré-populados de mock).
2. Transferência entre contas com saldo insuficiente no pagador deve ser rejeitada (`FAILED`) com o saldo preservado intacto.
3. Transferência bem-sucedida deve debitar a origem e creditar o destino de forma atômica no ledger e nas projeções CQRS.
4. Consulta de extrato no frontend deve listar lançamentos reais com data/hora, tipo, valor e contraparte.
5. Reenvio de requisições com a mesma `Idempotency-Key` não deve duplicar transferências, depósitos ou saques.

---

### Task 1: Transferência entre Contas no Backend (`transaction-service` e `ledger-service`)

**Files:**
- Create: `services/transaction-service/src/main/java/com/bankledger/transaction/adapters/in/rest/dto/TransferRequest.java`
- Create: `services/transaction-service/src/main/java/com/bankledger/transaction/application/port/in/CreateTransferCommand.java`
- Create: `services/transaction-service/src/main/java/com/bankledger/transaction/application/port/in/CreateTransferUseCase.java`
- Create: `services/transaction-service/src/main/java/com/bankledger/transaction/application/service/CreateTransferService.java`
- Modify: `services/transaction-service/src/main/java/com/bankledger/transaction/adapters/in/rest/TransactionController.java`
- Modify: `services/ledger-service/src/main/java/com/bankledger/ledger/domain/model/JournalType.java` (se necessário adicionar `TRANSFER`)
- Modify: `services/ledger-service/src/main/java/com/bankledger/ledger/application/service/PostJournalService.java`
- Test: `services/transaction-service/src/test/java/com/bankledger/transaction/integration/TransferIntegrationTest.java`
- Test: `services/ledger-service/src/test/java/com/bankledger/ledger/integration/LedgerTransferIntegrationTest.java`

**Interfaces:**
- Consumes: `IdempotencyValidatorUseCase`, `FinancialTransactionRepository`, `OutboxPublisher`
- Produces: `POST /api/v1/transactions/transfers` retornando `202 Accepted` com `Location` e `TransactionResponse`.

- [ ] **Step 1: Escrever teste de integração para transferência no `ledger-service`**
Criar `LedgerTransferIntegrationTest.java` com duas contas no Postgres (A com 50.000 centavos, B com 0 centavos). Validar que `PostJournalCommand` com `type = TRANSFER` debita 20.000 de A e credita 20.000 em B, resultando em soma zero de débitos e créditos.

- [ ] **Step 2: Executar teste do `ledger-service` e confirmar falha**
Executar `./gradlew :ledger-service:test --tests LedgerTransferIntegrationTest`.

- [ ] **Step 3: Implementar execução contábil de transferência no `ledger-service`**
Em `PostJournalService.java`, tratar `type.equalsIgnoreCase("TRANSFER")`: ordenar `sourceAccountId` e `destinationAccountId` por UUID para evitar deadlocks (ADR 0002), obter lock pessimista `findByIdForUpdate`, validar saldo da origem $\ge$ valor, debitar origem, creditar destino, persistir journal com status `POSTED` e gerar `EntryPosted`.

- [ ] **Step 4: Executar teste do `ledger-service` e confirmar sucesso**
Executar `./gradlew :ledger-service:test --tests LedgerTransferIntegrationTest`.

- [ ] **Step 5: Escrever teste de integração para endpoint `/api/v1/transactions/transfers`**
Criar `TransferIntegrationTest.java` no `transaction-service`, validando resposta `202 Accepted`, cabeçalho `Location`, validação de payload inválido (saldo negativo, conta de destino nula) e idempotência.

- [ ] **Step 6: Implementar `TransferRequest`, `CreateTransferUseCase` e endpoint no `TransactionController`**
Criar `TransferRequest(UUID destinationAccountId, Long amount, String description)`. Implementar `CreateTransferService` persistindo `FinancialTransaction` com tipo `TRANSFER` e publicando `PostJournalCommand` no outbox. Adicionar `@PostMapping("/transfers")` no `TransactionController`.

- [ ] **Step 7: Executar testes de integração e validar aprovação**
Executar `./gradlew :transaction-service:test`.

- [ ] **Step 8: Commit**
```bash
git add services/ledger-service/ services/transaction-service/
git commit -m "feat(transaction): implement transfer endpoint and double-entry ledger execution"
```

---

### Task 2: Implementação do `query-service` CQRS e Saldo Inicial Zero

**Files:**
- Create: `services/query-service/src/main/resources/db/migration/V1__init_query_schema.sql`
- Create: `services/query-service/src/main/java/com/bankledger/query/domain/model/AccountBalance.java`
- Create: `services/query-service/src/main/java/com/bankledger/query/domain/model/StatementEntry.java`
- Create: `services/query-service/src/main/java/com/bankledger/query/adapters/out/persistence/AccountBalanceEntity.java`
- Create: `services/query-service/src/main/java/com/bankledger/query/adapters/out/persistence/StatementEntryEntity.java`
- Create: `services/query-service/src/main/java/com/bankledger/query/adapters/out/persistence/AccountBalanceRepository.java`
- Create: `services/query-service/src/main/java/com/bankledger/query/adapters/out/persistence/StatementEntryRepository.java`
- Create: `services/query-service/src/main/java/com/bankledger/query/adapters/in/kafka/QueryEventConsumer.java`
- Create: `services/query-service/src/main/java/com/bankledger/query/adapters/in/rest/QueryController.java`
- Create: `services/query-service/src/main/java/com/bankledger/query/adapters/in/rest/dto/BalanceResponse.java`
- Create: `services/query-service/src/main/java/com/bankledger/query/adapters/in/rest/dto/StatementResponse.java`
- Create: `services/query-service/src/main/java/com/bankledger/query/config/SecurityConfig.java`
- Test: `services/query-service/src/test/java/com/bankledger/query/integration/QueryServiceIntegrationTest.java`

**Interfaces:**
- Consumes: Tópicos Kafka `account.events.v1` (`AccountOpened`), `ledger.entries.v1` (`EntryPosted`), `transaction.events.v1`
- Produces: 
  - `GET /api/v1/queries/accounts/{accountId}/balance` retornando `BalanceResponse`
  - `GET /api/v1/queries/accounts/{accountId}/statement` retornando `StatementResponse`

- [ ] **Step 1: Criar migração Flyway `V1__init_query_schema.sql`**
Criar tabelas `account_balance` e `statement_entry` no banco `query_db` com índices para busca por conta e ordenação temporal decrescente.

- [ ] **Step 2: Escrever teste de integração para o `query-service` com Testcontainers**
Criar `QueryServiceIntegrationTest.java`:
1. Quando receber evento `AccountOpened`, cria registro em `account_balance` com saldo estritamente `0` centavos.
2. Quando receber evento `EntryPosted` (crédito de 15.000 centavos), atualiza `account_balance` para 15.000 e insere linha em `statement_entry`.
3. Validar que `GET /api/v1/queries/accounts/{accountId}/balance` responde HTTP 200 com `availableCents: 15000`.
4. Validar que `GET /api/v1/queries/accounts/{accountId}/statement` responde com a entrada correspondente.

- [ ] **Step 3: Executar teste do `query-service` e confirmar falha**
Executar `./gradlew :query-service:test`.

- [ ] **Step 4: Implementar entidades, repositórios, consumidores Kafka e Controller REST**
1. Entidades JPA `AccountBalanceEntity` e `StatementEntryEntity`.
2. `QueryEventConsumer` processando mensagens dos tópicos com `InboxHandler` idempotente.
3. `QueryController` expondo `/api/v1/queries/accounts/{accountId}/balance` e `/api/v1/queries/accounts/{accountId}/statement`.
4. `SecurityConfig` configurando autenticação stateless ou pública/gateway-scoped com validação de JWT.

- [ ] **Step 5: Executar teste do `query-service` e validar aprovação**
Executar `./gradlew :query-service:test`.

- [ ] **Step 6: Commit**
```bash
git add services/query-service/
git commit -m "feat(query): implement CQRS query service with initial zero balance and statement projections"
```

---

### Task 3: Front-end: Clientes de API, Remoção do `mockBankingStore` e Refatoração do `useBanking`

**Files:**
- Create: `web/src/services/queryService.ts`
- Modify: `web/src/services/transactionService.ts`
- Modify: `web/src/services/accountService.ts`
- Modify: `web/src/services/index.ts`
- Modify: `web/src/hooks/useBanking.ts`
- Delete: `web/src/services/mockBankingStore.ts`
- Test: `web/src/services/__tests__/transactionService.test.ts`

**Interfaces:**
- Consumes: `/api/v1/accounts/me`, `/api/v1/queries/accounts/{id}/balance`, `/api/v1/queries/accounts/{id}/statement`, `/api/v1/transactions/transfers`
- Produces: `useBanking()` provendo `{ account, balance, statement, transfer, deposit, withdraw, lookup, refresh, isLoading }` 100% integrados à API.

- [ ] **Step 1: Criar `queryService.ts` com métodos `getBalance` e `getStatement`**
Consumir `/queries/accounts/{accountId}/balance` e `/queries/accounts/{accountId}/statement` usando o `request` utilitário de `apiClient.ts` com token JWT do `tokenStorage`.

- [ ] **Step 2: Atualizar `transactionService.ts` para suportar `createTransfer` e remover fallbacks mock**
Adicionar `createTransfer(destinationAccountId, amountCents, idempotencyKey, description)` disparando `POST /transactions/transfers`. Remover mutações e fallbacks para `mockBankingStore`.

- [ ] **Step 3: Refatorar `useBanking.ts` para integração assíncrona real**
Buscar os dados reais da conta autenticada via `accountService.getCurrentUserAccount()`, em seguida carregar saldo e extrato via `queryService`. Prover função `refresh()` para re-sincronizar dados após operações financeiras. Garantir que, se a conta for recém-aberta, o saldo seja `0` e o extrato seja vazio.

- [ ] **Step 4: Deletar `mockBankingStore.ts` e atualizar testes unitários do front**
Deletar `web/src/services/mockBankingStore.ts` e adaptar `transactionService.test.ts` para mockar `fetch` nativo em vez de store local.

- [ ] **Step 5: Executar testes/build do frontend**
Executar `npm run build` na pasta `web` e verificar que não há imports quebrados de `mockBankingStore`.

- [ ] **Step 6: Commit**
```bash
git add web/
git commit -m "refactor(web): remove mockBankingStore and connect useBanking to real backend services"
```

---

### Task 4: Front-end: Padronização das Rotas em Inglês e Integração das Telas Logadas (`/dashboard`, `/operations`, `/statement`, `/transfer`)

**Files:**
- Create/Move: `web/src/app/dashboard/operations/page.tsx` (a partir de `operacoes/page.tsx`)
- Create/Move: `web/src/app/dashboard/statement/page.tsx` (a partir de `extrato/page.tsx`)
- Create/Move: `web/src/app/dashboard/transfer/page.tsx` (a partir de `transferir/page.tsx`)
- Delete: `web/src/app/dashboard/operacoes/page.tsx`
- Delete: `web/src/app/dashboard/extrato/page.tsx`
- Delete: `web/src/app/dashboard/transferir/page.tsx`
- Modify: `web/src/app/dashboard/page.tsx`
- Modify: `web/src/components/banking/BankingHeader.tsx`
- Modify: `web/next.config.ts` (adicionar redirects opcionais para manter compatibilidade retroativa)

**Interfaces:**
- Consumes: Hook `useBanking` atualizado, sem nenhum resquício de mock.
- Produces: Rotas padronizadas em inglês (`/dashboard`, `/dashboard/operations`, `/dashboard/transfer`, `/dashboard/statement`) e links do header/dashboard consistentes.

- [ ] **Step 1: Renomear e padronizar as rotas do frontend em inglês**
Mover as pastas de páginas no Next.js:
- `operacoes` -> `operations`
- `transferir` -> `transfer`
- `extrato` -> `statement`
Configurar redirects em `next.config.ts` para que `/dashboard/operacoes` redirecione para `/dashboard/operations`, `/dashboard/transferir` para `/dashboard/transfer` e `/dashboard/extrato` para `/dashboard/statement`.

- [ ] **Step 2: Atualizar `web/src/components/banking/BankingHeader.tsx` e `web/src/app/dashboard/page.tsx`**
Atualizar `navItems` no `BankingHeader` para apontar para as rotas em inglês:
- `{ label: 'Visão Geral', href: '/dashboard' }`
- `{ label: 'Transferir', href: '/dashboard/transfer' }`
- `{ label: 'Depositar & Sacar', href: '/dashboard/operations' }`
- `{ label: 'Extrato', href: '/dashboard/statement' }`
Em `dashboard/page.tsx`, atualizar os botões de ação rápida e vincular card de saldo, status, limites e transações recentes aos dados da API via `useBanking()`.

- [ ] **Step 3: Atualizar `web/src/app/dashboard/operations/page.tsx`**
Na submissão de Depósito ou Saque:
1. Chamar `transactionService.createDeposit` ou `createWithdrawal`.
2. Executar polling com `transactionService.pollTransactionUntilResolved`.
3. Ao completar (`COMPLETED`), acionar `refresh()` para sincronizar saldo e extrato reais da API e exibir comprovante oficial.
4. Tratar eventuais falhas (`FAILED` / `INSUFFICIENT_FUNDS`) exibindo a mensagem retornada pelo backend.

- [ ] **Step 4: Atualizar `web/src/app/dashboard/transfer/page.tsx`**
1. Na digitação da agência e conta destino, disparar busca via `accountService.lookupAccount(agency, number)`.
2. Ao confirmar, chamar `transactionService.createTransfer(...)` com o `accountId` da contraparte.
3. Executar polling até `COMPLETED` e recarregar saldo via `refresh()`.

- [ ] **Step 5: Atualizar `web/src/app/dashboard/statement/page.tsx`**
Exibir a lista real de lançamentos contábeis retornada por `useBanking`, com filtros de período/tipo operando sobre os itens reais da API.

- [ ] **Step 6: Executar build de produção do Next.js**
Na pasta `web`, rodar `npm run build` e confirmar que a compilação do Next.js 16 ocorre com 0 erros.

- [ ] **Step 7: Commit**
```bash
git add web/
git commit -m "feat(web): standardize routes to english and wire dashboard, operations, transfer and statement to real API"
```

---

### Task 5: Validação da Infraestrutura Docker e Teste Ponta a Ponta

**Files:**
- Modify: `infra/docker/nginx/nginx.conf` (se necessário ajustar rotas)
- Modify: `infra/docker/docker-compose.yml` (garantir build e orquestração do `query-service`)

**Interfaces:**
- Produces: Ambiente completo em containers rodando e acessível na porta 3000 (Web) e 8080 (Gateway).

- [ ] **Step 1: Validar build de todos os microsserviços via Gradle**
Rodar `.\gradlew.bat test` na raiz e certificar que todos os 4 microsserviços passam nos testes.

- [ ] **Step 2: Subir a stack completa via Docker Compose**
Executar:
```powershell
docker compose -f infra/docker/docker-compose.yml up --build -d
```

- [ ] **Step 3: Verificar saúde dos containers**
Executar `docker compose -f infra/docker/docker-compose.yml ps` e confirmar que todos os serviços (`postgres`, `kafka`, `account-service`, `ledger-service`, `transaction-service`, `query-service`, `gateway`, `web`, `otel-lgtm`) estão saudáveis (`healthy` ou `Up`).

- [ ] **Step 4: Executar teste ponta a ponta de validação via API / Health**
Verificar endpoints principais:
* `http://localhost:8080/health` -> 200 OK
* `http://localhost:8080/api/v1/auth`
* `http://localhost:3000` -> 200 OK

- [ ] **Step 5: Commit final e relatório de prontidão**
```bash
git add infra/
git commit -m "chore(infra): orchestrate all microservices including query-service for full e2e execution"
```
