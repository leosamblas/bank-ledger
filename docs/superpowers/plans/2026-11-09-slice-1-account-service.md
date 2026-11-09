# Slice 1 (account-service) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Construir o microsserviço `account-service` em Arquitetura Hexagonal com Java 25 e Spring Boot 4.1.2, responsável por cadastro de clientes, abertura de contas correntes, autenticação JWT com proteção contra força bruta, publicação de eventos no Transactional Outbox (Kafka) e integração completa tela a tela com o frontend Next.js 16 (/signup, /login e /dashboard).

**Architecture:** Microsserviço independente estruturado rigorosamente em Arquitetura Hexagonal (Ports & Adapters) governado por testes automatizados do ArchUnit. O domínio é 100% puro (sem dependência de Spring ou JPA). A persistência utiliza PostgreSQL 18 (`account_db`) com migrações Flyway e sequence para numeração de contas. A mensageria integra o Transactional Outbox construído no Slice 0 em Virtual Threads para publicar `AccountOpened` no tópico `account.events.v1` do Kafka. A segurança emprega tokens JWT (HMAC-SHA256) e senhas com BCrypt.

**Tech Stack:** Java 25 (Virtual Threads), Spring Boot 4.1.2 (Spring Data JPA, Spring Security 7, Spring Validation, Spring Kafka, Spring Actuator), Gradle 9.9.0 (Groovy DSL, Composite Builds), PostgreSQL 18, Flyway, Jackson 3, Lombok, ArchUnit, Testcontainers, Next.js 16.4.3.

**Spec:** `api-contracts/account-service.yaml` e `docs/plano_desenvolvimento_ledger.md` (Seções 3.3, 4.2, 5.1, 5.2, 6.1 e 7 Slice 1).

---

## Global Constraints

- **Stack Oficial:** Java 25 com Virtual Threads ativas (`spring.threads.virtual.enabled=true`) e Spring Boot 4.1.2.
- **Porta & Gateway:** O `account-service` executa internamente na porta `8081` e é roteado pelo Nginx Gateway na porta `8080` sob os prefixos `/api/v1/auth/*` e `/api/v1/accounts/*`.
- **Contrato de API Estrito:** Endpoints, request bodies, responses e erros devem seguir rigorosamente o OpenAPI `api-contracts/account-service.yaml`.
- **Tratamento de Erros:** Erros devem utilizar o padrão RFC 7807 `ProblemDetail` (`application/problem+json`).
- **Domínio Puro (Hexagonal):** O pacote `com.bankledger.account.domain` não pode importar `org.springframework.*`, `jakarta.persistence.*` nem bibliotecas de infraestrutura. Governança garantida via ArchUnit.
- **Persistência Isolada:** Banco lógico dedicado `account_db` no PostgreSQL 18, migrado via Flyway (`V1__init_account_schema.sql`).
- **Mensageria Assíncrona:** Publicação de eventos bancários exclusivamente através do padrão Transactional Outbox (`outbox_event`) com `SKIP LOCKED` sobre Virtual Threads em `platform-commons`.
- **Segurança Bancária:** Senhas criptografadas com BCrypt; bloqueio por 15 minutos após 5 tentativas consecutivas de senha incorreta (`locked_until`).

---

## Review Focus

1. **Tentativa de cadastro com CPF ou E-mail duplicado:** Deve retornar HTTP `409 Conflict` com RFC 7807 `ProblemDetail` informando duplicidade sem vazar detalhes internos ou stack traces.
2. **Tentativa de cadastro de menor de idade (< 18 anos) ou CPF com dígitos inválidos:** Deve retornar HTTP `422 Unprocessable Entity` com detalhe claro da violação da regra de negócio.
3. **Bloqueio de segurança por 5 tentativas de senha incorreta:** Na 5ª falha consecutiva, o sistema deve registrar `locked_until = now() + 15 minutos` e responder HTTP `401 Unauthorized` indicando bloqueio temporário, rejeitando inclusive a senha correta enquanto durar o bloqueio.
4. **Geração determinística de número de conta e dígito Módulo 11:** O número da conta deve consumir a sequence `account_number_seq` (iniciando em 100000) e calcular o dígito pelo algoritmo Módulo 11 (pesos 2 a 9).
5. **Atomicidade da Abertura de Conta e Registro no Outbox:** A criação do `Customer`, da `Account` e a inserção do evento `AccountOpened` na tabela `outbox_event` devem ocorrer estritamente na mesma transação ACID do PostgreSQL. Se a gravação no outbox falhar, a conta não é criada.

---

## Estrutura de Arquivos Planejada

```text
services/account-service/
├── build.gradle
├── settings.gradle
├── Dockerfile
└── src/
    ├── main/
    │   ├── java/com/bankledger/account/
    │   │   ├── domain/
    │   │   │   ├── model/ (Customer, Account, AccountStatus)
    │   │   │   ├── vo/ (Cpf, AccountNumber, Email, Phone, Password)
    │   │   │   ├── event/ (AccountOpenedEvent)
    │   │   │   └── exception/ (InvalidCpfException, CustomerUnderageException, ...)
    │   │   ├── application/
    │   │   │   ├── port/in/ (CreateAccountUseCase, LoginUseCase, GetAccountMeUseCase, LookupAccountUseCase)
    │   │   │   ├── port/out/ (CustomerRepositoryPort, AccountRepositoryPort, PasswordEncoderPort, TokenServicePort, OutboxPort)
    │   │   │   └── service/ (CreateAccountService, LoginService, AccountQueryService)
    │   │   ├── adapters/
    │   │   │   ├── in/rest/ (AccountController, AuthController, GlobalExceptionHandler, DTOs)
    │   │   │   ├── out/persistence/ (CustomerJpaEntity, AccountJpaEntity, CustomerRepositoryAdapter, AccountRepositoryAdapter)
    │   │   │   └── out/kafka/ (KafkaOutboxPublisher, OutboxPollerLifecycle)
    │   │   ├── config/ (SecurityConfig, JwtAuthenticationFilter, VirtualThreadConfig)
    │   │   └── AccountServiceApplication.java
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/
    │           └── V1__init_account_schema.sql
    └── test/
        ├── java/com/bankledger/account/
        │   ├── arch/ (HexagonalArchitectureTest)
        │   ├── domain/ (CpfTest, AccountNumberTest, CustomerTest)
        │   ├── application/ (CreateAccountServiceTest, LoginServiceTest)
        │   └── integration/ (AccountApiIntegrationTest, AuthApiIntegrationTest, OutboxKafkaIntegrationTest)
        └── resources/
            └── application-test.yml
```

---

## Roteiro de Execução das Tarefas

### Task 1: Scaffolding do Módulo `account-service` & Governança ArchUnit

**Files:**
- Create: `services/account-service/build.gradle`
- Create: `services/account-service/settings.gradle`
- Create: `services/account-service/src/main/java/com/bankledger/account/AccountServiceApplication.java`
- Create: `services/account-service/src/main/resources/application.yml`
- Create: `services/account-service/src/test/java/com/bankledger/account/arch/HexagonalArchitectureTest.java`
- Modify: `settings.gradle` (raiz)
- Modify: `build.gradle` (raiz)

**Interfaces:**
- Consumes: `platform-commons` (via Composite Build)
- Produces: Módulo Gradle `account-service` compilável e regras de governança ArchUnit ativas.

- [ ] **Step 1: Escrever o teste de governança ArchUnit**
  - Implementar em `HexagonalArchitectureTest.java`:
    1. `domain` não pode depender de `application`, `adapters`, `config` nem de frameworks (`org.springframework..`, `jakarta.persistence..`).
    2. `application` não pode depender de `adapters` nem `config`.
    3. `adapters` só acessa `application.port.in` e implementa `application.port.out`.
- [ ] **Step 2: Rodar o teste para verificar falha (Red phase)**
  - Executar: `.\gradlew.bat :services:account-service:test`
  - Esperado: FALHA (classes e pacotes ainda não estruturados).
- [ ] **Step 3: Configurar o build e criar a estrutura inicial**
  - Criar `services/account-service/build.gradle` configurado para Java 25, Spring Boot 4.1.2 BOM, dependencies: Web, Data JPA, Security, Validation, Flyway, PostgreSQL, Lombok, ArchUnit (`com.tngtech.archunit:archunit-junit5:1.3.0`), Testcontainers.
  - Atualizar `settings.gradle` da raiz com `includeBuild('services/account-service')`.
  - Criar classe principal `AccountServiceApplication.java` e `application.yml` básico.
  - Criar pacotes base `domain`, `application`, `adapters`, `config`.
- [ ] **Step 4: Rodar o teste para verificar sucesso (Green phase)**
  - Executar: `.\gradlew.bat :services:account-service:test`
  - Esperado: BUILD SUCCESSFUL (testes do ArchUnit passam 100%).
- [ ] **Step 5: Commit**
  - `git commit -m "chore(account-service): scaffold service structure and configure archunit governance"`

---

### Task 2: Domínio Rico & Value Objects (`Customer`, `Account`, `Cpf`, `AccountNumber`)

**Files:**
- Create: `services/account-service/src/main/java/com/bankledger/account/domain/vo/Cpf.java`
- Create: `services/account-service/src/main/java/com/bankledger/account/domain/vo/AccountNumber.java`
- Create: `services/account-service/src/main/java/com/bankledger/account/domain/vo/Email.java`
- Create: `services/account-service/src/main/java/com/bankledger/account/domain/vo/Phone.java`
- Create: `services/account-service/src/main/java/com/bankledger/account/domain/model/Customer.java`
- Create: `services/account-service/src/main/java/com/bankledger/account/domain/model/Account.java`
- Create: `services/account-service/src/main/java/com/bankledger/account/domain/model/AccountStatus.java`
- Create: `services/account-service/src/main/java/com/bankledger/account/domain/event/AccountOpenedEvent.java`
- Create: `services/account-service/src/main/java/com/bankledger/account/domain/exception/*`
- Test: `services/account-service/src/test/java/com/bankledger/account/domain/CpfTest.java`
- Test: `services/account-service/src/test/java/com/bankledger/account/domain/AccountNumberTest.java`
- Test: `services/account-service/src/test/java/com/bankledger/account/domain/CustomerTest.java`
- Test: `services/account-service/src/test/java/com/bankledger/account/domain/AccountTest.java`

**Interfaces:**
- Consumes: Pure Java SE 25
- Produces: Entidades de domínio e Value Objects com invariantes estritas.

- [ ] **Step 1: Escrever testes unitários para os VOs e Entidades**
  - `CpfTest`: Validação de 11 dígitos, cálculo dos 2 dígitos verificadores, rejeição de repetidos (`111.111.111-11`).
  - `AccountNumberTest`: Cálculo Módulo 11 (pesos 2 a 9), formatação com 6 dígitos (`100000-7`).
  - `CustomerTest`: Validação de maioridade (>= 18 anos), contagem de falhas de login e bloqueio corporativo de 15 minutos após 5 erros.
  - `AccountTest`: Criação com agência `0001`, status inicial `ACTIVE`, fechamento de conta.
- [ ] **Step 2: Rodar testes e verificar falhas (Red phase)**
  - Executar: `.\gradlew.bat :services:account-service:test`
- [ ] **Step 3: Implementar VOs, Entidades, Enums e Exceções de Domínio**
  - Implementar classes puras, records e métodos defensivos.
  - Zero imports de Spring ou JPA no pacote `domain`.
- [ ] **Step 4: Rodar testes e verificar aprovação (Green phase)**
  - Executar: `.\gradlew.bat :services:account-service:test`
- [ ] **Step 5: Commit**
  - `git commit -m "feat(account-service): implement pure domain models, value objects, and invariant tests"`

---

### Task 3: Banco de Dados, Migração Flyway V1 & Adaptadores de Persistência

**Files:**
- Create: `services/account-service/src/main/resources/db/migration/V1__init_account_schema.sql`
- Create: `services/account-service/src/main/java/com/bankledger/account/adapters/out/persistence/CustomerJpaEntity.java`
- Create: `services/account-service/src/main/java/com/bankledger/account/adapters/out/persistence/AccountJpaEntity.java`
- Create: `services/account-service/src/main/java/com/bankledger/account/adapters/out/persistence/CustomerSpringDataRepository.java`
- Create: `services/account-service/src/main/java/com/bankledger/account/adapters/out/persistence/AccountSpringDataRepository.java`
- Create: `services/account-service/src/main/java/com/bankledger/account/adapters/out/persistence/CustomerPersistenceAdapter.java`
- Create: `services/account-service/src/main/java/com/bankledger/account/adapters/out/persistence/AccountPersistenceAdapter.java`
- Create: `services/account-service/src/main/java/com/bankledger/account/application/port/out/CustomerRepositoryPort.java`
- Create: `services/account-service/src/main/java/com/bankledger/account/application/port/out/AccountRepositoryPort.java`
- Test: `services/account-service/src/test/java/com/bankledger/account/adapters/out/persistence/PersistenceIntegrationTest.java`

**Interfaces:**
- Consumes: Domínio (`Customer`, `Account`), `platform-commons` (`outbox_event` schema)
- Produces: Portas de saída de persistência implementadas sobre Spring Data JPA e PostgreSQL 18.

- [ ] **Step 1: Criar o script Flyway `V1__init_account_schema.sql`**
  - `CREATE SEQUENCE account_number_seq START 100000;`
  - Tabelas `customer`, `account` (com foreign key, unique constraints de CPF/Email, checks e índices de status) e tabelas canônicas `outbox_event` e `inbox_event`.
- [ ] **Step 2: Escrever testes de integração com Testcontainers**
  - `PersistenceIntegrationTest`: Testa salvamento de `Customer`, salvamento de `Account`, busca por CPF, busca por agência e número, avanço da sequence `account_number_seq`, e constraint de duplicidade de CPF no banco.
- [ ] **Step 3: Implementar entidades JPA, Repositories e Adaptadores de Persistência**
  - Mapear JPA Entities com Lombok (`@Getter`, `@Setter`, etc.) e conversores de/para entidades de domínio puras.
  - Implementar `CustomerRepositoryPort` e `AccountRepositoryPort`.
- [ ] **Step 4: Executar testes de integração com Testcontainers**
  - Executar: `.\gradlew.bat :services:account-service:test`
- [ ] **Step 5: Commit**
  - `git commit -m "feat(account-service): create flyway v1 migration, jpa entities, and persistence adapters with testcontainers"`

---

### Task 4: Casos de Uso da Aplicação (CreateAccount, Login, GetAccountMe, LookupAccount)

**Files:**
- Create: `services/account-service/src/main/java/com/bankledger/account/application/port/in/CreateAccountUseCase.java`
- Create: `services/account-service/src/main/java/com/bankledger/account/application/port/in/LoginUseCase.java`
- Create: `services/account-service/src/main/java/com/bankledger/account/application/port/in/GetAccountMeUseCase.java`
- Create: `services/account-service/src/main/java/com/bankledger/account/application/port/in/LookupAccountUseCase.java`
- Create: `services/account-service/src/main/java/com/bankledger/account/application/port/out/PasswordEncoderPort.java`
- Create: `services/account-service/src/main/java/com/bankledger/account/application/port/out/TokenServicePort.java`
- Create: `services/account-service/src/main/java/com/bankledger/account/application/service/CreateAccountService.java`
- Create: `services/account-service/src/main/java/com/bankledger/account/application/service/LoginService.java`
- Create: `services/account-service/src/main/java/com/bankledger/account/application/service/AccountQueryService.java`
- Test: `services/account-service/src/test/java/com/bankledger/account/application/CreateAccountServiceTest.java`
- Test: `services/account-service/src/test/java/com/bankledger/account/application/LoginServiceTest.java`
- Test: `services/account-service/src/test/java/com/bankledger/account/application/AccountQueryServiceTest.java`

**Interfaces:**
- Consumes: `CustomerRepositoryPort`, `AccountRepositoryPort`, `OutboxEventRepository` (`platform-commons`), `PasswordEncoderPort`, `TokenServicePort`
- Produces: Portas de entrada (casos de uso) para criação de conta com outbox atômico, login com controle de tentativas, e consultas.

- [ ] **Step 1: Escrever testes unitários para os casos de uso**
  - `CreateAccountServiceTest`: Criação bem-sucedida gera número via sequence + DV Módulo 11, grava conta como `ACTIVE`, grava `AccountOpened` no outbox; rejeita duplicidade de CPF ou e-mail lançando exceções de domínio correspondentes.
  - `LoginServiceTest`: Senha correta zera tentativas e retorna token; senha incorreta incrementa contador; 5ª falha bloqueia por 15 minutos; login rejeitado durante o bloqueio.
  - `AccountQueryServiceTest`: Busca por agência e número retorna nome mascarado `C*** E*** S***`; busca de conta própria retorna dados completos.
- [ ] **Step 2: Rodar testes e verificar falhas (Red phase)**
  - Executar: `.\gradlew.bat :services:account-service:test`
- [ ] **Step 3: Implementar os serviços de aplicação com anotações `@Transactional`**
  - Gravação atômica de `Customer`, `Account` e `OutboxEvent` na abertura de conta.
  - Hashing de senha com `PasswordEncoderPort`.
  - Mascaramento determinístico no lookup.
- [ ] **Step 4: Rodar testes e verificar sucesso (Green phase)**
  - Executar: `.\gradlew.bat :services:account-service:test`
- [ ] **Step 5: Commit**
  - `git commit -m "feat(account-service): implement use cases for account creation, login, and lookup with transactional outbox"`

---

### Task 5: Adaptador REST, Segurança Spring Security JWT & GlobalExceptionHandler (ProblemDetail)

**Files:**
- Create: `services/account-service/src/main/java/com/bankledger/account/adapters/in/rest/AccountController.java`
- Create: `services/account-service/src/main/java/com/bankledger/account/adapters/in/rest/AuthController.java`
- Create: `services/account-service/src/main/java/com/bankledger/account/adapters/in/rest/GlobalExceptionHandler.java`
- Create: `services/account-service/src/main/java/com/bankledger/account/adapters/in/rest/dto/*` (Request/Response DTOs do OpenAPI)
- Create: `services/account-service/src/main/java/com/bankledger/account/config/SecurityConfig.java`
- Create: `services/account-service/src/main/java/com/bankledger/account/config/JwtAuthenticationFilter.java`
- Create: `services/account-service/src/main/java/com/bankledger/account/adapters/out/security/JwtTokenAdapter.java`
- Create: `services/account-service/src/main/java/com/bankledger/account/adapters/out/security/BCryptPasswordEncoderAdapter.java`
- Test: `services/account-service/src/test/java/com/bankledger/account/adapters/in/rest/AccountControllerTest.java`
- Test: `services/account-service/src/test/java/com/bankledger/account/adapters/in/rest/AuthControllerTest.java`

**Interfaces:**
- Consumes: Contrato `api-contracts/account-service.yaml`, Casos de Uso (`CreateAccountUseCase`, `LoginUseCase`, etc.)
- Produces: Endpoints REST na porta 8081 integrados ao Spring Security e serialização RFC 7807 `ProblemDetail`.

- [ ] **Step 1: Escrever testes de API (MockMvc / WebMvcTest)**
  - `POST /api/v1/accounts`: Valida 201 Created com header `Location`, valida 409 Conflict (CPF duplicado), valida 422 Unprocessable Entity (menor de idade / CPF inválido), valida 400 Bad Request (campos vazios).
  - `POST /api/v1/auth/login`: Valida 200 OK com `{ token, expiresIn }`, valida 401 Unauthorized com ProblemDetail.
  - `GET /api/v1/accounts/me`: Valida 200 OK com token Bearer válido, valida 401 se ausente.
  - `GET /api/v1/accounts/lookup`: Valida 200 OK com `holderNameMasked`, valida 404 se inexistente.
- [ ] **Step 2: Rodar testes e verificar falhas (Red phase)**
  - Executar: `.\gradlew.bat :services:account-service:test`
- [ ] **Step 3: Implementar Controllers, SecurityConfig, JWT Filter e GlobalExceptionHandler**
  - Implementar controllers REST aderentes ao OpenAPI `account-service.yaml`.
  - Configurar Spring Security liberando endpoints públicos (`/accounts` [POST], `/auth/login` [POST], `/health` [GET]) e exigindo Bearer Token nos demais.
  - Implementar `GlobalExceptionHandler` estendendo `ResponseEntityExceptionHandler` com suporte nativo a `ProblemDetail`.
- [ ] **Step 4: Rodar testes e verificar aprovação (Green phase)**
  - Executar: `.\gradlew.bat :services:account-service:test`
- [ ] **Step 5: Commit**
  - `git commit -m "feat(account-service): implement rest controllers, jwt authentication, and rfc 7807 problem detail handler"`

---

### Task 6: Integração do OutboxPoller com Kafka KRaft (`account.events.v1`)

**Files:**
- Create: `services/account-service/src/main/java/com/bankledger/account/adapters/out/kafka/KafkaOutboxPublisher.java`
- Create: `services/account-service/src/main/java/com/bankledger/account/adapters/out/kafka/OutboxPollerLifecycle.java`
- Test: `services/account-service/src/test/java/com/bankledger/account/integration/OutboxKafkaIntegrationTest.java`

**Interfaces:**
- Consumes: `OutboxPoller` (`platform-commons`), Spring `KafkaTemplate`
- Produces: Publicação assíncrona garantida de eventos bancários em tópicos do Kafka.

- [ ] **Step 1: Escrever teste de integração com Testcontainers (Postgres + Kafka)**
  - `OutboxKafkaIntegrationTest`: Cria conta pelo caso de uso -> verifica que o evento `AccountOpened` é inserido no outbox com `published_at IS NULL` -> aciona ciclo do `OutboxPoller` -> verifica que a mensagem chega no tópico `account.events.v1` e `published_at` é preenchido.
- [ ] **Step 2: Rodar teste e verificar falha (Red phase)**
  - Executar: `.\gradlew.bat :services:account-service:test`
- [ ] **Step 3: Implementar `KafkaOutboxPublisher` e `OutboxPollerLifecycle`**
  - `KafkaOutboxPublisher`: Implementa `OutboxPublisher` publicando no Kafka com `event.getTopic()` e `event.getMessageKey()`.
  - `OutboxPollerLifecycle`: `@Component` que inicia o poller do outbox em Virtual Thread no startup da aplicação e executa shutdown gracioso.
- [ ] **Step 4: Rodar teste e verificar sucesso (Green phase)**
  - Executar: `.\gradlew.bat :services:account-service:test`
- [ ] **Step 5: Commit**
  - `git commit -m "feat(account-service): wire outbox poller with kafka kraft publisher for account events"`

---

### Task 7: Dockerfile, Gateway Integration & Validação E2E com Frontend Next.js

**Files:**
- Create: `services/account-service/Dockerfile`
- Modify: `infra/docker/docker-compose.yml` (adiciona serviço `account-service` na porta 8081)
- Modify: `infra/docker/nginx/nginx.conf` (normaliza rota `/api/v1/auth` e `/api/v1/accounts`)
- Modify: `docs/plano_desenvolvimento_ledger.md` (marca Slice 1 como concluído)

**Interfaces:**
- Consumes: Todo o backend do `account-service`, Docker Compose, Frontend Next.js 16 (`web`)
- Produces: Fluxo ponta a ponta funcional de Abertura de Conta, Login e Dashboard com mock desativado.

- [ ] **Step 1: Criar `Dockerfile` multi-stage para `account-service`**
  - Build otimizado com Gradle e execução com OpenJDK 25 Alpine.
- [ ] **Step 2: Atualizar `docker-compose.yml` e `nginx.conf`**
  - Incluir `account-service` conectado a `bank-ledger-network`, dependendo de `postgres` e `kafka` saudáveis.
  - Validar resolução dinâmica do Nginx Gateway.
- [ ] **Step 3: Executar a suíte completa de testes do monorepo**
  - Executar: `.\gradlew.bat clean build`
  - Esperado: Todos os testes de `platform-commons` e `account-service` passam com 100% de sucesso.
- [ ] **Step 4: Validação E2E com o Frontend Next.js**
  - Subir o ambiente com `docker compose up -d`.
  - Configurar `NEXT_PUBLIC_USE_MOCK=false` em `web/.env.local`.
  - Testar no navegador:
    1. Acessar `http://localhost:3000/signup`: preencher formulário e abrir conta real no banco de dados.
    2. Acessar `http://localhost:3000/login`: fazer login com CPF e senha cadastrados, receber JWT real.
    3. Redirecionar para `http://localhost:3000/dashboard`: verificar que o nome do titular e número de agência/conta são renderizados no cabeçalho a partir da chamada `GET /api/v1/accounts/me`.
- [ ] **Step 5: Atualizar `docs/plano_desenvolvimento_ledger.md` e Commit**
  - Marcar o Slice 1 com checkbox `[x]` como concluído.
  - Commit: `git commit -m "docs: mark slice 1 identity and account management as completed"`

---

## Auto-Avaliação do Plano (Self-Review)

1. **Cobertura do Spec:** Todos os requisitos de `docs/plano_desenvolvimento_ledger.md` (Slice 1) e `api-contracts/account-service.yaml` estão contemplados (endpoints, DTOs, RFC 7807, bloqueio de 15 min, outbox).
2. **Granularidade:** Cada tarefa possui passos explícitos de teste falhando (Red), implementação (Green), verificação e commit individual.
3. **Consistência de Tipos:** Nomes de portas (`CustomerRepositoryPort`, `AccountRepositoryPort`), VOs (`Cpf`, `AccountNumber`) e DTOs mantêm padrão consistente entre todas as tarefas.
4. **Foco de Revisão:** Os 5 cenários mais críticos (duplicidade de CPF, menor de 18 anos, bloqueio de 15 minutos, sequence/Módulo 11, atomicidade do Outbox) foram explicitados com testes dedicados.
5. **Proporção:** O plano documenta exatamente as decisões de interface e contratos sem redundância desnecessária.
