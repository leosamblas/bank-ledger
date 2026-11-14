# Observabilidade com Spring Boot 4 OpenTelemetry e Grafana LGTM Stack

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implementar observabilidade moderna e unificada (Logs + Traces + Métricas) no Bank Ledger utilizando o starter nativo `spring-boot-starter-opentelemetry` do Spring Boot 4 e a imagem unificada All-in-One `grafana/otel-lgtm` via Docker Compose.

**Architecture:** Cada microsserviço Spring Boot 4 utiliza o starter oficial `spring-boot-starter-opentelemetry` para gerar `traceId`/`spanId`, propagar contexto via MDC nos logs e via W3C TraceContext no Kafka, e exportar telemetria via OTLP (OpenTelemetry Protocol). O backend de observabilidade é provido pelo container `grafana/otel-lgtm`, que agrega em um único processo: Loki (logs), Tempo (traces distribuídos), Mimir (métricas) e Grafana (UI na porta 3001).

**Tech Stack:** Spring Boot 4.1.2, Java 25, `spring-boot-starter-opentelemetry`, OpenTelemetry Protocol (OTLP), Docker Compose, `grafana/otel-lgtm`, Grafana 11, Tempo, Loki.

**Spec:** Arquitetura baseada na referência oficial do Dan Vega (`danvega/ot`) para Spring Boot 4 + LGTM Stack, adaptada para o ecossistema multi-serviço do Bank Ledger.

## Global Constraints

- O Grafana da stack LGTM deve ser exposto na porta `3001:3000` para evitar conflito com o frontend Next.js (`port 3000`).
- Usar exclusivamente o starter oficial `org.springframework.boot:spring-boot-starter-opentelemetry`, sem pontes legadas ou frameworks duplicados.
- O endpoint OTLP deve possuir fallback dinâmico: `http://localhost:4318` para testes locais no host e `http://otel-lgtm:4318` quando rodando em container Docker.
- A amostragem de tracing deve ser configurada em 100% (`1.0`) para desenvolvimento local.
- O padrão de log no MDC deve seguir `%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]`.

## Review Focus

1. **Resolução de Dependências do Starter:** Garantir que o BOM do Spring Boot 4.1.2 gerencie o `spring-boot-starter-opentelemetry` sem incompatibilidade de versões transitivas.
2. **Propagação de Contexto W3C no Kafka:** Assegurar que o cabeçalho `traceparent` trafegue entre `transaction-service` e `ledger-service`, mantendo o mesmo `traceId` nos logs de ambos os lados da saga.
3. **Resiliência a Falhas de Telemetria:** Assegurar que os microsserviços iniciem normalmente e atendam requisições bancárias mesmo se o container `otel-lgtm` não estiver em execução.
4. **Isolamento de Portas:** Garantir que a porta 3000 permaneça estritamente reservada para o container `bank-ledger-web`.
5. **Configuração do Docker Compose:** Garantir que o serviço `otel-lgtm` compartilhe a rede `bank-ledger-network`.

---

### Task 1: Habilitação de OpenTelemetry e MDC Trace Pattern nos Microsserviços

**Files:**
- Modify: `services/account-service/build.gradle`
- Modify: `services/ledger-service/build.gradle`
- Modify: `services/transaction-service/build.gradle`
- Modify: `services/query-service/build.gradle`
- Modify: `services/account-service/src/main/resources/application.yml`
- Modify: `services/ledger-service/src/main/resources/application.yml`
- Modify: `services/transaction-service/src/main/resources/application.yml`
- Modify: `services/query-service/src/main/resources/application.yml`
- Test: `services/account-service/src/test/java/com/bankledger/account/config/OpenTelemetryLoggingTest.java`

**Interfaces:**
- Consumes: Spring Boot 4.1.2 BOM
- Produces: Injeção de `traceId` e `spanId` no MDC de logs e exportação OTLP configurada.

- [ ] **Step 1: Adicionar `spring-boot-starter-opentelemetry` nos submódulos**
  - Nos arquivos `build.gradle` de `account-service`, `ledger-service`, `transaction-service` e `query-service`:
    ```groovy
    // OpenTelemetry Starter (Spring Boot 4)
    implementation 'org.springframework.boot:spring-boot-starter-opentelemetry'
    ```
- [ ] **Step 2: Configurar propriedades OTLP e padrão de logs nos `application.yml`**
  - Adicionar em cada serviço:
    ```yaml
    management:
      tracing:
        sampling:
          probability: 1.0

    spring:
      opentelemetry:
        tracing:
          export:
            otlp:
              endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318}/v1/traces
        logging:
          export:
            otlp:
              endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318}/v1/logs

    logging:
      pattern:
        level: "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]"
    ```
- [ ] **Step 3: Criar teste de verificação de traceId no log**
  - Criar `OpenTelemetryLoggingTest.java` em `account-service` fazendo um disparo HTTP com MockMvc e validando que um `traceId` não-nulo é gerado no contexto da requisição.
- [ ] **Step 4: Executar suíte de testes do Gradle**
  - Rodar `./gradlew test` garantindo que todos os testes passem com 100% de sucesso.
- [ ] **Step 5: Commit**
  - Mensagem: `feat(observability): enable spring-boot-starter-opentelemetry and MDC trace pattern across services`

---

### Task 2: Configuração do Serviço `otel-lgtm` no Docker Compose

**Files:**
- Modify: `infra/docker/docker-compose.yml`

**Interfaces:**
- Consumes: Imagem `grafana/otel-lgtm:latest`
- Produces: Stack All-in-One de observabilidade rodando na rede `bank-ledger-network`.

- [ ] **Step 1: Declarar o serviço `otel-lgtm` no `docker-compose.yml`**
  - Adicionar:
    ```yaml
      otel-lgtm:
        image: grafana/otel-lgtm:latest
        container_name: bank-ledger-otel-lgtm
        restart: unless-stopped
        ports:
          - "3001:3000" # Grafana UI (porta 3001 para não colidir com o frontend web 3000)
          - "4317:4317" # OTLP gRPC receiver
          - "4318:4318" # OTLP HTTP receiver
        environment:
          - GF_SECURITY_ADMIN_USER=admin
          - GF_SECURITY_ADMIN_PASSWORD=admin
        deploy:
          resources:
            limits:
              memory: 1024M
        networks:
          - bank-ledger-network
    ```
- [ ] **Step 2: Configurar variáveis de ambiente de telemetria nos containers de aplicação**
  - Nos serviços `account-service`, `ledger-service`, `transaction-service` e `query-service` do `docker-compose.yml`, adicionar:
    ```yaml
    OTEL_EXPORTER_OTLP_ENDPOINT: http://otel-lgtm:4318
    ```
- [ ] **Step 3: Validar integridade da configuração Docker**
  - Executar `docker compose -f infra/docker/docker-compose.yml config --quiet`.
- [ ] **Step 4: Commit**
  - Mensagem: `infra(docker): add grafana/otel-lgtm all-in-one observability service`

---

### Task 3: Validação Ponta a Ponta com Postman e Rastreamento de Transação

**Files:**
- Test: Execução manual com Docker e Postman
- Modify: `README.md` (instruções de observabilidade)

- [ ] **Step 1: Validar subida dos serviços no Docker**
  - Subir o container: `docker compose -f infra/docker/docker-compose.yml up -d otel-lgtm`.
  - Confirmar que o Grafana responde em `http://localhost:3001`.
- [ ] **Step 2: Executar transação via Postman Collection**
  - Disparar `POST /api/v1/transactions/deposits`.
  - Verificar no log da aplicação a geração do `traceId`:
    `INFO [transaction-service,67362a7b10f84a3b8e2d4f119a733d4e,01f84a3b8e2d4f11] ...`
- [ ] **Step 3: Verificar visualização no Grafana Explore**
  - Acessar `http://localhost:3001` (login: `admin` / `admin`).
  - No menu **Explore**:
    - Consultar **Loki**: filtrar por `traceId` ou `{service_name="transaction-service"}`.
    - Consultar **Tempo**: colar o `traceId` no campo TraceQL para visualizar a timeline em cascata.
- [ ] **Step 4: Atualizar documentação do projeto**
  - Documentar no `README.md` a seção de Observabilidade Local com acesso ao Grafana (3001) e exploração de traces.
- [ ] **Step 5: Commit**
  - Mensagem: `docs(observability): document OpenTelemetry and Grafana LGTM usage in README`
