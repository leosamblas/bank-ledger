# Slice 1 Incremental Refinements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Aplicar 9 ajustes incrementais de qualidade e produção no microsserviço `account-service` e `platform-commons`: Actuator para health checks, documentação OpenAPI/Swagger interativa, isolamento de JPA entities em subpacote dedicado, desacoplamento assíncrono e timeout configurável do Kafka outbox publisher, configuração de ACK manual no Kafka, externalização de variáveis de ambiente para o outbox poller, eliminação de `@Autowired` em favor de injeção por construtor, padronização em inglês de código/logs/docs (mantendo mensagens de erro em português) e poda de comentários verbosos.

**Architecture:** Refinamento arquitetural sobre o Slice 1 mantendo conformidade estrita com Arquitetura Hexagonal governada pelo ArchUnit. A camada de persistência ganha separação explícita entre adaptadores, entidades JPA (`entity`) e repositórios Spring Data (`repository`). A infraestrutura de mensageria reforça o controle transacional com ACK manual e publicação assíncrona não bloqueante. A monitorabilidade é entregue via Spring Boot Actuator e a exploração de endpoints via SpringDoc OpenAPI (Swagger UI).

**Tech Stack:** Java 25, Spring Boot 4.1.2 (Spring Data JPA, Spring Security 7, Spring Validation, Spring Kafka, Spring Boot Actuator, SpringDoc OpenAPI 2.8.5), PostgreSQL 18, Apache Kafka KRaft 8.4.0, Lombok, ArchUnit, Testcontainers, Docker Compose, Nginx.

**Spec:** `api-contracts/account-service.yaml`, `docs/plano_desenvolvimento_ledger.md` (Slice 1) e considerações do usuário.

---

## Global Constraints

- **Eliminação de `@Autowired`:** Não utilizar anotação `@Autowired` em construtores ou campos. Utilizar injeção de dependência por construtor com campos `final` (ou Lombok `@RequiredArgsConstructor`).
- **Isolamento de Entidades JPA:** Entidades JPA devem residir estritamente no subpacote `com.bankledger.account.adapters.out.persistence.entity` e repositórios em `com.bankledger.account.adapters.out.persistence.repository`.
- **Desacoplamento de Timeout Kafka:** A API REST nunca bloqueia esperando resposta do Kafka; o outbox publisher em background deve operar de forma assíncrona com timeout configurável via variável de ambiente `${OUTBOX_POLLER_PUBLISH_TIMEOUT_MS:2000}`.
- **Kafka ACK Manual:** Configuração de listeners e fábricas com `AckMode.MANUAL_IMMEDIATE` e controle explícito de commit no código.
- **Configurações por Variáveis de Ambiente:** Propriedades do outbox poller e segurança JWT devem ser externalizadas em `application.yml` com suporte a variáveis de ambiente e valores padrão seguros.
- **Spring Boot Actuator:** Healthcheck padronizado sob `/actuator/health` (removendo controller de health customizado).
- **Swagger / OpenAPI:** SpringDoc OpenAPI ativo e acessível publicamente em `/swagger-ui.html`, `/swagger-ui/**` e `/v3/api-docs/**`.
- **Idioma do Código:** Todo o código-fonte, nomes de classes, métodos, variáveis, logs e documentação técnica devem estar em **Inglês**. Mensagens de erro e validações voltadas ao usuário final permanecem em **Português do Brasil**.
- **Comentários Limpos e Objetivos:** Eliminar comentários históricos, cabeçalhos redundantes e notas obsoletas; manter javadocs concisos e diretos.

---

## Review Focus

1. **Injeção de Dependências sem `@Autowired`:** Verificar se nenhuma classe em `com.bankledger.account` ou `com.bankledger.commons` utiliza `@Autowired`.
2. **Saúde do Container via Actuator:** `GET /actuator/health` deve responder HTTP 200 `{"status":"UP"}` publicamente e atender ao healthcheck do `docker-compose.yml`.
3. **Exploração Swagger UI:** Acesso a `/swagger-ui.html` e `/v3/api-docs` sem exigência de autenticação, com esquema Bearer JWT configurado.
4. **Isolamento de Pacotes de Persistência:** Regras ArchUnit devem validar que entidades JPA residem em `persistence.entity` e que o domínio continua com 0 dependências de infraestrutura.
5. **Comportamento Assíncrono do Kafka Publisher:** O envio para o Kafka pelo outbox publisher não pode estagnar a Virtual Thread do poller além do timeout configurável (`publishTimeoutMs`).

---

## Estrutura de Arquivos Planejada

```text
services/account-service/
├── build.gradle (adiciona spring-boot-starter-actuator e springdoc-openapi-starter-webmvc-ui)
└── src/
    ├── main/
    │   ├── java/com/bankledger/account/
    │   │   ├── adapters/
    │   │   │   ├── in/rest/
    │   │   │   │   ├── AccountController.java (sem @Autowired, docs em inglês)
    │   │   │   │   ├── AuthController.java (sem @Autowired, docs em inglês)
    │   │   │   │   └── GlobalExceptionHandler.java (mensagens de erro em PT-BR)
    │   │   │   ├── out/persistence/
    │   │   │   │   ├── entity/ (CustomerJpaEntity.java, AccountJpaEntity.java)
    │   │   │   │   ├── repository/ (CustomerSpringDataRepository.java, AccountSpringDataRepository.java)
    │   │   │   │   ├── CustomerPersistenceAdapter.java (sem @Autowired)
    │   │   │   │   ├── AccountPersistenceAdapter.java (sem @Autowired)
    │   │   │   │   └── OutboxPersistenceAdapter.java (sem @Autowired)
    │   │   │   └── out/kafka/
    │   │   │       ├── KafkaOutboxPublisher.java (assíncrono com timeout configurável)
    │   │   │       └── OutboxPollerLifecycle.java (sem @Autowired, injeta OutboxPollerProperties)
    │   │   ├── config/
    │   │   │   ├── OpenApiConfig.java (Swagger UI e SecurityScheme Bearer)
    │   │   │   ├── KafkaConsumerConfig.java (AckMode.MANUAL_IMMEDIATE)
    │   │   │   ├── OutboxPollerProperties.java (@ConfigurationProperties)
    │   │   │   └── SecurityConfig.java (libera /actuator/** e /swagger-ui/**)
    │   └── resources/
    │       └── application.yml (management actuator, springdoc, kafka, poller env vars)
    └── test/
        ├── java/com/bankledger/account/
        │   ├── arch/HexagonalArchitectureTest.java (regras com persistence.entity)
        │   ├── config/OpenApiIntegrationTest.java (valida Swagger e OpenAPI docs)
        │   └── integration/ActuatorHealthIntegrationTest.java (valida /actuator/health)
```

---

## Roteiro de Execução das Tarefas

### Task 1: Spring Boot Actuator & SpringDoc OpenAPI (Swagger UI)

**Files:**
- Modify: `services/account-service/build.gradle` (adiciona actuator e springdoc-openapi)
- Create: `services/account-service/src/main/java/com/bankledger/account/config/OpenApiConfig.java`
- Delete: `services/account-service/src/main/java/com/bankledger/account/adapters/in/rest/HealthController.java`
- Delete: `services/account-service/src/test/java/com/bankledger/account/adapters/in/rest/HealthControllerTest.java`
- Modify: `services/account-service/src/main/java/com/bankledger/account/config/SecurityConfig.java`
- Modify: `services/account-service/src/main/resources/application.yml`
- Modify: `infra/docker/docker-compose.yml` (atualiza healthcheck para `/actuator/health`)
- Test: `services/account-service/src/test/java/com/bankledger/account/config/ActuatorAndSwaggerTest.java`

**Interfaces:**
- Consumes: Spring Boot Actuator, SpringDoc OpenAPI
- Produces: `/actuator/health` respondendo UP; `/swagger-ui/index.html` e `/v3/api-docs` disponíveis publicamente.

- [ ] **Step 1: Escrever teste de integração para Actuator e Swagger**
  - Criar `ActuatorAndSwaggerTest.java` com MockMvc testando:
    1. `GET /actuator/health` retorna HTTP 200 com `status: "UP"`.
    2. `GET /v3/api-docs` retorna HTTP 200 com JSON contendo OpenAPI 3.0 e componentes do `account-service`.
    3. `GET /swagger-ui/index.html` retorna HTTP 200 HTML.
- [ ] **Step 2: Rodar teste e verificar falha (Red phase)**
  - Executar: `.\gradlew.bat :account-service:test --tests com.bankledger.account.config.ActuatorAndSwaggerTest`
  - Esperado: FAIL (dependências e endpoints ausentes).
- [ ] **Step 3: Adicionar dependências e configurar Actuator + OpenApiConfig**
  - Adicionar ao `build.gradle`:
    - `implementation 'org.springframework.boot:spring-boot-starter-actuator'`
    - `implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.5'`
  - Excluir `HealthController.java` e `HealthControllerTest.java`.
  - Criar `OpenApiConfig.java` configurando OpenAPI com título "Account Service API", versão "1.0.0", e `SecurityScheme` Bearer JWT.
  - Atualizar `SecurityConfig.java`:
    - Liberar `GET /actuator/**`, `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`.
  - Atualizar `application.yml`:
    ```yaml
    management:
      endpoints:
        web:
          exposure:
            include: health,info,metrics
      endpoint:
        health:
          show-details: always
    springdoc:
      swagger-ui:
        path: /swagger-ui.html
      api-docs:
        path: /v3/api-docs
    ```
  - Atualizar `infra/docker/docker-compose.yml`:
    `test: ["CMD-SHELL", "wget --no-verbose --tries=1 --spider http://127.0.0.1:8081/actuator/health || exit 1"]`
- [ ] **Step 4: Rodar teste e verificar sucesso (Green phase)**
  - Executar: `.\gradlew.bat :account-service:test --tests com.bankledger.account.config.ActuatorAndSwaggerTest`
  - Esperado: PASS (3/3 testes passam).
- [ ] **Step 5: Commit**
  - `git commit -m "feat(account-service): integrate spring boot actuator health and springdoc openapi swagger ui"`

---

### Task 2: Reestruturação do Pacote de Persistência (`entity/` e `repository/`) & Governança ArchUnit

**Files:**
- Create: `services/account-service/src/main/java/com/bankledger/account/adapters/out/persistence/entity/CustomerJpaEntity.java`
- Create: `services/account-service/src/main/java/com/bankledger/account/adapters/out/persistence/entity/AccountJpaEntity.java`
- Create: `services/account-service/src/main/java/com/bankledger/account/adapters/out/persistence/repository/CustomerSpringDataRepository.java`
- Create: `services/account-service/src/main/java/com/bankledger/account/adapters/out/persistence/repository/AccountSpringDataRepository.java`
- Delete: arquivos antigos em `adapters/out/persistence/`
- Modify: `CustomerPersistenceAdapter.java`
- Modify: `AccountPersistenceAdapter.java`
- Modify: `OutboxPersistenceAdapter.java`
- Modify: `services/account-service/src/test/java/com/bankledger/account/arch/HexagonalArchitectureTest.java`
- Test: `services/account-service/src/test/java/com/bankledger/account/adapters/out/persistence/PersistenceIntegrationTest.java`

**Interfaces:**
- Consumes: Domínio (`Customer`, `Account`), Spring Data JPA
- Produces: Entidades isoladas em `persistence.entity`, repositórios isolados em `persistence.repository`, adaptadores injetados sem `@Autowired`.

- [ ] **Step 1: Atualizar o teste ArchUnit para validar a nova separação**
  - Em `HexagonalArchitectureTest.java`, adicionar regras explícitas:
    1. Classes com `@Entity` só podem residir no pacote `..adapters.out.persistence.entity..`.
    2. Interfaces que estendem `Repository` só podem residir no pacote `..adapters.out.persistence.repository..`.
- [ ] **Step 2: Rodar o teste para verificar falha (Red phase)**
  - Executar: `.\gradlew.bat :account-service:test --tests com.bankledger.account.arch.HexagonalArchitectureTest`
  - Esperado: FAIL (entidades ainda no pacote pai).
- [ ] **Step 3: Mover classes para os novos subpacotes e remover `@Autowired`**
  - Mover `CustomerJpaEntity` e `AccountJpaEntity` para `adapters.out.persistence.entity`.
  - Mover `CustomerSpringDataRepository` e `AccountSpringDataRepository` para `adapters.out.persistence.repository`.
  - Atualizar `CustomerPersistenceAdapter`, `AccountPersistenceAdapter` e `OutboxPersistenceAdapter` com injeção via construtor com Lombok `@RequiredArgsConstructor` (ou construtor explícito com `final`), garantindo zero `@Autowired`.
  - Atualizar imports em `PersistenceIntegrationTest` e mapeadores.
- [ ] **Step 4: Rodar testes e verificar sucesso (Green phase)**
  - Executar: `.\gradlew.bat :account-service:test --tests com.bankledger.account.arch.HexagonalArchitectureTest`
  - Executar: `.\gradlew.bat :account-service:test --tests com.bankledger.account.adapters.out.persistence.PersistenceIntegrationTest`
  - Esperado: PASS (100% dos testes de persistência e governança passam).
- [ ] **Step 5: Commit**
  - `git commit -m "refactor(account-service): organize persistence entities and repositories into dedicated subpackages"`

---

### Task 3: Kafka Manual ACK & Publicação Assíncrona Não-Bloqueante com Timeout Configurável

**Files:**
- Create: `services/account-service/src/main/java/com/bankledger/account/config/KafkaConfig.java`
- Modify: `services/account-service/src/main/java/com/bankledger/account/adapters/out/kafka/KafkaOutboxPublisher.java`
- Modify: `services/account-service/src/main/resources/application.yml`
- Test: `services/account-service/src/test/java/com/bankledger/account/adapters/out/kafka/KafkaOutboxPublisherTest.java`
- Test: `services/account-service/src/test/java/com/bankledger/account/integration/OutboxKafkaIntegrationTest.java`

**Interfaces:**
- Consumes: Spring Kafka `KafkaTemplate`, `OutboxPublisher`
- Produces: Publicação assíncrona não-bloqueante no outbox poller com timeout configurável `${OUTBOX_POLLER_PUBLISH_TIMEOUT_MS:2000}` e configuração de `AckMode.MANUAL_IMMEDIATE`.

- [ ] **Step 1: Escrever teste unitário para `KafkaOutboxPublisher` com timeout configurável**
  - Em `KafkaOutboxPublisherTest.java`:
    1. Valida envio bem-sucedido completando o `CompletableFuture` antes do timeout.
    2. Valida timeout lançando `TimeoutException` após o limite configurável (ex: 500ms no teste) sem travar a thread indefinidamente.
    3. Valida que erros de broker propagam falha para o poller incrementar tentativa e salvar `last_error`.
- [ ] **Step 2: Rodar teste e verificar falha (Red phase)**
  - Executar: `.\gradlew.bat :account-service:test --tests com.bankledger.account.adapters.out.kafka.KafkaOutboxPublisherTest`
- [ ] **Step 3: Implementar `KafkaConfig` e refatorar `KafkaOutboxPublisher`**
  - Em `KafkaConfig.java`:
    - Configurar `ConcurrentKafkaListenerContainerFactory` com `AckMode.MANUAL_IMMEDIATE` para total controle do commit manual nos consumidores.
  - Em `KafkaOutboxPublisher.java`:
    - Injetar `publishTimeout` em milissegundos configurável via `${bankledger.outbox.poller.publish-timeout-ms:2000}`.
    - Utilizar chamada assíncrona com `CompletableFuture` limitado pelo timeout configurado:
      `kafkaTemplate.send(topic, key, payload).get(publishTimeoutMs, TimeUnit.MILLISECONDS)`.
    - Documentar via Javadoc que a API REST (`/api/v1/accounts`) é 100% desacoplada do Kafka, gravando apenas na tabela `outbox_event` no PostgreSQL em < 5ms.
    - Utilizar injeção via construtor sem `@Autowired`.
- [ ] **Step 4: Rodar testes e verificar sucesso (Green phase)**
  - Executar: `.\gradlew.bat :account-service:test --tests com.bankledger.account.adapters.out.kafka.KafkaOutboxPublisherTest`
  - Executar: `.\gradlew.bat :account-service:test --tests com.bankledger.account.integration.OutboxKafkaIntegrationTest`
  - Esperado: PASS.
- [ ] **Step 5: Commit**
  - `git commit -m "feat(account-service): configure manual kafka ack mode and non-blocking outbox publishing with configurable timeout"`

---

### Task 4: Variáveis de Ambiente e Externalização de Propriedades do Outbox Poller

**Files:**
- Create: `services/account-service/src/main/java/com/bankledger/account/config/OutboxPollerProperties.java`
- Modify: `services/account-service/src/main/java/com/bankledger/account/adapters/out/kafka/OutboxPollerLifecycle.java`
- Modify: `services/account-service/src/main/resources/application.yml`
- Test: `services/account-service/src/test/java/com/bankledger/account/config/OutboxPollerPropertiesTest.java`

**Interfaces:**
- Consumes: Spring Boot `@ConfigurationProperties`
- Produces: Classe tipada `OutboxPollerProperties` mapeando variáveis de ambiente para o poller.

- [ ] **Step 1: Escrever teste para vinculação das propriedades do poller**
  - `OutboxPollerPropertiesTest`: Testa injeção dos defaults e override via environment/properties (`enabled`, `autoStartup`, `batchSize`, `intervalMs`, `maxAttempts`, `publishTimeoutMs`).
- [ ] **Step 2: Rodar teste e verificar falha (Red phase)**
  - Executar: `.\gradlew.bat :account-service:test --tests com.bankledger.account.config.OutboxPollerPropertiesTest`
- [ ] **Step 3: Implementar `OutboxPollerProperties` e refatorar `OutboxPollerLifecycle`**
  - Criar `OutboxPollerProperties` anotada com `@ConfigurationProperties(prefix = "bankledger.outbox.poller")`.
  - Configurar em `application.yml`:
    ```yaml
    bankledger:
      outbox:
        poller:
          enabled: ${OUTBOX_POLLER_ENABLED:true}
          auto-startup: ${OUTBOX_POLLER_AUTO_STARTUP:true}
          batch-size: ${OUTBOX_POLLER_BATCH_SIZE:50}
          interval-ms: ${OUTBOX_POLLER_INTERVAL_MS:200}
          max-attempts: ${OUTBOX_POLLER_MAX_ATTEMPTS:5}
          publish-timeout-ms: ${OUTBOX_POLLER_PUBLISH_TIMEOUT_MS:2000}
    ```
  - Em `OutboxPollerLifecycle`:
    - Injetar `OutboxPollerProperties` no construtor (sem `@Autowired`).
    - Passar as propriedades para o builder do `OutboxPoller`.
- [ ] **Step 4: Rodar testes e verificar sucesso (Green phase)**
  - Executar: `.\gradlew.bat :account-service:test --tests com.bankledger.account.config.OutboxPollerPropertiesTest`
  - Executar: `.\gradlew.bat :account-service:test --tests com.bankledger.account.integration.OutboxKafkaIntegrationTest`
  - Esperado: PASS.
- [ ] **Step 5: Commit**
  - `git commit -m "feat(account-service): externalize outbox poller properties with environment variable bindings"`

---

### Task 5: Padronização em Inglês, Poda de Comentários & Eliminação Total de `@Autowired`

**Files:**
- Modify: Todos os arquivos em `services/account-service/src/main/java/**` e `platform-commons/src/main/java/**`
- Modify: `SecurityConfig.java`, `AccountController.java`, `AuthController.java`, `CreateAccountService.java`, `LoginService.java`, `AccountQueryService.java`, `Account.java`, `Customer.java`
- Test: Suíte completa do monorepo (`.\gradlew.bat clean test`)

**Interfaces:**
- Consumes: Todo o codebase
- Produces: Base 100% padronizada em Inglês (código, logs, docstrings), sem anotações `@Autowired`, com comentários concisos e objetivos, mantendo apenas mensagens de erro de negócio em Português do Brasil.

- [ ] **Step 1: Identificar e remover qualquer `@Autowired` remanescente**
  - Varredura em `services/account-service` e `platform-commons`:
    - Garantir que todas as classes utilizam injeção via construtor (ou `@RequiredArgsConstructor` do Lombok).
    - Remover `import org.springframework.beans.factory.annotation.Autowired;`.
- [ ] **Step 2: Padronizar código, docstrings e logs para Inglês**
  - Traduzir nomes de variáveis, comentários internos, javadocs e mensagens de log para Inglês.
  - **Manter em Português do Brasil exclusivamente:**
    - Strings de mensagens de erro nos DTOs/Exceptions voltadas ao usuário (ex: `"Credenciais inválidas"`, `"CPF já cadastrado"`, `"Conta bloqueada temporariamente"`).
- [ ] **Step 3: Podar e simplificar blocos de comentários verbosos**
  - Simplificar cabeçalhos excessivos em `Account.java`, `CreateAccountService.java`, `LoginService.java`, `OutboxPollerLifecycle.java` e `HexagonalArchitectureTest.java`.
  - Manter apenas javadocs essenciais e claros com 1 a 3 linhas.
- [ ] **Step 4: Executar a suíte completa de testes do monorepo**
  - Executar: `.\gradlew.bat clean test`
  - Esperado: BUILD SUCCESSFUL com 100% dos testes passando sem nenhum warning.
- [ ] **Step 5: Commit**
  - `git commit -m "refactor(account-service): standardize codebase to english, prune verbose comments, and eliminate autowired annotations"`

---

## Auto-Avaliação do Plano (Self-Review)

1. **Cobertura das Considerações:** Todas as 9 solicitações do usuário estão detalhadas nas tarefas:
   - Item 1 (Kafka ACK manual & commit no código): Task 3.
   - Item 2 (Eliminar `@Autowired`): Tasks 1, 2, 3, 4, 5.
   - Item 3 (Variáveis de ambiente no outbox poller): Task 4.
   - Item 4 (Swagger OpenAPI): Task 1.
   - Item 5 (Timeout do Kafka desacoplado da API): Task 3.
   - Item 6 (Entities em pacote separado `entity/`): Task 2.
   - Item 7 (Actuator ao invés de endpoint customizado de Health): Task 1.
   - Item 8 (Padronizar em Inglês, mantendo erros em PT-BR): Task 5.
   - Item 9 (Simplificar comentários verbosos): Task 5.
2. **Granularidade:** Cada tarefa é autocontida com passos Red $\to$ Green $\to$ Commit.
3. **Consistência de Tipos:** Nomes de propriedades, portas e pacotes mantêm perfeita correspondência com o padrão monorepo.
4. **Proporção:** O plano documenta exatamente as decisões de interface e contratos sem redundância desnecessária.

