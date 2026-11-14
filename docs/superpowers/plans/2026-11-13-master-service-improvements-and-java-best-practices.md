# Plano Consolidado de Melhorias de Serviços e Modernização Java

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implementar de forma unificada as melhorias arquiteturais e as melhores práticas de Java moderno (baseadas nas Pílulas de Java e Java 25 LTS): padrão Strategy no `PostJournalService`, correção de hash com `Math.floorMod`, máquina de estados com `EnumMap`, eliminação de boxing em partidas dobradas, remoção da convenção `Jpa` em entidades, limpeza de anotações desnecessárias, centralização de constantes e contratos canônicos em `platform-commons`, desserialização direta no Kafka com `ObjectMapper`, consulta atômica de balancete (`getTrialBalance`), documentação de concorrência avançada (ADRs 0004, 0005, 0006) e ativação de Compact Object Headers nos Dockerfiles.

**Architecture:** Refatoração evolutiva preservando a arquitetura hexagonal e a governança do ArchUnit, eliminando armadilhas de concorrência, otimizando o consumo de recursos na JVM e estabelecendo padrões de código limpos e manuteníveis.

**Tech Stack:** Java 25 LTS, Spring Boot 4.1.2, Spring Data JPA, Spring Kafka, PostgreSQL 18, Confluent Kafka 8.4.0, Jackson 3 (`tools.jackson`), ArchUnit 1.4, Testcontainers 1.20.4, Docker Compose.

**Spec:** Especificações em `docs/adr/0001-cash-sharding-hybrid-allocation.md`, `docs/adr/0002-ledger-pessimistic-locking-anti-deadlock.md`, `docs/adr/0003-async-saga-choreography-transaction-ledger.md` e guia `java-best-practices/SKILL.md`.

## Global Constraints

- Manter 100% dos testes unitários e de integração verdes (`./gradlew test`) ao final de cada tarefa.
- Nenhuma quebra nas regras de governança arquitetural do ArchUnit (`HexagonalArchitectureTest`).
- Todas as injeções de dependência devem continuar via construtor com campos `final` (ou Lombok `@RequiredArgsConstructor`); proibido uso de `@Autowired` em atributos ou métodos.
- O motor de partidas dobradas deve manter a invariante estrita: $\sum \text{Débitos} == \sum \text{Créditos} == \text{totalAmount} > 0$ e saldo não-negativo para contas de clientes.
- Preservar o bloqueio pessimista ordenado por UUID ($\min(idA, idB)$ antes de $\max(idA, idB)$) para prevenir deadlocks.
- Preservar a garantia de commit manual no Kafka (`AckMode.MANUAL_IMMEDIATE` com confirmação estritamente após o sucesso da transação no banco).

## Review Focus

1. **Invariantes e Concorrência:** Garantir que o Strategy Pattern no `PostJournalService` mantenha o lock anti-deadlock e o tratamento de `INSUFFICIENT_FUNDS`.
2. **Imunidade a Índice Negativo:** Garantir que `CashShardResolver` use `Math.floorMod` para eliminar o bug de `Math.abs(Integer.MIN_VALUE)`.
3. **Máquina de Estados:** Validar que `FinancialTransaction` impeça transições a partir de estados terminais (`COMPLETED` e `FAILED`).
4. **Desserialização Kafka:** Garantir que a desserialização via `ObjectMapper` trate payloads com e sem envelope CloudEvents sem exceções inesperadas.
5. **Atomicidade no Balancete:** Garantir que `getTrialBalance` execute em query SQL única sem risco de phantom read.
6. **Integridade de Mapeamento JPA:** Garantir que a renomeação de `*JpaEntity` para `*Entity` e a remoção de `@Repository` preservem os mapeamentos e repositórios intactos.

---

### FASE 1: Domínio Puro, Invariantes e Java Moderno

#### Task 1.1: Correções de Domínio e Invariantes (Math.floorMod, Máquina de Estados com EnumMap e Streams sem Boxing)

**Files:**
- Modify: `services/ledger-service/src/main/java/com/bankledger/ledger/application/service/CashShardResolver.java:50,64`
- Modify: `services/transaction-service/src/main/java/com/bankledger/transaction/domain/model/FinancialTransaction.java:160-179`
- Modify: `services/ledger-service/src/main/java/com/bankledger/ledger/domain/model/Journal.java:87-95`
- Test: `services/ledger-service/src/test/java/com/bankledger/ledger/application/service/CashShardResolverTest.java`
- Test: `services/transaction-service/src/test/java/com/bankledger/transaction/domain/FinancialTransactionTest.java`
- Test: `services/ledger-service/src/test/java/com/bankledger/ledger/domain/model/JournalTest.java`

**Interfaces:**
- Consumes: `terminalId`, `transactionId`, `TransactionStatus`, `List<Entry>`
- Produces: Resolução de shard segura contra estouro de inteiro, máquina de estados imutável via `EnumMap`, validação declarativa de partidas dobradas sem boxing.

- [ ] **Step 1: Corrigir cálculo de dispersão de shards em `CashShardResolver`**
  - Substituir `Math.abs(h) % TOTAL_SHARDS` por `Math.floorMod(h, TOTAL_SHARDS)` para `terminalId` e `transactionId`.
  - Adicionar teste unitário cobrindo o caso extremo onde `hashCode == Integer.MIN_VALUE`.
- [ ] **Step 2: Implementar máquina de estados com `EnumMap` em `FinancialTransaction`**
  - Declarar tabela canônica `VALID_TRANSITIONS` usando `EnumMap<TransactionStatus, EnumSet<TransactionStatus>>` (Pílulas de Java #85).
  - Permitir transição apenas de `PENDING` para `COMPLETED` ou `FAILED`.
  - Rejeitar transições a partir de `COMPLETED` ou `FAILED` lançando `IllegalStateException`.
  - Escrever testes unitários validando a proteção contra transições ilegais.
- [ ] **Step 3: Refatorar equilíbrio contábil em `Journal.createPosted`**
  - Substituir loop imperativo com variáveis mutáveis por pipeline funcional declarativo com `mapToLong(Entry::amount).sum()` (Pílulas de Java #1 & #14).
- [ ] **Step 4: Executar suítes de teste de unidade**
  - Rodar `./gradlew :services:ledger-service:test :services:transaction-service:test`.
  - Confirmar aprovação de todos os testes de domínio.

---

#### Task 1.2: Strategy Pattern no `PostJournalService` e Desacoplamento da Lógica Pai

**Files:**
- Create: `services/ledger-service/src/main/java/com/bankledger/ledger/application/service/strategy/JournalPostingStrategy.java`
- Create: `services/ledger-service/src/main/java/com/bankledger/ledger/application/service/strategy/DepositPostingStrategy.java`
- Create: `services/ledger-service/src/main/java/com/bankledger/ledger/application/service/strategy/WithdrawalPostingStrategy.java`
- Create: `services/ledger-service/src/main/java/com/bankledger/ledger/application/service/strategy/TransferPostingStrategy.java`
- Create: `services/ledger-service/src/main/java/com/bankledger/ledger/application/service/strategy/PostingAccountsPair.java`
- Create: `services/ledger-service/src/main/java/com/bankledger/ledger/application/service/strategy/PostingEntriesResult.java`
- Modify: `services/ledger-service/src/main/java/com/bankledger/ledger/application/service/PostJournalService.java`
- Test: `services/ledger-service/src/test/java/com/bankledger/ledger/application/service/strategy/PostingStrategyTest.java`
- Test: `services/ledger-service/src/test/java/com/bankledger/ledger/application/PostJournalServiceTest.java`

**Interfaces:**
- Consumes: `PostJournalCommand`, `CashShardResolver`, `LedgerAccount`
- Produces: Partidas dobradas encapsuladas por tipo de operação contábil.

- [ ] **Step 1: Criar interfaces e records da estratégia**
  - `JournalPostingStrategy` com `supports(String transactionType)`, `resolveAccountsToLock(PostJournalCommand command)` e `generateEntries(command, firstAccount, secondAccount, journalId, now)`.
  - Records imutáveis `PostingAccountsPair` e `PostingEntriesResult`.
- [ ] **Step 2: Implementar as estratégias especializadas**
  - `DepositPostingStrategy`: Débito no shard de caixa e Crédito na conta destino.
  - `WithdrawalPostingStrategy`: Débito na conta origem e Crédito no shard de caixa; captura de `InsufficientFundsException` gerando rejeição.
  - `TransferPostingStrategy`: Débito na conta origem e Crédito na conta destino; captura de `InsufficientFundsException`.
- [ ] **Step 3: Refatorar `PostJournalService` para orquestração limpa**
  - Injetar `List<JournalPostingStrategy>`.
  - Delegar seleção de contas e geração de lançamentos à estratégia correspondente.
  - Preservar na classe orquestradora:
    1. Verificação de idempotência no repositório.
    2. Bloqueio pessimista ordenado por UUID anti-deadlock.
    3. Persistência de Journal e Entries.
    4. Emissão de eventos Outbox (`JournalPostedEvent` ou `JournalRejectedEvent`).
- [ ] **Step 4: Executar testes de integração com Testcontainers**
  - Executar `LedgerDoubleEntryIntegrationTest` e certificar execução 100% verde.

---

### FASE 2: Persistência, Nomenclatura e Governança de Código

#### Task 2.1: Remoção da Convenção `Jpa` nas Entidades e Limpeza de `@Repository` em Interfaces

**Files:**
- Rename/Modify em `account-service`:
  - `AccountJpaEntity.java` $\to$ `AccountEntity.java`
  - `CustomerJpaEntity.java` $\to$ `CustomerEntity.java`
  - `AccountSpringDataRepository.java`: remover `@Repository`
  - `CustomerSpringDataRepository.java`: remover `@Repository`
- Rename/Modify em `ledger-service`:
  - `LedgerAccountJpaEntity.java` $\to$ `LedgerAccountEntity.java`
  - `JournalJpaEntity.java` $\to$ `JournalEntity.java`
  - `EntryJpaEntity.java` $\to$ `EntryEntity.java`
  - `LedgerAccountSpringDataRepository.java`: remover `@Repository`
  - `JournalSpringDataRepository.java`: remover `@Repository`
  - `EntrySpringDataRepository.java`: remover `@Repository`
- Rename/Modify em `transaction-service`:
  - `FinancialTransactionJpaEntity.java` $\to$ `FinancialTransactionEntity.java`
  - `IdempotencyKeyJpaEntity.java` $\to$ `IdempotencyKeyEntity.java`
  - `TransactionSpringDataRepository.java`: remover `@Repository`
  - `IdempotencySpringDataRepository.java`: remover `@Repository`
- Modify: Mapeamentos nos adaptadores de persistência e testes de repositório.

**Interfaces:**
- Consumes: Camada de persistência JPA
- Produces: Entidades com nomenclatura canônica `*Entity` e interfaces Spring Data limpas sem anotações redundantes (SonarQube `java:S1130` / `java:S6830`).

- [ ] **Step 1: Renomear classes e arquivos de entidade para `*Entity`**
  - Renomear mantendo anotações `@Entity` e `@Table(name = "...")`.
  - Garantir `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` no `@Id` e proibir uso de `@Data`.
- [ ] **Step 2: Remover `@Repository` das interfaces que estendem `JpaRepository`**
  - Remover anotação `@Repository` redundante de todas as interfaces Spring Data.
- [ ] **Step 3: Atualizar adaptadores de persistência e repositórios**
  - Atualizar tipos genéricos e métodos de conversão `toDomain()` / `fromDomain()`.
- [ ] **Step 4: Executar testes e regras ArchUnit**
  - Rodar `./gradlew test` em todos os microserviços e validar 100% de aprovação no ArchUnit.

---

#### Task 2.2: Constantes Centralizadas para Strings Mágicas e Governança de Código

**Files:**
- Create: `services/platform-commons/src/main/java/com/bankledger/commons/constant/KafkaTopics.java`
- Create: `services/platform-commons/src/main/java/com/bankledger/commons/constant/BankingErrorCodes.java`
- Create: `services/platform-commons/src/main/java/com/bankledger/commons/constant/HttpHeaderConstants.java`
- Create: `services/platform-commons/src/main/java/com/bankledger/commons/constant/BankingConstants.java`
- Modify: Consumers, publishers e controllers que utilizam strings literais hardcoded.

**Interfaces:**
- Consumes: Constantes reutilizáveis
- Produces: Código centralizado, manutenível e imune a erros de digitação.

- [ ] **Step 1: Criar classes utilitárias de constantes em `platform-commons`**
  - `KafkaTopics` (`ACCOUNT_EVENTS_V1`, `LEDGER_COMMANDS_V1`, `LEDGER_JOURNAL_RESULTS_V1`, etc.).
  - `BankingErrorCodes` (`INSUFFICIENT_FUNDS`, `LIMIT_EXCEEDED`, `ACCOUNT_NOT_FOUND`, etc.).
  - `HttpHeaderConstants` (`IDEMPOTENCY_KEY`, `AUTHORIZATION`, `BEARER_PREFIX`, `LOCATION`).
  - `BankingConstants` (`DEFAULT_CURRENCY = "BRL"`, `CASH_SHARD_PREFIX = "SYS-CASH-"`, `TOTAL_CASH_SHARDS = 16`).
- [ ] **Step 2: Substituir literais hardcoded pelos nomes constantes**
  - Atualizar listeners Kafka, publishers, controllers REST e validadores de idempotência.
- [ ] **Step 3: Validar compilação e testes**
  - Rodar `./gradlew test`.

---

### FASE 3: Contratos Canônicos, Mensageria Kafka e Serialização com ObjectMapper

#### Task 3.1: Versionamento de Eventos e Comandos Canônicos no `platform-commons`

**Files:**
- Create: `services/platform-commons/src/main/java/com/bankledger/commons/events/v1/account/AccountOpenedPayload.java`
- Create: `services/platform-commons/src/main/java/com/bankledger/commons/events/v1/ledger/PostJournalCommandPayload.java`
- Create: `services/platform-commons/src/main/java/com/bankledger/commons/events/v1/ledger/JournalPostedPayload.java`
- Create: `services/platform-commons/src/main/java/com/bankledger/commons/events/v1/ledger/JournalRejectedPayload.java`
- Create: `services/platform-commons/src/main/java/com/bankledger/commons/events/v1/ledger/EntryPostedPayload.java`
- Modify: `account-service`, `ledger-service` e `transaction-service` para importar contratos de `platform-commons`.

**Interfaces:**
- Consumes: Contratos de dados compartilhados entre serviços
- Produces: Single Source of Truth para eventos do ecossistema bancário.

- [ ] **Step 1: Criar records canônicos versionados em `platform-commons`**
  - Declarar records imutáveis no pacote `com.bankledger.commons.events.v1.*`.
- [ ] **Step 2: Atualizar produtores e consumidores nos microserviços**
  - Substituir records locais duplicados pelos canônicos compartilhados.
- [ ] **Step 3: Executar testes de serialização e integração**
  - Validar que o fluxo de eventos permanece compatível.

---

#### Task 3.2: Padronização de Consumo Kafka com `ObjectMapper` Direto e Limpeza de Configuração

**Files:**
- Modify: `services/ledger-service/src/main/java/com/bankledger/ledger/adapters/in/kafka/AccountOpenedKafkaConsumer.java`
- Modify: `services/ledger-service/src/main/java/com/bankledger/ledger/adapters/in/kafka/LedgerCommandKafkaConsumer.java`
- Modify: `services/transaction-service/src/main/java/com/bankledger/transaction/adapters/in/kafka/JournalResultKafkaConsumer.java`
- Test: Suítes de teste de integração dos consumidores.

**Interfaces:**
- Consumes: `ConsumerRecord<String, String>` com payload JSON
- Produces: Desserialização direta com `objectMapper.treeToValue(dataNode, TargetClass.class)`.

- [ ] **Step 1: Refatorar parsing no `AccountOpenedKafkaConsumer`**
  - Eliminar o método `extractPayload(JsonNode)` que lê campo por campo.
  - Usar:
    ```java
    JsonNode dataNode = root.has("data") ? root.get("data") : root;
    AccountOpenedPayload payload = objectMapper.treeToValue(dataNode, AccountOpenedPayload.class);
    ```
- [ ] **Step 2: Refatorar parsing no `LedgerCommandKafkaConsumer`**
  - Desserializar `PostJournalCommandPayload` diretamente com `objectMapper.treeToValue(dataNode, PostJournalCommandPayload.class)`.
- [ ] **Step 3: Refatorar parsing no `JournalResultKafkaConsumer`**
  - Desserializar o payload contábil diretamente com `objectMapper.treeToValue(dataNode, JournalResultPayload.class)`.
- [ ] **Step 4: Executar testes dos consumidores**
  - Executar testes com Testcontainers garantindo que todas as mensagens continuam sendo processadas e commitadas com sucesso.

---

### FASE 4: Concorrência Avançada, Resiliência e Auditoria Contábil

#### Task 4.1: Otimização Atômica da Consulta de Balancete (`getTrialBalance`)

**Files:**
- Modify: `services/ledger-service/src/main/java/com/bankledger/ledger/adapters/out/persistence/repository/LedgerAccountSpringDataRepository.java`
- Modify: `services/ledger-service/src/main/java/com/bankledger/ledger/application/service/LedgerQueryService.java`
- Test: `services/ledger-service/src/test/java/com/bankledger/ledger/application/service/LedgerQueryServiceTest.java`
- Test: `services/ledger-service/src/test/java/com/bankledger/ledger/integration/LedgerDoubleEntryIntegrationTest.java`

**Interfaces:**
- Consumes: Tabela `ledger_account`
- Produces: Balancete contábil atômico em query SQL única sem risco de leituras fantasmas.

- [ ] **Step 1: Criar projeção SQL agregada única no repositório**
  - Substituir `sumDebits()` e `sumCredits()` por uma consulta nativa/JPQL atômica única:
    ```java
    @Query(value = """
        SELECT 
            COALESCE(SUM(CASE WHEN a.normal_balance = 'DEBIT' THEN a.balance ELSE 0 END), 0) AS debits,
            COALESCE(SUM(CASE WHEN a.normal_balance = 'CREDIT' THEN a.balance ELSE 0 END), 0) AS credits
        FROM ledger_account a
        """, nativeQuery = true)
    TrialBalanceProjection getTrialBalanceAggregate();
    ```
- [ ] **Step 2: Atualizar `LedgerQueryService` para usar a projeção atômica**
  - Avaliar débitos e créditos sob o mesmo snapshot transacional, garantindo que `difference == 0` seja matematicamente consistente mesmo sob concorrência intensa de escrita.
- [ ] **Step 3: Executar testes de integração**
  - Rodar `LedgerDoubleEntryIntegrationTest` e validar o assert `difference == 0`.

---

#### Task 4.2: Documentação de Arquitetura de Alta Concorrência (ADRs 0004, 0005 e 0006)

**Files:**
- Create: `docs/adr/0004-outbox-multi-instance-concurrency.md`
- Create: `docs/adr/0005-distributed-idempotency-redis-postgres.md`
- Create: `docs/adr/0006-reactive-auth-service-and-token-introspection.md`

**Interfaces:**
- Consumes: Decisões técnicas e avaliações arquiteturais
- Produces: Registros formais de arquitetura (Architecture Decision Records) aprovados.

- [ ] **Step 1: Criar ADR 0004 — Concorrência Multi-Instância do `OutboxPoller`**
  - Documentar como o `FOR UPDATE SKIP LOCKED` nativo do PostgreSQL já garante a divisão não-bloqueante de tarefas entre múltiplos nós sem duplicação de mensagens.
  - Documentar o padrão de envio em 2 fases para evitar retenção de conexão JDBC sob latência de rede no Kafka.
- [ ] **Step 2: Criar ADR 0005 — Idempotência Híbrida de Dois Níveis (Redis + PostgreSQL)**
  - Detalhar o fluxo `SET NX EX 60` no Redis para rejeição sub-milissegundo de concorrência com HTTP 409 sem onerar o banco, combinado com a persistência de longo prazo no PostgreSQL para auditoria.
- [ ] **Step 3: Criar ADR 0006 — Microserviço de Autenticação Reativo (`auth-service`) e Validação Criptográfica**
  - Especificar a separação do login em um microsserviço reativo independente.
  - Definir o padrão de mercado de validação descentralizada via chaves públicas assimétricas (JWKS / RS256) em memória nos demais serviços, eliminando chamadas HTTP síncronas de validação.

---

### FASE 5: Otimização de JVM nos Contêineres e Validação Final

#### Task 5.1: Ativação de JVM Compact Object Headers e Flags OOM nos Dockerfiles

**Files:**
- Modify: `services/account-service/Dockerfile`
- Modify: `services/ledger-service/Dockerfile`
- Modify: `services/transaction-service/Dockerfile`
- Modify: `services/query-service/Dockerfile`

**Interfaces:**
- Consumes: Java 25 Runtime Image
- Produces: Redução de até 22% no heap e encerramento determinístico em caso de OOM.

- [ ] **Step 1: Atualizar ENTRYPOINT dos Dockerfiles**
  - Adicionar as flags `-XX:+UseCompactObjectHeaders` (JEP 534) e `-XX:+ExitOnOutOfMemoryError` a todos os serviços.
- [ ] **Step 2: Validar sintaxe com `docker compose config`**
  - Executar `docker compose -f infra/docker/docker-compose.yml config --quiet`.

---

#### Task 5.2: Validação Completa de Ponta a Ponta

**Files:**
- Execute: Todos os microserviços e testes

- [ ] **Step 1: Executar `./gradlew test` em todo o monorepo**
  - Garantir que `platform-commons`, `account-service`, `ledger-service` e `transaction-service` passem com 100% de sucesso.
- [ ] **Step 2: Verificar governança ArchUnit**
  - Confirmar zero violações arquiteturais em todos os serviços.
- [ ] **Step 3: Executar testes de build do frontend**
  - Rodar `npx tsc --noEmit` e `npm run build` em `web/`.

---

## Critérios de Conclusão

1. Todas as tarefas de 1.1 a 5.2 concluídas e revisadas com sucesso.
2. Nenhuma quebra de regressão em testes unitários ou de integração.
3. Repositório limpo, documentado com ADRs 0004 a 0006 e pronto para o Slice 3.
