# Plano de Melhorias Técnicas, Refatoração e Arquitetura dos Serviços

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implementar melhorias arquiteturais e de código de padrão corporativo no Bank Ledger: desacoplamento do motor contábil via Strategy Pattern, serialização direta de eventos Kafka via `ObjectMapper`, centralização de contratos no `platform-commons`, avaliação e mitigação de riscos de concorrência (`getTrialBalance`, `OutboxPoller`, Idempotência com Redis), remoção de sufixos `Jpa` em entidades, limpeza de anotações desnecessárias (`@Repository`, `KafkaConsumerConfig`), substituição de strings mágicas por constantes, e desenho arquitetural de um microserviço de autenticação reativo (`auth-service`).

**Architecture:** Aplicação de padrões de design corporativos (GoF Strategy, Two-Tier Idempotency, Centralized Event Contracts, Reactive Identity Provider) mantendo a arquitetura hexagonal e governança estrita de ArchUnit, com separação clara de responsabilidades, resiliência em alta concorrência e zero acoplamento indesejado entre domínios.

**Tech Stack:** Java 25, Spring Boot 4.1.2, Spring Data JPA, Spring Kafka, PostgreSQL 18, Confluent Kafka 8.4.0, Jackson 3 (`tools.jackson`), ArchUnit 1.4, Testcontainers 1.20.4, Docker Compose.

**Spec:** Baseado na revisão de engenharia de software e feedback do usuário em `docs/plano_desenvolvimento_ledger.md`, `docs/adr/0001-cash-sharding-hybrid-allocation.md`, `docs/adr/0002-ledger-pessimistic-locking-anti-deadlock.md` e `docs/adr/0003-async-saga-choreography-transaction-ledger.md`.

## Global Constraints

- Manter 100% de conformidade com os testes de arquitetura ArchUnit em todos os microserviços.
- Todas as injeções de dependência devem continuar via construtor com campos `final` (ou Lombok `@RequiredArgsConstructor`); proibido uso de `@Autowired` em atributos ou métodos.
- O motor de partidas dobradas deve manter a invariante estrita: $\sum \text{Débitos} == \sum \text{Créditos} == \text{totalAmount} > 0$ e saldo não-negativo para contas de clientes.
- Preservar o bloqueio pessimista ordenado por UUID ($\min(idA, idB)$ antes de $\max(idA, idB)$) para prevenir deadlocks.
- Preservar a garantia de commit manual no Kafka (`AckMode.MANUAL_IMMEDIATE` com confirmação estritamente após o sucesso da transação no banco).
- Todas as suítes de teste de unidade e integração (Testcontainers) existentes devem continuar 100% verdes após as refatorações.

## Review Focus

1. **Strategy Pattern:** Garantir que a extração das estratégias de postagem (`DepositPostingStrategy`, `WithdrawalPostingStrategy`, `TransferPostingStrategy`) não quebre a orquestração central de anti-deadlock, idempotência e publicação no Outbox.
2. **ObjectMapper no Kafka:** Garantir que a desserialização tipada direta trate payloads CloudEvents/EventEnvelope (`{"data": ...}`) e payloads puros sem regressão para eventos legados.
3. **Atomicidade do Trial Balance:** Evitar leitura não-atômica e degradação de performance por travamento ou leitura fantasma em `getTrialBalance` sob alta concorrência.
4. **Renomeação de Entidades:** Garantir que a remoção do sufixo `Jpa` em `*JpaEntity` atualize todos os mapeamentos Hibernate/JPA, repositórios, converters e regras ArchUnit sem quebrar testes.
5. **Idempotência e Outbox:** Validar as avaliações técnicas de concorrência multi-instância (PostgreSQL `SKIP LOCKED` vs Redis) fornecendo diretrizes claras de escalabilidade.

---

### FASE 1: Refatoração de Domínio e Padrões Estruturais

#### Task 1: Strategy Pattern no `PostJournalService` e Desacoplamento da Classe Pai

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
- Produces: `Journal`, `Entry`, `JournalPostedEvent`, `JournalRejectedEvent`

- [ ] **Step 1: Criar interfaces e records da estratégia**
  - Definir `JournalPostingStrategy`:
    ```java
    public interface JournalPostingStrategy {
        boolean supports(String transactionType);
        PostingAccountsPair resolveAccountsToLock(PostJournalCommand command);
        PostingEntriesResult generateEntries(
                PostJournalCommand command,
                LedgerAccount firstAccount,
                LedgerAccount secondAccount,
                UUID journalId,
                Instant now
        );
    }
    ```
  - Definir records `PostingAccountsPair(UUID accountIdA, UUID accountIdB)` e `PostingEntriesResult(List<Entry> entries, String rejectionCode)`.

- [ ] **Step 2: Implementar as estratégias individuais**
  - Implementar `DepositPostingStrategy`:
    - Resolve shard de caixa via `cashShardResolver` e conta destino.
    - Aplica `DEBIT` no caixa e `CREDIT` na conta do cliente.
  - Implementar `WithdrawalPostingStrategy`:
    - Resolve conta origem e shard de caixa via `cashShardResolver`.
    - Aplica `DEBIT` na conta do cliente e `CREDIT` no caixa.
    - Trata `InsufficientFundsException` retornando `rejectionCode = "INSUFFICIENT_FUNDS"`.
  - Implementar `TransferPostingStrategy`:
    - Resolve conta origem e conta destino.
    - Aplica `DEBIT` na conta origem e `CREDIT` na conta destino.
    - Trata `InsufficientFundsException` retornando `rejectionCode = "INSUFFICIENT_FUNDS"`.

- [ ] **Step 3: Refatorar `PostJournalService` para injetar `List<JournalPostingStrategy>`**
  - Injetar lista de estratégias no construtor.
  - Localizar a estratégia adequada via `strategies.stream().filter(s -> s.supports(type)).findFirst()`.
  - Delegar a resolução das contas e a geração das partidas dobradas à estratégia.
  - Manter na classe pai a responsabilidade transversal única:
    1. Verificação de idempotência no banco.
    2. Coordenação do lock pessimista ordenado por UUID anti-deadlock.
    3. Persistência transacional do Journal e Entries.
    4. Criação e publicação de eventos no Outbox.

- [ ] **Step 4: Executar suíte de testes unitários e de integração**
  - Executar `./gradlew :services:ledger-service:test`.
  - Confirmar que todos os testes unitários e o teste e2e `LedgerDoubleEntryIntegrationTest` continuam 100% verdes.

---

#### Task 2: Remoção da Convenção `Jpa` nas Entidades e Limpeza de `@Repository` em Interfaces

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
- Modify: Mapeamentos nos adaptadores de persistência e testes de repositório correspondentes.

**Interfaces:**
- Consumes: Entidades JPA em camada `adapters.out.persistence.entity`
- Produces: Nomes padronizados `*Entity` e interfaces Spring Data limpas sem anotação redundante.

- [ ] **Step 1: Renomear classes de entidades e atualizar anotações `@Table`**
  - Renomear os arquivos e classes substituindo `*JpaEntity` por `*Entity`.
  - Garantir que `@Entity` e `@Table(name = "...")` permaneçam inalterados.
- [ ] **Step 2: Remover anotação `@Repository` das interfaces Spring Data**
  - Remover `@Repository` de todas as interfaces que estendem `JpaRepository`.
  - Justificativa arquitetural e SonarQube: interfaces que estendem `JpaRepository` são detectadas automaticamente pelo scanning do Spring Data (`@EnableJpaRepositories`); o uso de `@Repository` em interfaces é redundante e acarreta alertas de código (ex: SonarQube `java:S1130` / `java:S6830`).
- [ ] **Step 3: Atualizar adaptadores de persistência e repositórios**
  - Atualizar tipos genéricos em `JpaRepository<*Entity, UUID>`.
  - Atualizar métodos `toDomain()` e `fromDomain()` nos adaptadores de persistência.
- [ ] **Step 4: Executar testes e regras ArchUnit**
  - Rodar `./gradlew test` em todos os microserviços.
  - Verificar que as regras ArchUnit (`HexagonalArchitectureTest`) continuam passando sem violações.

---

#### Task 3: Constantes Centralizadas para Strings Mágicas e Governança de Código

**Files:**
- Create: `services/platform-commons/src/main/java/com/bankledger/commons/constant/KafkaTopics.java`
- Create: `services/platform-commons/src/main/java/com/bankledger/commons/constant/BankingErrorCodes.java`
- Create: `services/platform-commons/src/main/java/com/bankledger/commons/constant/HttpHeaderConstants.java`
- Create: `services/platform-commons/src/main/java/com/bankledger/commons/constant/BankingConstants.java`
- Modify: Consumers, publishers e controllers que utilizam strings literais hardcoded.

**Interfaces:**
- Consumes: Constantes reutilizáveis
- Produces: Código limpo, manutenível e imune a erros de digitação em tópicos e cabeçalhos.

- [ ] **Step 1: Criar classes utilitárias de constantes em `platform-commons`**
  - `KafkaTopics`:
    - `ACCOUNT_EVENTS_V1 = "account.events.v1"`
    - `LEDGER_COMMANDS_V1 = "ledger.commands.v1"`
    - `LEDGER_JOURNAL_RESULTS_V1 = "ledger.journal-results.v1"`
    - `LEDGER_ACCOUNT_EVENTS_V1 = "ledger.account-events.v1"`
    - `LEDGER_ENTRIES_V1 = "ledger.entries.v1"`
    - `TRANSACTION_EVENTS_V1 = "transaction.events.v1"`
  - `BankingErrorCodes`:
    - `INSUFFICIENT_FUNDS = "INSUFFICIENT_FUNDS"`
    - `ACCOUNT_NOT_FOUND = "ACCOUNT_NOT_FOUND"`
    - `ACCOUNT_BLOCKED = "ACCOUNT_BLOCKED"`
    - `LIMIT_EXCEEDED = "LIMIT_EXCEEDED"`
    - `IDEMPOTENCY_CONFLICT = "IDEMPOTENCY_CONFLICT"`
    - `IDEMPOTENCY_REUSED = "IDEMPOTENCY_KEY_REUSED"`
  - `HttpHeaderConstants`:
    - `IDEMPOTENCY_KEY = "Idempotency-Key"`
    - `AUTHORIZATION = "Authorization"`
    - `BEARER_PREFIX = "Bearer "`
    - `LOCATION = "Location"`
  - `BankingConstants`:
    - `DEFAULT_CURRENCY = "BRL"`
    - `CASH_SHARD_PREFIX = "SYS-CASH-"`
    - `TOTAL_CASH_SHARDS = 16`

- [ ] **Step 2: Substituir strings literais nas classes de aplicação e adaptadores**
  - Atualizar listeners Kafka, outbox publishers, controllers REST e validadores de idempotência para consumir as constantes.
- [ ] **Step 3: Validar compilação e testes**
  - Executar `./gradlew test` para certificar integridade.

---

### FASE 2: Mensageria, Kafka e Serialização Robusta

#### Task 4: Versionamento de Eventos e Comandos Canônicos no `platform-commons`

**Files:**
- Create: `services/platform-commons/src/main/java/com/bankledger/commons/events/v1/account/AccountOpenedPayload.java`
- Create: `services/platform-commons/src/main/java/com/bankledger/commons/events/v1/ledger/PostJournalCommandPayload.java`
- Create: `services/platform-commons/src/main/java/com/bankledger/commons/events/v1/ledger/JournalPostedPayload.java`
- Create: `services/platform-commons/src/main/java/com/bankledger/commons/events/v1/ledger/JournalRejectedPayload.java`
- Create: `services/platform-commons/src/main/java/com/bankledger/commons/events/v1/ledger/EntryPostedPayload.java`
- Modify: `account-service`, `ledger-service` e `transaction-service` para importar os contratos de eventos de `platform-commons`.

**Interfaces:**
- Consumes: Contratos de dados compartilhados entre serviços
- Produces: Única fonte da verdade (Single Source of Truth) para os tópicos Kafka.

- [ ] **Step 1: Criar os DTOs de eventos e comandos canônicos em `platform-commons`**
  - Adicionar records imutáveis com serialização Jackson 3.
  - Documentar versionamento explícito no pacote `events.v1.*`.
- [ ] **Step 2: Atualizar os produtores e consumidores nos microserviços**
  - Substituir DTOs duplicados locais pelos DTOs canônicos de `platform-commons`.
- [ ] **Step 3: Validar compatibilidade binária e contratos**
  - Executar os testes de serialização e integração.

---

#### Task 5: Padronização de Consumo Kafka com `ObjectMapper` Direto (Sem parsing item-a-item)

**Files:**
- Modify: `services/ledger-service/src/main/java/com/bankledger/ledger/adapters/in/kafka/AccountOpenedKafkaConsumer.java`
- Modify: `services/ledger-service/src/main/java/com/bankledger/ledger/adapters/in/kafka/LedgerCommandKafkaConsumer.java`
- Modify: `services/transaction-service/src/main/java/com/bankledger/transaction/adapters/in/kafka/JournalResultKafkaConsumer.java`
- Test: Testes de unidade e de integração dos consumidores correspondentes.

**Interfaces:**
- Consumes: `ConsumerRecord<String, String>` com payload JSON
- Produces: Objeto tipado via `objectMapper.treeToValue(dataNode, TargetClass.class)` ou `objectMapper.readValue(json, TargetClass.class)`.

- [ ] **Step 1: Refatorar parsing no `AccountOpenedKafkaConsumer`**
  - Substituir o método manual `extractPayload(JsonNode)` que lê campo por campo (`getRequiredUuid`, `getOptionalText`, etc.) por:
    ```java
    JsonNode dataNode = root.has("data") ? root.get("data") : root;
    AccountOpenedPayload payload = objectMapper.treeToValue(dataNode, AccountOpenedPayload.class);
    ```
  - Validar os campos obrigatórios via Bean Validation (`@NotNull`, `@NotBlank`) ou no próprio construtor canônico do record.
- [ ] **Step 2: Refatorar parsing no `LedgerCommandKafkaConsumer`**
  - Desserializar `PostJournalCommandPayload` diretamente com `objectMapper.treeToValue(dataNode, PostJournalCommandPayload.class)`.
- [ ] **Step 3: Refatorar parsing no `JournalResultKafkaConsumer`**
  - Desserializar o payload de resultado contábil diretamente com `objectMapper.treeToValue(dataNode, JournalResultPayload.class)`.
- [ ] **Step 4: Executar testes de regressão dos consumidores**
  - Executar os testes dos consumers (`AccountOpenedKafkaConsumerTest`, `LedgerCommandKafkaConsumerTest`, `JournalResultKafkaConsumerTest`).
  - Garantir que todos os cenários (CloudEvents com envelope, JSON plano, headers `ce_id`) continuem funcionando com 100% de sucesso.

---

#### Task 6: Avaliação e Limpeza de `KafkaConsumerConfig` vs `application.yml`

**Files:**
- Analyze: `services/ledger-service/src/main/java/com/bankledger/ledger/config/KafkaConsumerConfig.java`
- Analyze: `services/transaction-service/src/main/java/com/bankledger/transaction/config/KafkaConsumerConfig.java`
- Modify: `application.yml` em todos os serviços.

**Interfaces:**
- Consumes: Configuração do Spring Kafka
- Produces: Configuração limpa, centralizada e idiomática sem duplicação desnecessária.

- [ ] **Step 1: Análise técnica da configuração**
  - *Contexto:* O Spring Boot provê `ConcurrentKafkaListenerContainerFactoryConfigurer` que lê automaticamente a propriedade:
    ```yaml
    spring:
      kafka:
        listener:
          ack-mode: manual_immediate
        consumer:
          enable-auto-commit: false
    ```
  - Quando um bean customizado `@Bean public ConcurrentKafkaListenerContainerFactory kafkaListenerContainerFactory(...)` é criado manualmente declarando apenas `factory.setConsumerFactory(...)` e `factory.getContainerProperties().setAckMode(...)`, ele **descarta** as configurações automáticas do Spring Boot (como observabilidade com Micrometer, backoff, interceptors e concorrência).
- [ ] **Step 2: Testar se a remoção da classe manual preserva o `AckMode.MANUAL_IMMEDIATE`**
  - Configurar `ConcurrentKafkaListenerContainerFactoryConfigurer` caso se queira customização avançada, ou confiar na propriedade `spring.kafka.listener.ack-mode: manual_immediate` validada pelo teste de container.
  - Atualizar os testes de configuração para validar a presença e o comportamento do `AckMode`.

---

### FASE 3: Alta Concorrência, Resiliência e Auditoria Contábil

#### Task 7: Avaliação de Risco e Otimização Atômica do `getTrialBalance`

**Files:**
- Modify: `services/ledger-service/src/main/java/com/bankledger/ledger/adapters/out/persistence/repository/LedgerAccountSpringDataRepository.java`
- Modify: `services/ledger-service/src/main/java/com/bankledger/ledger/application/service/LedgerQueryService.java`
- Test: `services/ledger-service/src/test/java/com/bankledger/ledger/application/service/LedgerQueryServiceTest.java`
- Document: Atualização do relatório de concorrência em `docs/adr/0002-ledger-pessimistic-locking-anti-deadlock.md`

**Interfaces:**
- Consumes: Tabela `ledger_account`
- Produces: `TrialBalanceResult` atômico em query única sem phantom reads.

- [ ] **Step 1: Análise de Risco (Por que a implementação atual é perigosa em produção?)**
  - **Problema 1: Leitura Fantasma (Phantom Discrepancy):** Atualmente, `LedgerQueryService.getTrialBalance` executa duas queries separadas em `READ COMMITTED`:
    1. `sumDebits()` $\to$ `SELECT SUM(balance) FROM ledger_account WHERE normal_balance = 'DEBIT'`
    2. `sumCredits()` $\to$ `SELECT SUM(balance) FROM ledger_account WHERE normal_balance = 'CREDIT'`
    Se uma transação comitar entre a query 1 e a query 2, a soma de débitos e créditos reportará um falso desbalanceamento (`difference != 0`) gerando alertas falsos para a equipe de compliance e auditoria!
  - **Problema 2: Table Scan e Contenção em Alta Concorrência:** Com milhões de contas, escanear duas vezes a tabela `ledger_account` consome I/O e locks de leitura compartilhados que podem degradar a latência das operações de escrita concorrentes.
- [ ] **Step 2: Implementar Query Única Atômica no Banco de Dados**
  - Substituir as duas chamadas por uma projeção SQL agregada única:
    ```sql
    SELECT 
        COALESCE(SUM(CASE WHEN normal_balance = 'DEBIT' THEN balance ELSE 0 END), 0) AS total_debits,
        COALESCE(SUM(CASE WHEN normal_balance = 'CREDIT' THEN balance ELSE 0 END), 0) AS total_credits
    FROM ledger_account
    ```
  - Executada sob isolamento de snapshot único, garantindo atomicidade matemática estrita ($\text{total\_debits} - \text{total\_credits} == 0$).
- [ ] **Step 3: Documentar Estratégia de Escala Empresarial (Fechamento Diário / Read Replica)**
  - Para volumes gigantescos (> 100 milhões de contas), a auditoria em tempo real deve rodar contra uma réplica de leitura (Read Replica) ou através de snapshot incremental no fechamento diário contábil (End-of-Day Ledger Reconciliation), evitando onerar a base transacional OLTP primária.

---

#### Task 8: Avaliação da Concorrência do `OutboxPoller` em Múltiplas Instâncias

**Files:**
- Analyze: `services/platform-commons/src/main/java/com/bankledger/commons/outbox/OutboxPoller.java`
- Analyze: `services/platform-commons/src/main/java/com/bankledger/commons/outbox/OutboxEventRepository.java`
- Document: Relatório de análise em `docs/adr/0004-outbox-multi-instance-concurrency.md`

**Interfaces:**
- Consumes: Múltiplas instâncias simultâneas do microserviço
- Produces: Garantia de publicação sem duplicação e sem travamento entre pods concorrentes.

- [ ] **Step 1: Avaliação Técnica da Implementação Atual**
  - *Dúvida do usuário:* "O OutboxPoller não tem problema de concorrência entre múltiplas instâncias?"
  - *Veredito Técnico:* **A implementação atual já utiliza `FOR UPDATE SKIP LOCKED` nativo do PostgreSQL!**
    ```sql
    SELECT * FROM outbox_event
    WHERE published_at IS NULL
    ORDER BY created_at ASC
    LIMIT :batchSize
    FOR UPDATE SKIP LOCKED
    ```
  - *Como funciona na prática:* Quando a Instância 1 executa a query e trava as linhas 1 a 50, a Instância 2 rodando simultaneamente **ignora (SKIP)** essas 50 linhas travadas e pega imediatamente as linhas 51 a 100. Nenhuma instância bloqueia a outra e nenhuma instância processa a mesma linha!
- [ ] **Step 2: Riscos Identificados e Melhorias Necessárias**
  - **Risco 1 (Ordenação por Agregado):** Se a Instância 1 estiver processando o evento 1 da Conta A e a Instância 2 processar o evento 2 da Conta A mais rápido, o Kafka pode receber o evento 2 antes do evento 1.
    *Mitigação:* Assegurar particionamento no Kafka pela chave de agregação (`aggregateId`) e garantir que eventos do mesmo agregado sejam agrupados na mesma transação de outbox.
  - **Risco 2 (Segurar Conexão JDBC durante o envio ao Kafka):** O `pollAndPublish` mantém a transação de banco aberta enquanto executa `publisher.publish(event)`. Se a rede do Kafka ficar lenta, a conexão com o PostgreSQL fica presa.
    *Mitigação:* Padrão de 2 fases: (1) Bloquear e marcar eventos como `IN_PUBLISH` no banco e fechar a transação rápida; (2) Enviar ao Kafka de forma assíncrona; (3) Atualizar `published_at` após a confirmação do broker.
- [ ] **Step 3: Documentar ADR 0004 com a análise e recomendações**

---

#### Task 9: Avaliação e Design de Idempotência Distribuída com Redis + Postgres

**Files:**
- Create: `docs/adr/0005-distributed-idempotency-redis-postgres.md`
- Design: Arquitetura de Dois Níveis (Two-Tier Idempotency) para alta taxa de requisições.

**Interfaces:**
- Consumes: Requisições HTTP concorrentes em cluster distribuído
- Produces: Latência sub-milissegundo para rejeição de duplicatas sem sobrecarga no PostgreSQL.

- [ ] **Step 1: Análise Comparativa (Postgres vs Redis)**
  - *PostgreSQL Puro (Atual):* Robusto, ACID, persiste por 24h, porém cada requisição precisa abrir uma transação no banco (`REQUIRES_NEW`) e sofrer contenção de chave primária composta. Sob picos de 50.000 req/s, o pool de conexões do banco pode esgotar.
  - *Redis Distribuído:* Operação em memória atômica `SET idempotency:{scope}:{key} "IN_PROGRESS" NX EX 60` em < 1ms. Absorve 100k+ req/s com custo computacional mínimo.
- [ ] **Step 2: Desenho da Arquitetura Híbrida (Two-Tier)**
  - **Camada 1 (Borda Rápida - Redis):** Ao chegar a requisição, o gateway ou o interceptor executa `SET NX` no Redis com TTL de 60 segundos. Se retornar false, responde imediatamente HTTP 409 Conflict sem abrir transação no banco.
  - **Camada 2 (Persistência Durável - PostgreSQL):** Ao concluir o processamento, o resultado final (status HTTP, corpo da resposta, transactionId) é gravado no PostgreSQL para auditoria de longo prazo e cacheado no Redis com TTL de 24h para replays instantâneos.
- [ ] **Step 3: Registrar ADR 0005 com diagramas de sequência e estratégia de rollout**

---

### FASE 4: Arquitetura do Microserviço de Autenticação / Login Dedicado Reativo

#### Task 10: Especificação Arquitetural e Plano do `auth-service` Reativo

**Files:**
- Create: `docs/adr/0006-reactive-auth-service-and-token-introspection.md`
- Document: Diagrama arquitetural e estratégia de desacoplamento do `account-service`.

**Interfaces:**
- Consumes: Credenciais do cliente (CPF + Senha)
- Produces: Emissão de Tokens JWT assinados assimetricamente (RS256 / Ed25519) e validação reativa.

- [ ] **Step 1: Justificativa Arquitetural da Separação**
  - Atualmente o `account-service` acumula duas responsabilidades distintas:
    1. Gestão de Contas e Clientes (Ciclo de vida cadastral, limites, agência/conta).
    2. Autenticação e Emissão de Tokens (Login, hashing de senha, segurança de credenciais).
  - Separar em um `auth-service` (ou `identity-service`) dedicado permite isolar dados sensíveis de credenciais, escalar o login independentemente das contas e adotar arquitetura puramente reativa (Spring WebFlux / R2DBC).
- [ ] **Step 2: Comparativo de Validação Entre Microserviços (Chamada Reativa vs Chave Pública)**
  - **Opção A: Introspecção Reativa via HTTP (`POST /auth/verify`):**
    - Cada microserviço faz uma chamada HTTP reativa via WebClient para o `auth-service` a cada requisição.
    - *Desvantagem:* Adiciona 5ms a 20ms de latência a cada chamada na borda e cria um ponto único de falha de rede (se o `auth-service` oscilar, o banco todo para).
  - **Opção B: Padrão de Mercado — Criptografia Assimétrica (JWKS / RS256) com Validação Local:**
    - O `auth-service` assina o JWT com sua **Chave Privada** RSA/ECDSA e expõe a **Chave Pública** em um endpoint público seguro (`/.well-known/jwks.json`).
    - Cada microserviço (`transaction-service`, `ledger-service`, `account-service`) baixa a chave pública uma única vez (com cache local) e valida a assinatura criptográfica em nanossegundos em memória, sem fazer nenhuma chamada de rede!
    - *Vantagem:* Desempenho máximo, zero latência adicional e alta disponibilidade garantida.
  - **Opção C: Híbrido Reativo (Validação Local com Revogação em Tempo Real via Redis):**
    - Validação de assinatura em memória + verificação rápida no Redis reativo de lista de tokens revogados / logout.
- [ ] **Step 3: Registrar ADR 0006 com especificações completas de endpoints e modelos de dados para o novo microserviço**

---

## Critérios de Aceite Globais do Plano

1. **Strategy Pattern:** `PostJournalService` orquestra estratégias desacopladas para Depósito, Saque e Transferência com 100% dos testes passando.
2. **Kafka ObjectMapper:** 0 parsing manual campo-a-campo nos consumers Kafka; todos usando desserialização direta via Jackson 3.
3. **Contratos Canônicos:** Eventos e comandos versionados em `platform-commons`.
4. **Nomenclatura Limpa:** Todas as entidades renomeadas de `*JpaEntity` para `*Entity` e anotações `@Repository` removidas de interfaces Spring Data.
5. **Eliminação de Strings Mágicas:** Constantes tipadas centralizadas para tópicos Kafka, códigos de erro e cabeçalhos HTTP.
6. **Auditoria Contábil Atômica:** Query única do `getTrialBalance` eliminando qualquer risco de phantom read.
7. **Documentação e ADRs:** ADRs 0004, 0005 e 0006 aprovadas abordando a concorrência do OutboxPoller, Idempotência com Redis e o novo `auth-service` reativo.
