# Bank Ledger

Bem-vindo ao repositório do **Bank Ledger**, uma plataforma de Core Bancário (Ledger) desenhada para garantir consistência financeira com alta escalabilidade. 

Este projeto baseia-se no princípio contábil das **Partidas Dobradas (Double-Entry Accounting)**, onde toda movimentação possui origens e destinos simétricos (débito e crédito), garantindo que não haja perda de valores e facilitando processos de auditoria (balancete e extrato confiáveis).

## 🏗️ Arquitetura Resumida

O sistema utiliza padrões modernos para garantir alta disponibilidade e escalabilidade de dados financeiros:
* **Backend em Java 25 & Spring Boot 4.1.2**: Microsserviços independentes com Gradle (sintaxe padrão Java) sobre Spring Boot 4.1.2 (starters modulares, Spring Framework 7, Jackson 3) com suporte a Virtual Threads nativas e Project Reactor configurado via JVM (`reactor.schedulers.defaultBoundedElasticOnVirtualThreads=true`) para executar `Schedulers.boundedElastic()` em threads virtuais.
* **API Gateway (Nginx na porta 8080)**: Ponto de entrada unificado para o tráfego do frontend, distribuindo chamadas para os microsserviços isolados.
* **Arquitetura Hexagonal (Ports & Adapters)**: Desacoplamento da regra de negócio (domínio) de frameworks, bancos de dados e mensageria.
* **Injeção de Dependências com Lombok (@RequiredArgsConstructor)**: Padrão arquitetural obrigatório para todos os microsserviços. É terminantemente proibido o uso de `@Autowired` em construtores ou sobre atributos (`field injection`). Dependências são declaradas como `private final` e propriedades externas são encapsuladas via classes dedicadas com `@ConfigurationProperties`.
* **CQRS (Command Query Responsibility Segregation)**: Separação lógica entre rotinas de gravação de transações e projeções desnormalizadas de extrato e saldo no PostgreSQL.
* **Consistência Eventual Segura**: Utilização do padrão *Transactional Outbox* para publicação assíncrona confiável de eventos em tópicos do Kafka (KRaft).
* **Tolerância e Resiliência**: Particionamento de tabelas por data (`pg_partman`), particionamento das contas de caixa (*shards* do Banco Central) para evitar concorrência/lock, e suporte a alta idempotência na ingestão de eventos.
* **Real-Time Updates**: Utilização de *Server-Sent Events (SSE)* no Frontend em substituição ao polling, evitando gargalos de I/O em fluxos de atualização contínua.

## 📚 Documentação Interna & Interfaces

Todo o planejamento de negócio, topologia do sistema, diagramas de sequência, contratos e fluxos assíncronos foram detalhados e mantidos internamente.

* 👉 **[Arquitetura Consolidada do Sistema (Spring Boot 4.1.2 & Java 25)](./docs/ARQUITETURA_CONSOLIDADA.md)**: Roteiro técnico completo, decisões arquiteturais consolidadas, topologia de microsserviços, governança e modelagem de domínios bancários.
* 👉 **[Sugestões de Melhorias e Roadmap Evolutivo](./docs/SUGESTOES_DE_MELHORIAS.md)**: Propostas de engenharia, otimizações de concorrência e roadmap de produto.
* 👉 **[Contratos de API (Swagger/OpenAPI)](./api-contracts/)**
* 👉 **[Frontend Web Banking (Next.js 16 / App Router)](./web/)**: Interface de Web Banking completa e funcional em modo autônomo (mock reativo integrado), incluindo telas de Visão Geral de Saldo, Extrato Oficial, Transferência Segura entre Contas, Depósito e Saque, com linguagem 100% amigável ao cliente bancário e zero jargões de infraestrutura.
* 👉 **[Suíte de Testes de Carga (Pure JS)](./load-tests/README.md)**: Ferramenta de teste de carga e estresse para o `account-service`, sem dependências externas pesadas (k6/JMeter), com geração de dados realistas brasileiros (CPF módulo 11, idade >= 18 anos) e métricas detalhadas de latência e throughput.

### 🚀 Como Rodar o Web Banking Localmente

**Opção A — Desenvolvimento Local (Node.js):**
```bash
cd web
npm install
npm run dev
# Acesse http://localhost:3000
```

**Opção B — Via Docker (Container Otimizado):**
```bash
# Subir apenas o frontend web
docker compose -f infra/docker/docker-compose.yml up --build -d web

# Ou subir toda a stack (Frontend + Backend + Infraestrutura)
docker compose -f infra/docker/docker-compose.yml up --build -d
# Acesse http://localhost:3000
```

---

## ⚙️ Como Rodar o Backend Localmente

### Pré-requisitos
* **Java 25** (ex: [Eclipse Temurin 25](https://adoptium.net/temurin/releases/?version=25))
* **Docker & Docker Compose** (para PostgreSQL 18, Kafka KRaft, Nginx e Kafka UI)

---

### Opção 1: Subir Todo o Ambiente via Docker Compose (Recomendado para Demonstração)

Sobe toda a infraestrutura e constrói a imagem do `account-service` em containers:

```bash
docker compose -f infra/docker/docker-compose.yml up --build -d
```

Para verificar o status dos containers e logs:
```bash
docker compose -f infra/docker/docker-compose.yml ps
docker compose -f infra/docker/docker-compose.yml logs -f account-service
```

Para parar o ambiente:
```bash
docker compose -f infra/docker/docker-compose.yml down
```

---

### Opção 2: Infraestrutura em Docker + Microsserviço Local (Modo Desenvolvimento)

Ideal para desenvolvimento ativo no terminal ou na IDE (VS Code / IntelliJ):

1. **Subir apenas a infraestrutura base (Postgres, Kafka KRaft, Nginx Gateway e Kafka UI):**
   ```bash
   docker compose -f infra/docker/docker-compose.yml up -d postgres kafka kafka-init nginx kafka-ui
   ```

2. **Executar as suítes de testes:**
   * **Testes Backend (Java / Testcontainers):**
     * No Linux / macOS:
       ```bash
       ./gradlew test
       ```
     * No Windows (PowerShell / CMD):
       ```powershell
       .\gradlew.bat test
       ```
   * **Testes da Suíte de Carga (Pure JS):**
     ```bash
     npm --prefix load-tests test
     # ou
     node --test load-tests/tests/*.test.js
     ```

3. **Iniciar o microsserviço `account-service`:**
   * A partir da raiz:
     ```bash
     ./gradlew :account-service:bootRun
     ```
   * Ou a partir do diretório `services`:
     ```bash
     cd services
     ./gradlew :account-service:bootRun
     ```

---

### 🌐 Portas e Endpoints Úteis

| Recurso | URL / Host | Descrição |
| :--- | :--- | :--- |
| **Web Banking (Next.js)** | [`http://localhost:3000`](http://localhost:3000) | Interface web do cliente bancário (Next.js 16 / App Router) |
| **API Gateway (Nginx)** | `http://localhost:8080` | Entrada central com CORS habilitado para o frontend |
| **Auth Service** | `http://localhost:8086` | Autenticação reativa, emissão de JWT/JWKS, rate limit e lock no Redis |
| **Account Service** | `http://localhost:8081` | Gestão de clientes, contas bancárias e abertura assíncrona |
| **Transaction Service** | `http://localhost:8082` | Orquestração de transações, depósitos, saques e transferências |
| **Query Service** | `http://localhost:8083` | Projeções de leitura CQRS, extrato e saldos desnormalizados |
| **Ledger Service** | `http://localhost:8084` | Core bancário interno (Partidas Dobradas, Shards de Caixa, Journal) |
| **Notification Service** | `http://localhost:8087` | Notificações reativas de boas-vindas via Kafka e SMTP |
| **Swagger UI (Account)** | [`http://localhost:8081/swagger-ui.html`](http://localhost:8081/swagger-ui.html) | Documentação interativa OpenAPI 3.0 com autenticação JWT |
| **Actuator Health (Account)** | [`http://localhost:8081/actuator/health`](http://localhost:8081/actuator/health) | Health check detalhado da aplicação |
| **Kafka UI** | [`http://localhost:8085`](http://localhost:8085) | Painel visual para inspeção de tópicos e mensagens Kafka |
| **Grafana UI (LGTM)** | [`http://localhost:3001`](http://localhost:3001) | Painel unificado de Observabilidade (Logs, Traces e Métricas - user/pass: `admin`/`admin`) |
| **OTLP Exporter (HTTP)** | `http://localhost:4318` | Endpoint de recepção de telemetria OpenTelemetry via HTTP |
| **OTLP Exporter (gRPC)** | `http://localhost:4317` | Endpoint de recepção de telemetria OpenTelemetry via gRPC |
| **Redis 7** | `localhost:6379` | Cache, locks de concorrência e rate-limiting reativo |
| **PostgreSQL 18** | `localhost:5432` | Bancos: `auth_db`, `account_db`, `ledger_db`, `transaction_db`, `query_db`<br>Usuário: `bank_ledger` / Senha: `bank_ledger_secret` |

---

## ⚡ Testes de Carga & Estresse (Pure JS Load Testing)

O repositório inclui uma ferramenta autônoma de teste de carga em JavaScript moderno (`load-tests/`) para avaliar a capacidade e latência do `account-service` sob concorrência, sem a necessidade de instalar ferramentas externas pesadas (zero k6, zero JMeter).

### Principais Características
* **Geração Realista de Clientes Brasileiros**: Integração com `@faker-js/faker` (`fakerPT_BR`), validação estrita de CPF por Módulo 11 (100% aderente a `Cpf.java`), data de nascimento com garantia de idade $\ge 18$ anos (evitando `CustomerUnderageException`) e telefones no padrão E.164 com DDDs válidos da ANATEL.
* **Métricas Completas de Performance**: Percentis de latência (mínimo, médio, p50, p90, p95, p99, máximo), taxa de transferência (RPS) e distribuição percentual de códigos HTTP.
* **Execução Assíncrona com Concorrência Estrita**: Pool concorrente de workers disparando chamadas HTTP `fetch` nativas do Node.js.

### Como Executar

```bash
# Execução padrão (10 conexões simultâneas, 100 requisições)
node load-tests/run.js

# Customizar concorrência (-c) e total de requisições (-n)
node load-tests/run.js -c 20 -n 500
node load-tests/run.js --concurrency 50 --requests 1000

# Executar via npm script
npm --prefix load-tests start -- -c 5 -n 50

# Customizar endpoint de destino (padrão: http://localhost:8081/api/v1/accounts)
node load-tests/run.js -u http://localhost:8080/api/v1/accounts -c 10 -n 100
# Ou via variável de ambiente
TARGET_URL=http://localhost:8081/api/v1/accounts node load-tests/run.js -c 10 -n 100

# Exibir ajuda e opções de CLI
node load-tests/run.js --help
```

Para mais detalhes de arquitetura, parâmetros CLI e documentação completa, consulte [load-tests/README.md](./load-tests/README.md).

---

## 📊 Observabilidade Local (OpenTelemetry & Grafana LGTM)

O **Bank Ledger** adota o padrão de observabilidade distribuída baseado no ecossistema **OpenTelemetry** e na stack **Grafana LGTM** (*Loki, Grafana, Tempo, Mimir*), permitindo rastrear o ciclo de vida completo de qualquer transação financeira através de microsserviços e mensageria assíncrona Kafka.

### 🧩 Arquitetura e Componentes Integrados

A stack de observabilidade é executada localmente via container All-in-One (`grafana/otel-lgtm`):

* **Loki (Logs)**: Ingestão e agregação de logs estruturados dos microsserviços com enriquecimento contextual de `traceId` e `spanId`.
* **Grafana (Dashboards & Explore)**: Interface visual centralizada acessível em [`http://localhost:3001`](http://localhost:3001) (usuário: `admin` / senha: `admin`).
* **Tempo (Distributed Tracing)**: Mecanismo de rastreamento distribuído com suporte a consultas via *TraceQL* e visualização de timelines em cascata (*waterfall*).
* **Mimir (Métricas)**: Armazenamento escalável e compatível com Prometheus para monitoramento contínuo de métricas do Spring Boot Actuator.
* **OTLP Receivers**: Portas `4318` (HTTP) e `4317` (gRPC) prontas para ingestão de telemetria das aplicações Spring Boot via OTLP.

### 🏷️ Padrão de Logs e Correlação de Traces

Todos os microsserviços utilizam o starter oficial `org.springframework.boot:spring-boot-starter-opentelemetry` e o `logback-spring.xml` unificado em `platform-commons`. O nome do serviço é configurado explicitamente no padrão de log para simplificar e agilizar buscas e filtros no Grafana Loki:

```text
[<service-name>] [<traceId>,<spanId>]
```

Exemplo real capturado no console:
```text
2026-11-16T19:30:15.123-03:00  INFO [transaction-service] [67362a7b10f84a3b8e2d4f119a733d4e,01f84a3b8e2d4f11] 1 --- [          main] c.b.t.a.i.w.TransactionController       : Received deposit request for account 01923456-789a-7b8c-9d0e-1f2a3b4c5d6e
```

O contexto W3C Trace Context (`traceparent`) é propagado automaticamente nas chamadas síncronas HTTP (via RestClient/RestTemplate/WebClient). Para fluxos assíncronos desacoplados via Transactional Outbox e Kafka, a propagação contínua de trace entre fronteiras assíncronas requer a captura do `traceparent` no momento da criação do evento de Outbox e a habilitação de `spring.kafka.template.observation-enabled: true` e `spring.kafka.listener.observation-enabled: true`, planejada para evolução das sagas assíncronas.

### 🔍 Como Testar e Rastrear uma Transação Ponta a Ponta

#### 1. Disparar uma Transação via Postman
Utilize a coleção oficial do projeto localizada em:
* 📁 `postman/Bank-Ledger-API.postman_collection.json` (com as variáveis de ambiente em `postman/Bank-Ledger-Local.postman_environment.json`).

* Dispare uma requisição de depósito: `POST http://localhost:8080/api/v1/transactions/deposits` (ou diretamente na porta `:8082`).
* Copie o `traceId` gerado nos logs do container do `transaction-service`:
  ```bash
  docker compose -f infra/docker/docker-compose.yml logs -f transaction-service
  ```

#### 2. Investigar no Grafana Explore
1. Acesse o Grafana em [`http://localhost:3001`](http://localhost:3001) e faça login (`admin` / `admin`).
2. Acesse o menu lateral **Explore**:
   * **Consulta de Logs no Loki**:
     Selecione o datasource **Loki** e consulte pelos logs correlacionados da transação:
     ```logql
     {service_name=~".+"} |= "<seu-traceId>"
     ```
     Ou filtre por serviço específico:
     ```logql
     {service_name="transaction-service"}
     ```
   * **Timeline em Cascata no Tempo**:
     Selecione o datasource **Tempo**, cole o `traceId` no campo de busca ou TraceQL, e visualize a cascata detalhada (*waterfall*):
     - `HTTP POST /api/v1/transactions/deposits` (`transaction-service`)
     - Publicação no tópico Kafka `transaction.events.v1` / `ledger.commands.v1`
     - Consumo do evento pelo `ledger-service`
     - Gravação contábil em partidas dobradas e persistência no PostgreSQL

---

*Repositório inicializado e mantido ativamente com base nas práticas modernas de engenharia de software.*
