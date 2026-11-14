# Bank Ledger

Bem-vindo ao repositório do **Bank Ledger**, uma plataforma de Core Bancário (Ledger) desenhada para garantir consistência financeira com alta escalabilidade. 

Este projeto baseia-se no princípio contábil das **Partidas Dobradas (Double-Entry Accounting)**, onde toda movimentação possui origens e destinos simétricos (débito e crédito), garantindo que não haja perda de valores e facilitando processos de auditoria (balancete e extrato confiáveis).

## 🏗️ Arquitetura Resumida

O sistema utiliza padrões modernos para garantir alta disponibilidade e escalabilidade de dados financeiros:
* **Backend em Java 25 & Spring Boot 4.1.2**: Microsserviços independentes com Gradle (sintaxe padrão Java) sobre Spring Boot 4.1.2 (starters modulares, Spring Framework 7, Jackson 3) com suporte a Virtual Threads nativas.
* **API Gateway (Nginx na porta 8080)**: Ponto de entrada unificado para o tráfego do frontend, distribuindo chamadas para os microsserviços isolados.
* **Arquitetura Hexagonal (Ports & Adapters)**: Desacoplamento da regra de negócio (domínio) de frameworks, bancos de dados e mensageria.
* **CQRS (Command Query Responsibility Segregation)**: Separação lógica entre rotinas de gravação de transações e projeções desnormalizadas de extrato e saldo no PostgreSQL.
* **Consistência Eventual Segura**: Utilização do padrão *Transactional Outbox* para publicação assíncrona confiável de eventos em tópicos do Kafka (KRaft).
* **Tolerância e Resiliência**: Particionamento de tabelas por data (`pg_partman`), particionamento das contas de caixa (*shards* do Banco Central) para evitar concorrência/lock, e suporte a alta idempotência na ingestão de eventos.
* **Real-Time Updates**: Utilização de *Server-Sent Events (SSE)* no Frontend em substituição ao polling, evitando gargalos de I/O em fluxos de atualização contínua.

## 📚 Documentação Interna & Interfaces

Todo o planejamento de negócio, topologia do sistema, diagramas de sequência, contratos e fluxos assíncronos foram detalhados e mantidos internamente.

* 👉 **[Plano de Desenvolvimento e Arquitetura (Spring Boot 4.1.2 & Java 25)](./docs/plano_desenvolvimento_ledger.md)**: Roteiro técnico completo, decisões arquiteturais, requisitos de governança com ArchUnit e modelagem de domínios bancários.
* 👉 **[Contratos de API (Swagger/OpenAPI)](./api-contracts/)**
* 👉 **[Frontend Web Banking (Next.js 16 / App Router)](./web/)**: Interface de Web Banking completa e funcional em modo autônomo (mock reativo integrado), incluindo telas de Visão Geral de Saldo, Extrato Oficial, Transferência Segura entre Contas, Depósito e Saque, com linguagem 100% amigável ao cliente bancário e zero jargões de infraestrutura.

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

2. **Executar a suíte de testes (utiliza Testcontainers automaticamente):**
   * No Linux / macOS:
     ```bash
     ./gradlew test
     ```
   * No Windows (PowerShell / CMD):
     ```powershell
     .\gradlew.bat test
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
| **Account Service** | `http://localhost:8081` | Gestão de contas, clientes e autenticação JWT |
| **Transaction Service** | `http://localhost:8082` | Orquestração de transações, depósitos, saques e transferências |
| **Query Service** | `http://localhost:8083` | Projeções de leitura CQRS, extrato e saldos desnormalizados |
| **Ledger Service** | `http://localhost:8084` | Core bancário interno (Partidas Dobradas, Shards de Caixa, Journal) |
| **Swagger UI (Account)** | [`http://localhost:8081/swagger-ui.html`](http://localhost:8081/swagger-ui.html) | Documentação interativa OpenAPI 3.0 com autenticação JWT |
| **Actuator Health (Account)** | [`http://localhost:8081/actuator/health`](http://localhost:8081/actuator/health) | Health check detalhado da aplicação |
| **Kafka UI** | [`http://localhost:8085`](http://localhost:8085) | Painel visual para inspeção de tópicos e mensagens Kafka |
| **Grafana UI (LGTM)** | [`http://localhost:3001`](http://localhost:3001) | Painel unificado de Observabilidade (Logs, Traces e Métricas - user/pass: `admin`/`admin`) |
| **OTLP Exporter (HTTP)** | `http://localhost:4318` | Endpoint de recepção de telemetria OpenTelemetry via HTTP |
| **OTLP Exporter (gRPC)** | `http://localhost:4317` | Endpoint de recepção de telemetria OpenTelemetry via gRPC |
| **PostgreSQL 18** | `localhost:5432` | Bancos: `account_db`, `ledger_db`, `transaction_db`, `query_db`<br>Usuário: `bank_ledger` / Senha: `bank_ledger_secret` |

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

Todos os microsserviços utilizam o starter oficial `io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter` configurado com propagação de MDC (`service_name`, `trace_id`, `span_id`). O padrão de log configurado é:

```text
[service-name,traceId,spanId]
```

Exemplo real capturado no console:
```text
2026-11-14 17:30:15.123 [INFO ] [transaction-service,67362a7b10f84a3b8e2d4f119a733d4e,01f84a3b8e2d4f11] [virtual-28] c.b.t.a.i.w.TransactionController : Received deposit request for account 01923456-789a-7b8c-9d0e-1f2a3b4c5d6e
```

O contexto W3C Trace Context (`traceparent`) é automaticamente propagado entre serviços síncronos (HTTP via RestClient/RestTemplate) e assíncronos (mensagens Kafka através de headers OpenTelemetry), garantindo rastreabilidade contínua ponta a ponta sem perda de contexto entre o `transaction-service`, os tópicos Kafka e o `ledger-service`.

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
     - Publicação no tópico Kafka `transaction-events`
     - Consumo do evento pelo `ledger-service`
     - Gravação contábil em partidas dobradas e persistência no PostgreSQL

---

*Repositório inicializado e mantido ativamente com base nas práticas modernas de engenharia de software.*
