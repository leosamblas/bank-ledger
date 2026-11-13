# Slice 2 (Core Ledger & Transactions) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Construir o núcleo financeiro de partidas dobradas (`ledger-service`) e o serviço de operações de borda (`transaction-service`) em Java 25 e Spring Boot 4.1.2 sobre Arquitetura Hexagonal com governança ArchUnit, viabilizando depósitos e saques com idempotência estrita, orquestração assíncrona via Kafka e integração completa com a tela de operações (`/dashboard/operacoes`) do Next.js 16.

**Architecture:** Dois microsserviços autônomos em Arquitetura Hexagonal pura (Ports & Adapters). O `transaction-service` gerencia o ciclo de vida da transação de negócio (`financial_transaction`), garante idempotência HTTP por cliente (`idempotency_key`) e despacha comandos assíncronos no Kafka via Transactional Outbox. O `ledger-service` é o livro-razão interno (Core Bancário, sem rota pública no Gateway), implementa partidas dobradas imutáveis (`journal` e `entry`), concorrência com lock pessimista ordenado por UUID anti-deadlock, alocação híbrida de 16 shards de caixa (`SYS-CASH-nn`) e auditoria de balancete (`trial-balance`). A comunicação entre os serviços é 100% orientada a eventos e comandos assíncronos via Kafka (`ledger.commands.v1` e `ledger.journal-results.v1`), intermediada por Outbox/Inbox em Virtual Threads. O frontend submete requisições protegidas por JWT, recebe `202 Accepted` com cabeçalho `Location` e realiza polling leve até a resolução definitiva do comprovante.

**Tech Stack:** Java 25 (Virtual Threads ativas), Spring Boot 4.1.2 (Spring Data JPA, Spring Security 7, Spring Validation, Spring Kafka, Spring Boot Actuator, SpringDoc OpenAPI 2.8.5), PostgreSQL 18 (bancos dedicados `ledger_db` e `transaction_db`), Flyway, Apache Kafka KRaft 8.4.0, Jackson 3, Lombok, ArchUnit, Testcontainers, Next.js 16.4.3 (App Router / TypeScript / Tailwind CSS v4).

**Spec:** `api-contracts/ledger-service.yaml`, `api-contracts/transaction-service.yaml`, `docs/plano_desenvolvimento_ledger.md` (Seções 3.2, 4.3, 4.4, 5.1, 5.2, 6.1 e 7 Slice 2), [GLOSSARY.md](file:///c:/Users/leosa/Documents/Projects/bank-ledger/GLOSSARY.md), [ADR 0001](file:///c:/Users/leosa/Documents/Projects/bank-ledger/docs/adr/0001-cash-sharding-hybrid-allocation.md), [ADR 0002](file:///c:/Users/leosa/Documents/Projects/bank-ledger/docs/adr/0002-ledger-pessimistic-locking-anti-deadlock.md) e [ADR 0003](file:///c:/Users/leosa/Documents/Projects/bank-ledger/docs/adr/0003-async-saga-choreography-transaction-ledger.md).

---

## Global Constraints

- **Stack & Runtime:** Java 25 com Virtual Threads ativas (`spring.threads.virtual.enabled=true`) e Spring Boot 4.1.2.
- **Portas & Gateway:**
  - `transaction-service`: executa internamente na porta `8082`, exposto publicamente pelo Nginx Gateway na porta `8080` sob o prefixo `/api/v1/transactions/*`.
  - `ledger-service`: executa internamente na porta `8084`, serviço de **núcleo privado** sem exposição pública no Nginx Gateway (acesso apenas intra-cluster e para monitorabilidade/testes).
- **Domínio Puro (Hexagonal):** Os pacotes `domain` em ambos os serviços não possuem qualquer dependência de frameworks externos (`org.springframework..`, `jakarta.persistence..`). Isolamento garantido por testes de arquitetura com ArchUnit.
- **Injeção de Dependências:** Proibido o uso de `@Autowired` em campos ou construtores. Utilizar injeção via construtor com campos `final` (ou `@RequiredArgsConstructor` do Lombok).
- **Isolamento de Persistência JPA:** Entidades JPA devem residir estritamente no subpacote `adapters.out.persistence.entity` e repositórios Spring Data em `adapters.out.persistence.repository`.
- **Valores Monetários Estritamente Inteiros:** Valores em centavos (`bigint`/`long`), nunca ponto flutuante (`float`/`double`). Moeda única BRL.
- **Premissas Contábeis Estritas:**
  - Soma dos débitos igual à soma dos créditos em cada journal contábil ($\sum \text{Débitos} - \sum \text{Créditos} = 0$).
  - Contas de clientes possuem natureza credora (`normal_balance = 'CREDIT'`) e restrição matemática absoluta de saldo $\ge 0$.
  - Shards de caixa do sistema possuem natureza devedora (`normal_balance = 'DEBIT'`).
- **Padrão de Idempotência:** Chave UUIDv4 informada no header `Idempotency-Key`, escopada pelo `customerId` do token JWT autenticado, com validação de hash SHA-256 do corpo e status `IN_PROGRESS`/`COMPLETED`.
- **Compromisso Manual de Mensagens Kafka (Manual Commit):** Todo listener Kafka (`@KafkaListener`) deve operar estritamente com `AckMode.MANUAL_IMMEDIATE` através do container configurado em `KafkaConsumerConfig`. O commit manual do offset via `acknowledgment.acknowledge()` deve ser executado **se e somente se** a transação de banco de dados e o `InboxHandler` completarem com sucesso irrestrito. Em caso de falha de banco ou erro transitório, o `acknowledge()` **não** pode ser chamado, permitindo que a mensagem seja retentada pelo Kafka sem perda de dados.
- **Idioma do Código:** Código-fonte, classes, métodos, variáveis, logs e docs em **Inglês**. Mensagens de erro e validações de negócio ao usuário final em **Português do Brasil**.

---

## Review Focus

1. **Invariante de Partidas Dobradas & Balancete Zero:** Cada lançamento no `ledger-service` deve criar 1 `journal` e exatamente 2 `entry` (uma a DÉBITO e outra a CRÉDITO com valores rigorosamente idênticos). O balancete global (`/internal/v1/ledger/trial-balance`) deve confirmar `difference == 0`.
2. **Impossibilidade Matemática de Saldo Negativo em Saque:** Em caso de tentativa de saque com valor superior ao saldo disponível na conta do cliente, o `ledger-service` deve gravar o `journal` como `REJECTED`, emitir `JournalRejectedEvent` com `rejectCode = 'INSUFFICIENT_FUNDS'` e o `transaction-service` deve atualizar a transação para `FAILED` sem alterar os saldos.
3. **Lock Pessimista Ordenado Anti-Deadlock:** A aquisição de locks nas contas envolvidas no lançamento deve seguir a ordem lexicográfica de UUID (`min(idA, idB)` depois `max(idA, idB)`), prevenindo deadlocks sob concorrência intensa entre caixas de sistema e correntistas.
4. **Idempotência Estrita no `transaction-service`:**
   - Duas requisições simultâneas com a mesma chave: a segunda deve receber HTTP `409 Conflict`.
   - Reenvio da mesma requisição após conclusão: deve retornar replay do HTTP `202 Accepted` idêntico sem gerar novo comando contábil.
   - Reenvio da mesma chave com corpo/valor adulterado: deve retornar HTTP `422 Unprocessable Entity`.
5. **Resolução Assíncrona Ponta a Ponta via Kafka:** O fluxo completo (Frontend $\to$ Gateway $\to$ Transaction $\to$ Kafka Outbox $\to$ Ledger $\to$ Kafka Outbox $\to$ Transaction Inbox $\to$ Polling Frontend) deve resolver em menos de 500ms em ambiente de teste integrado.
6. **Commit Manual no Kafka (Se e Somente Se Sucesso):** Verificar que nenhum consumer utiliza auto-commit (`enable.auto.commit = false` e `AckMode.MANUAL_IMMEDIATE`). Em caso de falha de banco de dados ou erro de infraestrutura durante o processamento, `acknowledgment.acknowledge()` NÃO deve ser chamado, permitindo que o broker Kafka reentregue a mensagem para reprocessamento seguro. Se a mensagem for duplicada, o `inbox_event` deve prevenir repetição de efeitos colaterais e comitar o offset com segurança.

---

## Estrutura de Arquivos Planejada

```text
services/ledger-service/
├── build.gradle
├── settings.gradle
├── Dockerfile
└── src/
    ├── main/
    │   ├── java/com/bankledger/ledger/
    │   │   ├── domain/
    │   │   │   ├── model/ (LedgerAccount, Journal, Entry, JournalStatus, AccountKind, NormalBalance, EntryDirection)
    │   │   │   ├── command/ (PostJournalCommand)
    │   │   │   ├── event/ (JournalPostedEvent, JournalRejectedEvent, EntryPostedEvent, LedgerAccountProvisionedEvent)
    │   │   │   └── exception/ (InsufficientFundsException, LedgerAccountNotFoundException, LedgerAccountBlockedException, ...)
    │   │   ├── application/
    │   │   │   ├── port/in/ (PostJournalUseCase, ProvisionAccountUseCase, GetLedgerBalanceUseCase, GetTrialBalanceUseCase)
    │   │   │   ├── port/out/ (LedgerAccountRepositoryPort, JournalRepositoryPort, OutboxPort)
    │   │   │   └── service/ (PostJournalService, ProvisionAccountService, LedgerQueryService, CashShardResolver)
    │   │   ├── adapters/
    │   │   │   ├── in/
    │   │   │   │   ├── kafka/ (AccountOpenedKafkaConsumer, LedgerCommandKafkaConsumer)
    │   │   │   │   └── rest/ (InternalLedgerController, GlobalExceptionHandler, DTOs)
    │   │   │   ├── out/
    │   │   │   │   ├── persistence/
    │   │   │   │   │   ├── entity/ (LedgerAccountJpaEntity, JournalJpaEntity, EntryJpaEntity)
    │   │   │   │   │   ├── repository/ (LedgerAccountSpringDataRepository, JournalSpringDataRepository, EntrySpringDataRepository)
    │   │   │   │   │   ├── LedgerAccountPersistenceAdapter.java
    │   │   │   │   │   ├── JournalPersistenceAdapter.java
    │   │   │   │   │   └── OutboxPersistenceAdapter.java
    │   │   │   │   └── kafka/ (KafkaOutboxPublisher, OutboxPollerLifecycle)
    │   │   │   └── cache/ (CashShardCache.java)
    │   │   ├── config/ (KafkaConsumerConfig, OpenApiConfig, OutboxPollerProperties, CashShardConfig)
    │   │   └── LedgerServiceApplication.java
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/
    │           └── V1__init_ledger_schema.sql
    └── test/
        ├── java/com/bankledger/ledger/
        │   ├── arch/ (HexagonalArchitectureTest)
        │   ├── domain/ (LedgerAccountTest, JournalTest, CashShardResolverTest)
        │   ├── application/ (PostJournalServiceTest, ProvisionAccountServiceTest)
        │   └── integration/ (LedgerDoubleEntryIntegrationTest, LedgerAccountProvisioningIntegrationTest)
        └── resources/
            └── application-test.yml

services/transaction-service/
├── build.gradle
├── settings.gradle
├── Dockerfile
└── src/
    ├── main/
    │   ├── java/com/bankledger/transaction/
    │   │   ├── domain/
    │   │   │   ├── model/ (FinancialTransaction, TransactionType, TransactionStatus, IdempotencyRecord, IdempotencyStatus)
    │   │   │   ├── command/ (CreateDepositCommand, CreateWithdrawalCommand)
    │   │   │   ├── event/ (TransactionCompletedEvent, TransactionFailedEvent)
    │   │   │   └── exception/ (IdempotencyConflictException, IdempotencyKeyReusedException, TransactionNotFoundException, ...)
    │   │   ├── application/
    │   │   │   ├── port/in/ (CreateDepositUseCase, CreateWithdrawalUseCase, GetTransactionUseCase, HandleJournalResultUseCase)
    │   │   │   ├── port/out/ (TransactionRepositoryPort, IdempotencyRepositoryPort, OutboxPort)
    │   │   │   └── service/ (TransactionOperationService, TransactionQueryService, IdempotencyValidatorService)
    │   │   ├── adapters/
    │   │   │   ├── in/
    │   │   │   │   ├── rest/ (TransactionController, IdempotencyFilter, GlobalExceptionHandler, DTOs)
    │   │   │   │   └── kafka/ (JournalResultKafkaConsumer)
    │   │   │   └── out/
    │   │   │       ├── persistence/
    │   │   │       │   ├── entity/ (FinancialTransactionJpaEntity, IdempotencyKeyJpaEntity)
    │   │   │       │   ├── repository/ (TransactionSpringDataRepository, IdempotencySpringDataRepository)
    │   │   │       │   ├── TransactionPersistenceAdapter.java
    │   │   │       │   ├── IdempotencyPersistenceAdapter.java
    │   │   │       │   └── OutboxPersistenceAdapter.java
    │   │   │       └── kafka/ (KafkaOutboxPublisher, OutboxPollerLifecycle)
    │   │   ├── config/ (SecurityConfig, JwtAuthenticationFilter, KafkaConsumerConfig, OpenApiConfig, OutboxPollerProperties)
    │   │   └── TransactionServiceApplication.java
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/
    │           └── V1__init_transaction_schema.sql
    └── test/
        ├── java/com/bankledger/transaction/
        │   ├── arch/ (HexagonalArchitectureTest)
        │   ├── domain/ (FinancialTransactionTest, IdempotencyRecordTest)
        │   ├── application/ (TransactionOperationServiceTest, IdempotencyValidatorServiceTest)
        │   └── integration/ (TransactionApiIntegrationTest, IdempotencyConcurrencyIntegrationTest)
        └── resources/
            └── application-test.yml

web/
└── src/
    ├── services/
    │   └── transactionService.ts (createDeposit, createWithdrawal, getTransaction, pollTransaction)
    └── app/dashboard/operacoes/
        └── page.tsx (conecta com a API real, 202 com polling e modal oficial)
```

---

## Roteiro de Execução das Tarefas

---

### FASE 1: Core Ledger (`services/ledger-service`)

#### Task 1.1: Scaffolding Hexagonal do `ledger-service` & Governança ArchUnit

**Files:**
- Create: `services/ledger-service/build.gradle`
- Create: `services/ledger-service/settings.gradle`
- Create: `services/ledger-service/src/main/java/com/bankledger/ledger/LedgerServiceApplication.java`
- Create: `services/ledger-service/src/main/java/com/bankledger/ledger/config/KafkaConsumerConfig.java`
- Create: `services/ledger-service/src/main/resources/application.yml`
- Create: `services/ledger-service/src/test/java/com/bankledger/ledger/arch/HexagonalArchitectureTest.java`
- Modify: `settings.gradle` (raiz monorepo)
- Modify: `build.gradle` (raiz monorepo)

**Interfaces:**
- Consumes: `platform-commons` (via Composite Build)
- Produces: Módulo Gradle `ledger-service` compilável, `KafkaConsumerConfig` com `AckMode.MANUAL_IMMEDIATE` e testes de governança ArchUnit ativos.

- [ ] **Step 1: Escrever teste ArchUnit para governança hexagonal**
  - Implementar em `HexagonalArchitectureTest.java`:
    1. Pacote `com.bankledger.ledger.domain..` tem zero dependências de Spring, JPA ou pacotes externos.
    2. Entidades JPA residem estritamente em `com.bankledger.ledger.adapters.out.persistence.entity..`.
    3. `application` não acessa `adapters` nem `config`.
    4. Nenhuma classe utiliza `@Autowired`.
- [ ] **Step 2: Configurar `build.gradle` e `settings.gradle` do `ledger-service`**
  - Declarar dependências: Spring Data JPA, Spring Kafka, Spring Validation, Actuator, SpringDoc OpenAPI, PostgreSQL Driver, Flyway, Lombok, Jackson 3, ArchUnit, Testcontainers.
- [ ] **Step 3: Criar classe principal, `KafkaConsumerConfig` e `application.yml` inicial**
  - Em `KafkaConsumerConfig`, configurar `ConcurrentKafkaListenerContainerFactory` com `setAckMode(AckMode.MANUAL_IMMEDIATE)`.
  - Configurar porta `8084`, conexão com banco `ledger_db`, Virtual Threads ativas (`spring.threads.virtual.enabled=true`) e `spring.kafka.consumer.enable-auto-commit=false`.
- [ ] **Step 4: Executar `./gradlew :services:ledger-service:test`**
  - Validar que o módulo compila e o teste ArchUnit passa com sucesso.

---

#### Task 1.2: Migração Flyway `V1`, Shards de Caixa e Cache em Memória

**Files:**
- Create: `services/ledger-service/src/main/resources/db/migration/V1__init_ledger_schema.sql`
- Create: `services/ledger-service/src/main/java/com/bankledger/ledger/adapters/cache/CashShardCache.java`
- Create: `services/ledger-service/src/main/java/com/bankledger/ledger/config/CashShardConfig.java`
- Create: `services/ledger-service/src/main/java/com/bankledger/ledger/application/service/CashShardResolver.java`
- Test: `services/ledger-service/src/test/java/com/bankledger/ledger/domain/CashShardResolverTest.java`

**Interfaces:**
- Consumes: PostgreSQL `ledger_db`
- Produces: Tabelas `outbox_event`, `inbox_event`, `ledger_account`, `journal`, `entry` e os 16 caixas pré-populados; cache imutável de caixas no startup; resolvedor de shard híbrido (ADR 0001).

- [ ] **Step 1: Escrever teste unitário para `CashShardResolverTest`**
  - Validar regra híbrida (ADR 0001):
    1. Se `terminalId` contiver `SYS-CASH-11`, resolve para o shard `SYS-CASH-11`.
    2. Se `terminalId` for string genérica de agência/terminal, resolve determinísticamente via `hash(terminalId) % 16`.
    3. Se `terminalId` for nulo, recorre ao hash determinístico da transação `hash(transactionId) % 16`.
- [ ] **Step 2: Criar o script DDL Flyway `V1__init_ledger_schema.sql`**
  - Incluir DDL canônico de outbox/inbox (`platform-commons`).
  - DDL de `ledger_account` com constraint `CHECK (kind = 'SYSTEM' OR balance >= 0)` e `normal_balance IN ('DEBIT','CREDIT')`.
  - DDL de `journal` com constraint `uk_journal_transaction` e status `POSTED`/`REJECTED`.
  - DDL de `entry` com constraint `uk_entry_account_seq` e direction `DEBIT`/`CREDIT`.
  - Inserção dos 16 caixas de sistema (`SYS-CASH-00` a `SYS-CASH-15`) com `kind = 'SYSTEM'`, `normal_balance = 'DEBIT'` e status `ACTIVE`.
- [ ] **Step 3: Implementar `CashShardCache` e `CashShardResolver`**
  - No startup da aplicação, carregar todos os `ledger_account` onde `kind = 'SYSTEM'` em um `Map<String, UUID>` imutável.
- [ ] **Step 4: Executar testes unitários e validar**
  - Executar `./gradlew :services:ledger-service:test --tests "*CashShard*"` e garantir fase verde.

---

#### Task 1.3: Domínio Puro do Ledger e Motor de Partidas Dobradas

**Files:**
- Create: `services/ledger-service/src/main/java/com/bankledger/ledger/domain/model/LedgerAccount.java`
- Create: `services/ledger-service/src/main/java/com/bankledger/ledger/domain/model/Journal.java`
- Create: `services/ledger-service/src/main/java/com/bankledger/ledger/domain/model/Entry.java`
- Create: `services/ledger-service/src/main/java/com/bankledger/ledger/domain/model/AccountKind.java`
- Create: `services/ledger-service/src/main/java/com/bankledger/ledger/domain/model/NormalBalance.java`
- Create: `services/ledger-service/src/main/java/com/bankledger/ledger/domain/model/EntryDirection.java`
- Create: `services/ledger-service/src/main/java/com/bankledger/ledger/domain/model/JournalStatus.java`
- Create: `services/ledger-service/src/main/java/com/bankledger/ledger/domain/command/PostJournalCommand.java`
- Create: `services/ledger-service/src/main/java/com/bankledger/ledger/domain/event/JournalPostedEvent.java`
- Create: `services/ledger-service/src/main/java/com/bankledger/ledger/domain/event/JournalRejectedEvent.java`
- Create: `services/ledger-service/src/main/java/com/bankledger/ledger/domain/event/EntryPostedEvent.java`
- Create: `services/ledger-service/src/main/java/com/bankledger/ledger/domain/exception/InsufficientFundsException.java`
- Create: `services/ledger-service/src/main/java/com/bankledger/ledger/domain/exception/UnbalancedJournalException.java`
- Test: `services/ledger-service/src/test/java/com/bankledger/ledger/domain/LedgerAccountTest.java`
- Test: `services/ledger-service/src/test/java/com/bankledger/ledger/domain/JournalTest.java`

**Interfaces:**
- Consumes: Nenhuma (Domínio 100% puro)
- Produces: Modelos agregados imutáveis e regras contábeis auditadas.

- [ ] **Step 1: Escrever testes unitários em `LedgerAccountTest` e `JournalTest`**
  - Testar invariante contábil:
    1. Conta de cliente (`normal_balance = CREDIT`): lançamento a CRÉDITO aumenta saldo; lançamento a DÉBITO diminui saldo.
    2. Lançamento a DÉBITO superior ao saldo dispara `InsufficientFundsException`.
    3. Conta de caixa (`normal_balance = DEBIT`): lançamento a DÉBITO aumenta saldo; lançamento a CRÉDITO diminui saldo.
    4. Criação de Journal onde $\sum \text{Débitos} \ne \sum \text{Créditos}$ dispara `UnbalancedJournalException`.
- [ ] **Step 2: Implementar classes de domínio e records**
  - Implementar lógica pura sem frameworks.
- [ ] **Step 3: Executar testes unitários de domínio**
  - Garantir que todos os testes passem com 100% de sucesso.

---

#### Task 1.4: Caso de Uso `PostJournalService` com Lock Pessimista Anti-Deadlock

**Files:**
- Create: `services/ledger-service/src/main/java/com/bankledger/ledger/application/port/in/PostJournalUseCase.java`
- Create: `services/ledger-service/src/main/java/com/bankledger/ledger/application/port/out/LedgerAccountRepositoryPort.java`
- Create: `services/ledger-service/src/main/java/com/bankledger/ledger/application/port/out/JournalRepositoryPort.java`
- Create: `services/ledger-service/src/main/java/com/bankledger/ledger/application/port/out/OutboxPort.java`
- Create: `services/ledger-service/src/main/java/com/bankledger/ledger/application/service/PostJournalService.java`
- Test: `services/ledger-service/src/test/java/com/bankledger/ledger/application/PostJournalServiceTest.java`

**Interfaces:**
- Consumes: `PostJournalCommand`
- Produces: Execução atômica de depósito/saque, gravação de Journal/Entries e gravação de eventos de resultado no Outbox.

- [ ] **Step 1: Escrever teste de unidade para `PostJournalServiceTest` com Mocks**
  - Cenários:
    1. Depósito: bloqueia caixa e cliente de forma ordenada por UUID; credita cliente; debita caixa; grava Journal POSTED e OutboxEvents.
    2. Saque com saldo suficiente: bloqueia ordenadamente; debita cliente; credita caixa; grava Journal POSTED.
    3. Saque com saldo insuficiente: captura `InsufficientFundsException`; grava Journal REJECTED com reject_code `INSUFFICIENT_FUNDS`; publica `JournalRejectedEvent` no Outbox sem tocar nos saldos.
- [ ] **Step 2: Implementar `PostJournalService`**
  - Aplicar ordenação lexicográfica de UUIDs antes da chamada a `findByIdForUpdate` (ADR 0002).
  - Executar tudo dentro de transação `@Transactional`.
- [ ] **Step 3: Executar testes de aplicação**
  - Rodar `./gradlew :services:ledger-service:test --tests "com.bankledger.ledger.application.*"` e verificar sucesso.

---

#### Task 1.5: Provisionamento Contábil de Contas (`AccountOpened` Kafka Consumer com Commit Manual)

**Files:**
- Create: `services/ledger-service/src/main/java/com/bankledger/ledger/application/port/in/ProvisionAccountUseCase.java`
- Create: `services/ledger-service/src/main/java/com/bankledger/ledger/application/service/ProvisionAccountService.java`
- Create: `services/ledger-service/src/main/java/com/bankledger/ledger/domain/event/AccountOpenedPayload.java`
- Create: `services/ledger-service/src/main/java/com/bankledger/ledger/domain/event/LedgerAccountProvisionedEvent.java`
- Create: `services/ledger-service/src/main/java/com/bankledger/ledger/adapters/in/kafka/AccountOpenedKafkaConsumer.java`
- Test: `services/ledger-service/src/test/java/com/bankledger/ledger/application/ProvisionAccountServiceTest.java`
- Test: `services/ledger-service/src/test/java/com/bankledger/ledger/adapters/in/kafka/AccountOpenedKafkaConsumerTest.java`

**Interfaces:**
- Consumes: Tópico `account.events.v1` (com `auto-offset-reset: earliest`)
- Produces: Conta contábil provisionada no `ledger_db` com saldo 0, evento `LedgerAccountProvisioned` no Outbox, commit manual do offset via `Acknowledgment.acknowledge()` se e somente se a transação do banco for bem sucedida.

- [ ] **Step 1: Escrever teste unitário para `ProvisionAccountServiceTest` e `AccountOpenedKafkaConsumerTest`**
  - Em `ProvisionAccountServiceTest`:
    - Validar criação de `LedgerAccount` com `id = accountId`, `code = ACC-0001-100000-1`, `kind = CUSTOMER`, `normal_balance = CREDIT`, `balance = 0` e status `ACTIVE`.
    - Garantir idempotência: se a conta já existir, não falhar (ignorar silenciosamente).
  - Em `AccountOpenedKafkaConsumerTest`:
    - Testar que `acknowledgment.acknowledge()` é chamado imediatamente após `inboxHandler.execute` completar com sucesso.
    - Testar que se `inboxHandler.execute` lançar exceção (ex: falha de banco), `acknowledgment.acknowledge()` NUNCA é chamado e a exceção é re-lançada.
- [ ] **Step 2: Implementar `ProvisionAccountService` e `AccountOpenedKafkaConsumer`**
  - No consumer, injetar `Acknowledgment acknowledgment` no método `@KafkaListener`.
  - Executar a lógica de provisionamento envelopada em `inboxHandler.execute`.
  - Executar `acknowledgment.acknowledge()` após o bloco de transação.
- [ ] **Step 3: Validar testes unitários do provisionamento contábil e commit manual**
  - Rodar `./gradlew :services:ledger-service:test --tests "*AccountOpened*"` e garantir fase verde.

---

#### Task 1.6: Adaptadores de Persistência, Outbox Poller, Consumer de Comandos com Commit Manual e Testes de Integração com Testcontainers

**Files:**
- Create: Entidades JPA e repositórios Spring Data em `adapters.out.persistence..`
- Create: `LedgerAccountPersistenceAdapter.java`, `JournalPersistenceAdapter.java`, `OutboxPersistenceAdapter.java`
- Create: `KafkaOutboxPublisher.java` e `OutboxPollerLifecycle.java`
- Create: `InternalLedgerController.java` (`/internal/v1/ledger/accounts/{id}/balance` e `/trial-balance`)
- Create: `LedgerCommandKafkaConsumer.java` (consome `ledger.commands.v1` com `InboxHandler` e `Acknowledgment`)
- Test: `services/ledger-service/src/test/java/com/bankledger/ledger/adapters/in/kafka/LedgerCommandKafkaConsumerTest.java`
- Test: `services/ledger-service/src/test/java/com/bankledger/ledger/integration/LedgerDoubleEntryIntegrationTest.java`

**Interfaces:**
- Consumes: PostgreSQL 18 e Kafka KRaft (Testcontainers)
- Produces: Teste de integração completo de ponta a ponta do `ledger-service` com commit manual garantido.

- [ ] **Step 1: Escrever teste unitário para `LedgerCommandKafkaConsumerTest`**
  - Mock de `Acknowledgment` e `InboxHandler`:
    1. Sucesso: `acknowledgment.acknowledge()` chamado exatamente 1 vez.
    2. Falha de execução/banco: `acknowledgment.acknowledge()` tem 0 interações e a exceção é propagada.
- [ ] **Step 2: Escrever teste de integração com Testcontainers em `LedgerDoubleEntryIntegrationTest`**
  - Subir Postgres 18 efêmero com Flyway e Kafka KRaft.
  - Testar sequência real:
    1. Publicar `AccountOpened` no Kafka $\to$ verificar criação da conta no banco contábil com saldo 0.
    2. Publicar `PostJournalCommand` de depósito de R$ 500,00 $\to$ aguardar processamento $\to$ verificar `journal` POSTED, `entries` balanceadas e saldo da conta = 50.000 centavos.
    3. Publicar `PostJournalCommand` de saque de R$ 200,00 $\to$ verificar saldo final = 30.000 centavos.
    4. Publicar `PostJournalCommand` de saque de R$ 500,00 (saldo insuficiente) $\to$ verificar `journal` REJECTED, evento `JournalRejected` publicado no Kafka e saldo mantido em 30.000 centavos.
    5. Chamar `/internal/v1/ledger/trial-balance` e confirmar `difference == 0`.
- [ ] **Step 3: Implementar os adaptadores de persistência, outbox e consumer de comandos com commit manual**
- [ ] **Step 4: Executar teste de integração completo**
  - Rodar `./gradlew :services:ledger-service:test` e certificar que todos os testes passam.

---

### FASE 2: Operações de Transação (`services/transaction-service`)

#### Task 2.1: Scaffolding Hexagonal do `transaction-service` & Governança ArchUnit

**Files:**
- Create: `services/transaction-service/build.gradle`
- Create: `services/transaction-service/settings.gradle`
- Create: `services/transaction-service/src/main/java/com/bankledger/transaction/TransactionServiceApplication.java`
- Create: `services/transaction-service/src/main/java/com/bankledger/transaction/config/KafkaConsumerConfig.java`
- Create: `services/transaction-service/src/main/resources/application.yml`
- Create: `services/transaction-service/src/test/java/com/bankledger/transaction/arch/HexagonalArchitectureTest.java`
- Modify: `settings.gradle` (raiz monorepo)
- Modify: `build.gradle` (raiz monorepo)

**Interfaces:**
- Consumes: `platform-commons` (via Composite Build)
- Produces: Módulo Gradle `transaction-service` compilável, `KafkaConsumerConfig` com `AckMode.MANUAL_IMMEDIATE` e teste ArchUnit ativo.

- [ ] **Step 1: Escrever teste de arquitetura ArchUnit em `HexagonalArchitectureTest.java`**
- [ ] **Step 2: Configurar dependências no `build.gradle`**
- [ ] **Step 3: Criar classe principal, `KafkaConsumerConfig` e `application.yml` inicial**
  - Em `KafkaConsumerConfig`, configurar `ConcurrentKafkaListenerContainerFactory` com `setAckMode(AckMode.MANUAL_IMMEDIATE)`.
  - Configurar porta `8082`, conexão com banco `transaction_db`, Virtual Threads ativas (`spring.threads.virtual.enabled=true`) e `spring.kafka.consumer.enable-auto-commit=false`.
- [ ] **Step 4: Executar `./gradlew :services:transaction-service:test` e validar governança verde**

---

#### Task 2.2: Migração Flyway `V1` e Mecanismo Estrito de Idempotência HTTP

**Files:**
- Create: `services/transaction-service/src/main/resources/db/migration/V1__init_transaction_schema.sql`
- Create: `services/transaction-service/src/main/java/com/bankledger/transaction/domain/model/IdempotencyRecord.java`
- Create: `services/transaction-service/src/main/java/com/bankledger/transaction/domain/model/IdempotencyStatus.java`
- Create: `services/transaction-service/src/main/java/com/bankledger/transaction/domain/exception/IdempotencyConflictException.java`
- Create: `services/transaction-service/src/main/java/com/bankledger/transaction/domain/exception/IdempotencyKeyReusedException.java`
- Create: `services/transaction-service/src/main/java/com/bankledger/transaction/application/port/out/IdempotencyRepositoryPort.java`
- Create: `services/transaction-service/src/main/java/com/bankledger/transaction/application/service/IdempotencyValidatorService.java`
- Test: `services/transaction-service/src/test/java/com/bankledger/transaction/application/IdempotencyValidatorServiceTest.java`

**Interfaces:**
- Consumes: PostgreSQL `transaction_db`
- Produces: Tabela `idempotency_key` e serviço de validação/bloqueio de concorrência.

- [ ] **Step 1: Escrever teste unitário para `IdempotencyValidatorServiceTest`**
  - Casos de teste:
    1. Chave inédita: cria registro com status `IN_PROGRESS` e expiração em 24h.
    2. Chave com status `IN_PROGRESS`: lança `IdempotencyConflictException` (HTTP 409).
    3. Chave com status `COMPLETED` e mesmo hash de payload: retorna o resultado em cache para replay idêntico.
    4. Chave com status `COMPLETED` e hash diferente: lança `IdempotencyKeyReusedException` (HTTP 422).
- [ ] **Step 2: Criar script Flyway `V1__init_transaction_schema.sql`**
  - DDL de outbox/inbox (`platform-commons`).
  - DDL de `financial_transaction` com status `PENDING`, `COMPLETED`, `FAILED`.
  - DDL de `idempotency_key` com chave primária composta `(scope, idem_key)`.
- [ ] **Step 3: Implementar `IdempotencyValidatorService`**
- [ ] **Step 4: Executar testes de unidade e validar fase verde**

---

#### Task 2.3: Casos de Uso de Depósito, Saque e Consulta de Status

**Files:**
- Create: `services/transaction-service/src/main/java/com/bankledger/transaction/domain/model/FinancialTransaction.java`
- Create: `services/transaction-service/src/main/java/com/bankledger/transaction/domain/model/TransactionType.java`
- Create: `services/transaction-service/src/main/java/com/bankledger/transaction/domain/model/TransactionStatus.java`
- Create: `services/transaction-service/src/main/java/com/bankledger/transaction/application/port/in/CreateDepositUseCase.java`
- Create: `services/transaction-service/src/main/java/com/bankledger/transaction/application/port/in/CreateWithdrawalUseCase.java`
- Create: `services/transaction-service/src/main/java/com/bankledger/transaction/application/port/in/GetTransactionUseCase.java`
- Create: `services/transaction-service/src/main/java/com/bankledger/transaction/application/service/TransactionOperationService.java`
- Create: `services/transaction-service/src/main/java/com/bankledger/transaction/application/service/TransactionQueryService.java`
- Test: `services/transaction-service/src/test/java/com/bankledger/transaction/application/TransactionOperationServiceTest.java`

**Interfaces:**
- Consumes: Requisitos de API (`DepositRequest`, `WithdrawalRequest`)
- Produces: Criação de transação PENDING, registro do comando `PostJournalCommand` no Outbox e atualização do registro de idempotência.

- [ ] **Step 1: Escrever teste unitário em `TransactionOperationServiceTest`**
  - Validar criação atômica da transação `PENDING` e emissão do `PostJournalCommand` no Outbox para depósito e saque.
- [ ] **Step 2: Implementar `TransactionOperationService` e `TransactionQueryService`**
- [ ] **Step 3: Executar testes unitários de aplicação**

---

#### Task 2.4: Consumidor de Resultados Contábeis (`JournalResultKafkaConsumer` com Commit Manual)

**Files:**
- Create: `services/transaction-service/src/main/java/com/bankledger/transaction/application/port/in/HandleJournalResultUseCase.java`
- Create: `services/transaction-service/src/main/java/com/bankledger/transaction/domain/event/JournalResultPayload.java`
- Create: `services/transaction-service/src/main/java/com/bankledger/transaction/adapters/in/kafka/JournalResultKafkaConsumer.java`
- Test: `services/transaction-service/src/test/java/com/bankledger/transaction/application/HandleJournalResultTest.java`
- Test: `services/transaction-service/src/test/java/com/bankledger/transaction/adapters/in/kafka/JournalResultKafkaConsumerTest.java`

**Interfaces:**
- Consumes: Tópico `ledger.journal-results.v1`
- Produces: Transição de status da `financial_transaction` para `COMPLETED` ou `FAILED` e commit manual do offset Kafka via `Acknowledgment.acknowledge()` se e somente se o banco atualizar com sucesso.

- [ ] **Step 1: Escrever teste unitário para `HandleJournalResultTest` e `JournalResultKafkaConsumerTest`**
  - Em `HandleJournalResultTest`:
    - Testar transição para `COMPLETED` quando `status == POSTED`.
    - Testar transição para `FAILED` com `failureCode` e `failureReason` quando `status == REJECTED`.
  - Em `JournalResultKafkaConsumerTest`:
    - Mock de `Acknowledgment` e `InboxHandler`:
      1. Se o processamento e a transação no banco forem bem sucedidos: `acknowledgment.acknowledge()` é chamado exatamente 1 vez.
      2. Se ocorrer erro de banco/execução: `acknowledgment.acknowledge()` NÃO é chamado e a exceção é propagada.
- [ ] **Step 2: Implementar consumer Kafka com `InboxHandler` e injeção de `Acknowledgment`**
  - Invocar `acknowledgment.acknowledge()` somente após `inboxHandler.execute` completar.
- [ ] **Step 3: Validar testes de resolução de transações e commit manual**
  - Rodar `./gradlew :services:transaction-service:test --tests "*JournalResult*"` e certificar fase verde.

---

#### Task 2.5: Controllers REST, Segurança JWT e Testes de Integração com Testcontainers

**Files:**
- Create: `TransactionController.java` (`POST /api/v1/transactions/deposits`, `withdrawals`, `GET /{id}`)
- Create: `SecurityConfig.java` e `JwtAuthenticationFilter.java` (validação de token JWT Bearer compatível com `account-service`)
- Create: `GlobalExceptionHandler.java` (RFC 7807 `ProblemDetail` com 400, 401, 404, 409, 422)
- Create: Entidades JPA e repositórios Spring Data
- Test: `services/transaction-service/src/test/java/com/bankledger/transaction/integration/TransactionApiIntegrationTest.java`
- Test: `services/transaction-service/src/test/java/com/bankledger/transaction/integration/IdempotencyConcurrencyIntegrationTest.java`

**Interfaces:**
- Consumes: API REST e JWT Bearer Token
- Produces: Endpoints em conformidade estrita com `api-contracts/transaction-service.yaml`.

- [ ] **Step 1: Escrever teste de integração para a API REST (`TransactionApiIntegrationTest`)**
  - Testar `POST /deposits` com `Idempotency-Key` e JWT:
    1. Retorna HTTP `202 Accepted` com header `Location: /api/v1/transactions/{id}` e corpo da transação PENDING.
    2. Reenvio da mesma requisição com a mesma chave: retorna HTTP `202 Accepted` idêntico (replay).
    3. Reenvio com valor diferente na mesma chave: retorna HTTP `422 Unprocessable Entity`.
    4. Consulta `GET /transactions/{id}`: retorna status da transação.
- [ ] **Step 2: Escrever teste de concorrência simultânea (`IdempotencyConcurrencyIntegrationTest`)**
  - Disparar 10 threads simultâneas com a mesma `Idempotency-Key`: exatamente 1 deve vencer e receber 202; as demais 9 devem receber 409 Conflict.
- [ ] **Step 3: Implementar controllers, filtro JWT e handlers de erro**
- [ ] **Step 4: Executar suíte completa de testes de integração**
  - Rodar `./gradlew :services:transaction-service:test` e certificar cobertura 100% verde.

---

### FASE 3: Integração com Gateway e Frontend Next.js 16

#### Task 3.1: Configuração do Nginx Gateway e Docker Compose

**Files:**
- Modify: `infra/docker/nginx/nginx.conf`
- Modify: `infra/docker/docker-compose.yml`

**Interfaces:**
- Consumes: Containers `ledger-service` e `transaction-service`
- Produces: Roteamento Nginx `/api/v1/transactions/*` para `transaction-service:8082` e ambiente completo orquestrado.

- [ ] **Step 1: Atualizar roteamento no `infra/docker/nginx/nginx.conf`**
  - Adicionar bloco de upstream `transaction-service:8082` e rota `location /api/v1/transactions/`.
- [ ] **Step 2: Adicionar os novos serviços ao `infra/docker/docker-compose.yml`**
  - Declarar `ledger-service` (porta interna 8084, dependente de `postgres` e `kafka`) com healthcheck no `/actuator/health`.
  - Declarar `transaction-service` (porta interna 8082, dependente de `postgres`, `kafka` e `ledger-service`) com healthcheck no `/actuator/health`.
- [ ] **Step 3: Subir infraestrutura e validar conectividade**
  - Executar `docker compose up -d` e verificar que todos os serviços ficam saudáveis (`healthy`).

---

#### Task 3.2: Cliente de Transações no Frontend (`web/src/services/transactionService.ts`)

**Files:**
- Create: `web/src/services/transactionService.ts`
- Modify: `web/src/services/index.ts`
- Modify: `web/src/services/types.ts`

**Interfaces:**
- Consumes: Nginx Gateway `http://localhost:8080/api/v1/transactions/*`
- Produces: Funções tipadas em TypeScript para criação de depósito/saque com polling de status.

- [ ] **Step 1: Definir tipos em `types.ts`**
  - `TransactionResponse`, `DepositRequest`, `WithdrawalRequest`.
- [ ] **Step 2: Implementar `transactionService.ts`**
  - Implementar métodos:
    1. `createDeposit(amountCents, idempotencyKey, terminalId)`
    2. `createWithdrawal(amountCents, idempotencyKey, terminalId)`
    3. `getTransactionById(transactionId)`
    4. `pollTransactionUntilResolved(transactionId, intervalMs = 350, maxAttempts = 10)`: realiza polling até status `COMPLETED` ou `FAILED`.

---

#### Task 3.3: Integração da Tela `/dashboard/operacoes` com a API Real

**Files:**
- Modify: `web/src/app/dashboard/operacoes/page.tsx`

**Interfaces:**
- Consumes: `transactionService.ts` e token de autenticação JWT do usuário logado
- Produces: Fluxo real de depósito e saque sem mock ativo.

- [ ] **Step 1: Atualizar o formulário de depósito e saque em `page.tsx`**
  - Substituir chamada síncrona mockada por submissão real:
    1. Ao clicar em "Confirmar Depósito" ou "Confirmar Saque", ativar estado `isSubmitting` com feedback *"Processando liquidação contábil..."*.
    2. Enviar requisição com header `Idempotency-Key` (gerado dinamicamente via UUIDv4).
    3. Capturar o HTTP 202 e executar `pollTransactionUntilResolved`.
    4. Se `COMPLETED`: preencher `successItem` e renderizar comprovante oficial bancário com identificador da transação, data/hora e terminal.
    5. Se `FAILED`: desativar spinner e exibir mensagem amigável de erro (ex: *"Saldo insuficiente para efetuar este saque"*).
- [ ] **Step 2: Testar no navegador**
  - Fazer login com a conta criada no Slice 1.
  - Acessar `/dashboard/operacoes`.
  - Executar depósito de R$ 500,00 $\to$ verificar exibição do comprovante de sucesso após ~200ms.
  - Executar saque de R$ 150,00 $\to$ verificar sucesso.
  - Tentar saque de R$ 1.000,00 (acima do saldo) $\to$ verificar rejeição elegante informando saldo insuficiente.

---

### FASE 4: Validação Final e Critérios de Conclusão

#### Task 4.1: Validação de Ponta a Ponta e Auditoria Contábil

- [ ] **Step 1: Executar `./gradlew test` em todo o monorepo**
  - Todos os testes de `platform-commons`, `account-service`, `ledger-service` e `transaction-service` devem passar com sucesso.
- [ ] **Step 2: Auditoria de Balancete no Banco Real**
  - Fazer chamada `GET http://localhost:8084/internal/v1/ledger/trial-balance` e confirmar `difference == 0`.
- [ ] **Step 3: Atualizar documentação de progresso**
  - Marcar o Slice 2 como concluído em `docs/plano_desenvolvimento_ledger.md`.

