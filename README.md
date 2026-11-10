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
| **Account Service** | `http://localhost:8081` | Porta direta do microsserviço |
| **Swagger UI** | [`http://localhost:8081/swagger-ui.html`](http://localhost:8081/swagger-ui.html) | Documentação interativa OpenAPI 3.0 com autenticação JWT |
| **OpenAPI Docs** | [`http://localhost:8081/v3/api-docs`](http://localhost:8081/v3/api-docs) | Especificação JSON da API |
| **Actuator Health** | [`http://localhost:8081/actuator/health`](http://localhost:8081/actuator/health) | Health check detalhado da aplicação |
| **Kafka UI** | [`http://localhost:8085`](http://localhost:8085) | Painel visual para inspeção de tópicos e mensagens Kafka |
| **PostgreSQL 18** | `localhost:5432` | Bancos: `account_db`, `ledger_db`, `transaction_db`, `query_db`<br>Usuário: `bank_ledger` / Senha: `bank_ledger_secret` |

---

*Repositório inicializado e mantido ativamente com base nas práticas modernas de engenharia de software.*
