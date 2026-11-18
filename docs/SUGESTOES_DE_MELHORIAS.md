# Bank Ledger — Sugestões de Melhorias e Roadmap Evolutivo

> **Documento de Propostas Técnicas, Arquiteturais e de Negócio**  
> *Versão:* 1.0  
> *Data:* Novembro de 2026

Este documento reúne recomendações de engenharia e oportunidades de evolução para o **Bank Ledger**, estruturadas por pilares estratégicos para guiar futuras iterações e novas decisões arquiteturais.

---

## 1. Performance, Escalabilidade e Concorrência

### 1.1 Migração de Polling para Change Data Capture (CDC com Debezium)
- **Cenário Atual:** O padrão Transactional Outbox em `account-service` e `transaction-service` utiliza um poller periódico (`OutboxPoller`) agendado a cada 200ms que varre a tabela `outbox_events` no PostgreSQL via `FOR UPDATE SKIP LOCKED`.
- **Proposta de Melhoria:** Adotar **Debezium Engine / Kafka Connect** para realizar CDC diretamente no Write-Ahead Log (WAL) do PostgreSQL:
  - *Vantagens:* Latência de publicação cai de ~200ms para **< 5ms**; elimina 100% das queries de varredura periódica e contenção de índices no banco relacional; preservação rigorosa da ordem de escrita.
  - *Complexidade:* Média (requer habilitar `wal_level = logical` no PostgreSQL e orquestrar o container do Debezium).

### 1.2 R2DBC Nativo no `auth-service`
- **Cenário Atual:** O `auth-service` opera com Spring WebFlux e Netty, mas acessa o PostgreSQL via Spring Data JPA / Hibernate (JDBC), executando chamadas bloqueantes em `Schedulers.boundedElastic()`.
- **Proposta de Melhoria:** Migrar a persistência do `auth-service` para **Spring Data R2DBC** (Reactive Relational Database Connectivity):
  - *Vantagens:* Arquitetura 100% não bloqueante de ponta a ponta sem pool de threads elástico para JDBC; menor footprint de memória e maior suporte a rajadas massivas de autenticação.
  - *Complexidade:* Baixa/Média (as entidades do `auth-service` são enxutas e não utilizam relacionamentos complexos como `@OneToMany`).

### 1.3 Cache Distribuído de Projeções de Saldo no Redis (`query-service`)
- **Cenário Atual:** As consultas de saldo (`/api/v1/queries/balance`) sempre consultam a tabela desnormalizada `account_balance` no PostgreSQL (`query_db`).
- **Proposta de Melhoria:** Implementar uma camada de cache *read-through / write-around* no Redis com invalidação orientada a eventos:
  - Ao consumir o evento `EntryPosted` do Kafka, o `query-service` atualiza atomicamente o banco e atualiza/invalida a chave `balance:{accountId}` no Redis.
  - *Vantagens:* Latência de leitura de saldos cai para **< 1ms**, desonerando o PostgreSQL mesmo sob milhões de acessos simultâneos de visualização de saldo no aplicativo mobile/web.

### 1.4 ~~Formalização do Lease Timeout no Outbox Poller Multi-Instância~~ ✅ Implementado
- **Status:** ✅ **Concluído e Implementado no Core** (Commit `d00bb7d`)
- **Implementação Realizada:**
  - Adicionada coluna `locked_until` (TIMESTAMP) e índice na tabela `outbox_event` via migração Flyway V2 em `account-service`, `ledger-service` e `transaction-service`.
  - Refatorado o `OutboxPoller` para operar em 3 fases: **Claim Atômico** (com `FOR UPDATE SKIP LOCKED` e lease de 30s) $\rightarrow$ **Publicação Kafka** (fora de transação DB) $\rightarrow$ **Persistência de Estado** (`markPublished()` para sucesso ou `releaseLock()` / `markFailed()` para retry).
  - Query refatorada com filtros de concorrência: `WHERE published_at IS NULL AND (locked_until IS NULL OR locked_until < :now) AND attempts < :maxAttempts`.
  - Adicionado teste de integração multi-thread `concurrentPollersDoNotPublishDuplicateEvents` em `platform-commons`.
  - Garantia de suporte à escalabilidade horizontal (múltiplas instâncias/réplicas do mesmo serviço) com zero duplicação de mensagens no Kafka.

---

## 2. Segurança, Governança e Resiliência

### 2.1 Multi-Factor Authentication (MFA / 2FA)
- **Cenário Atual:** MFA foi deliberadamente omitido na fase inicial de laboratório para viabilizar testes de carga contínuos.
- **Proposta de Melhoria:**
  - Suporte a **TOTP (RFC 6238)** via Google Authenticator / Microsoft Authenticator no `auth-service`.
  - Suporte a **WebAuthn / Passkeys (FIDO2)** para biometria nativa em dispositivos móveis e navegadores modernos.
  - Step-up Authentication: Exigir segundo fator condicionalmente para transferências acima de limites de segurança (ex: > R$ 5.000,00) ou acessos a partir de IPs/dispositivos não reconhecidos.

### 2.2 Gestão e Rotação Automatizada de Chaves Criptográficas (JWKS) com HashiCorp Vault
- **Cenário Atual:** As chaves RSA são geradas dinamicamente na inicialização do serviço pelo `RsaJwtSigner` com armazenamento em memória.
- **Proposta de Melhoria:** Integrar o `auth-service` ao **HashiCorp Vault** (Transit Secrets Engine) ou AWS KMS:
  - Armazenamento persistente e seguro da Chave Privada em HSM.
  - Rotação programada transparente (Key Versioning): o `auth-service` expõe as chaves pública atual e anterior no `/.well-known/jwks.json`, permitindo rotação com zero downtime.

### 2.3 Contratos Formais e Schema Registry no Kafka (Apache Avro / Protobuf)
- **Cenário Atual:** Mensagens trafegam em JSON CloudEvents 1.0 desserializadas via Jackson 3 fortemente tipado com `KafkaEventEnvelope<T>`.
- **Proposta de Melhoria:** Evoluir para **Apache Avro** ou **Protocol Buffers (Protobuf)** com **Confluent Schema Registry**:
  - *Vantagens:* Validação estrita de compatibilidade retroativa/progressiva de contratos (*Backward/Forward Compatibility*); redução de ~60% no tamanho das mensagens no Kafka (formato binário compacto); geração automática de DTOs nas linguagens clientes.

### 2.4 Dead Letter Queue (DLQ) com Reprocessamento Automatizado
- **Cenário Atual:** Falhas não recuperáveis no consumo acionam retentativas locais ou logging de erro.
- **Proposta de Melhoria:** Implementar padrão formal de **Retry Topic + DLT (Dead Letter Topic)** no Spring Kafka:
  - Tópico original -> Tópico de retry com backoff exponencial (1m, 5m, 15m) -> Tópico `.dlt` após esgotar tentativas.
  - Endpoint ou painel de gestão para reprocessamento manual/automático de mensagens da DLT após correção de bugs.

### 2.5 ~~Proteção de Dados Pessoais (LGPD) na Consulta de Favorecidos (POST Seguro)~~ ✅ Implementado
- **Status:** ✅ **Concluído e Implementado no Core** (Commit `0abf49f`)
- **Implementação Realizada:**
  - Substituição da consulta via query parameters (`GET /api/v1/accounts/lookup?agency=...` ou `cpf=...`) pelo endpoint seguro **`POST /api/v1/accounts/lookup`**.
  - **Conformidade LGPD e OWASP:** Eliminação de dados sensíveis (PII como CPF e conta) na URL, prevenindo vazamentos em logs de acesso de servidores web, proxies reversos (Nginx), histórico de navegadores e cabeçalhos `Referer`.
  - Suporte a busca flexível por chave CPF ou por par Agência/Conta com validação de payload JSON criptografado via TLS.
  - Retorno do nome desmascarado do favorecido para verificação visual e confirmação segura antes da conclusão de transferências financeiras.

---

## 3. Funcionalidades de Negócio (Core Banking)

### 3.1 Módulo Pix (Pagamentos Instantâneos)
- **Funcionalidades:**
  - Cadastro e gestão de Chaves Pix (CPF, e-mail, telefone, EVP/aleatória).
  - Envio e recebimento de Pix com liquidação em partidas dobradas:
    - Débito na conta do cliente $\leftrightarrow$ Crédito na Conta de Reserva Bancária / SPI do BACEN.
  - Webhook e mensageria assíncrona para conciliação em tempo real.

### 3.2 ~~Motor de Limites Diários Parametrizáveis por Conta no Banco de Dados~~ ✅ Implementado
- **Status:** ✅ **Concluído e Implementado no Core** (Commits `0abf49f`, `a6330e4`, `f2fdb6a`, `827b07a`, `65e45ce`, `75b423c`)
- **Implementação Realizada:**
  - **Governança no Banco de Dados:** Criada tabela `account_limit` em `account-service` e `transaction-service` (Flyway V3) com coluna `daily_transfer_limit_cents BIGINT NOT NULL DEFAULT 5000000` (R$ 50.000,00 inicial), parametrizável individualmente por conta.
  - **Eliminação de Validação Estática no Código:** Removida a anotação estática `@Max(50000)` em `TransferRequest`. A validação é 100% dinâmica via consulta SQL em banco de dados (`AccountLimitRepositoryPort`), calculando a soma das transferências concluídas no dia corrente no fuso de Brasília (`America/Sao_Paulo`).
  - **Cálculo Reativo em Consultas CQRS (`query-service`):** O endpoint `GET /api/v1/queries/balance` calcula e retorna dinamicamente `dailyLimitCents`, `usedTodayCents` e `availableLimitCents = max(0, dailyLimitCents - usedTodayCents)` com agregação diária dos lançamentos de débito.
  - **Interface com Barra Reativa no Frontend (`web`):** Tela de transferência com monitoramento em tempo real do limite diário consumido e disponível, com transição dinâmica de cores semânticas (verde < 70%, âmbar 70-99%, vermelho >= 100%) e bloqueio preventivo de submissão quando o saldo diário for insuficiente.
- **Evoluções Futuras do Pilar de Risco:**
  - Limites diferenciados por faixa de horário (diurno vs. noturno conforme Resolução BACEN).
  - Motor de antifraude e detecção de anomalias em tempo real com pontuação de risco (*risk scoring*) antes da emissão do comando contábil.

### 3.3 Boletos Bancários e Convênios
- **Funcionalidades:**
  - Registro de boletos com código de barras / linha digitável e QR Code Pix integrado (Boleto Híbrido).
  - Processamento de arquivos de remessa e retorno (CNAB 240/400) com liquidação contábil no ledger.

### 3.4 Operações Agendadas e Recorrentes
- **Funcionalidades:**
  - Agendamento de transferências e pagamentos com engine de execução distribuída (ShedLock / Quartz / Temporal.io) integrado ao Outbox.

---

## 4. Frontend e Experiência do Usuário (DX)

### 4.1 Server-Sent Events (SSE) ou WebSockets para Atualização em Tempo Real
- **Cenário Atual:** O frontend realiza polling HTTP ou aguarda recarregamento manual para refletir transações no saldo e extrato.
- **Proposta de Melhoria:** Estabelecer uma conexão contínua de **Server-Sent Events (SSE)** entre o Next.js e o `query-service`:
  - Assim que o `query-service` consome o evento `EntryPosted` do Kafka, empurra o novo saldo e a nova linha de extrato instantaneamente para o navegador do cliente.
  - *Vantagens:* Elimina 100% do tráfego de polling do frontend; experiência de usuário reativa em tempo real ("estilo Nubank/Inter").

### 4.2 Suíte de Testes End-to-End Automatizada com Playwright
- **Cenário Atual:** Testes unitários no backend (JUnit 5 / Testcontainers) e testes de componentes/serviços no Next.js.
- **Proposta de Melhoria:** Adicionar uma suíte de testes E2E com **Playwright** que sobe os containers via Docker e testa a jornada completa do cliente:
  - Criação de conta -> Validação do evento no Kafka -> Login com JWT -> Depósito -> Transferência entre contas -> Verificação do saldo e extrato atualizados.

### 4.3 ~~Gestão Cadastral Completa de Perfil com Endereço e Auto-preenchimento por CEP~~ ✅ Implementado
- **Status:** ✅ **Concluído e Implementado** (Commits `0abf49f`, `006d774`, `ce9c30d`)
- **Implementação Realizada:**
  - **Campos de Endereço no Backend:** Modelagem de `Address` e migração Flyway V3 em `account-service` com persistência na tabela `customer`.
  - **Endpoints de Perfil:** `GET /api/v1/accounts/me` (enriquecido com endereço, limite e `birthDate`) e `PUT /api/v1/accounts/me/profile` (atualização de dados de contato e residência com regras de imutabilidade para Nome, CPF e Data de Nascimento).
  - **Interface Web (`/dashboard/profile`):** Visualização de dados cadastrais protegidos e edição assistida de endereço com preenchimento automático integrado via API pública do ViaCEP e máscaras acessíveis (CEP, telefone celular).

### 4.4 ~~Extrato Bancário Humanizado e Comprovante Oficial com Protocolo Canônico UUID~~ ✅ Implementado
- **Status:** ✅ **Concluído e Implementado** (Commits `c0d9944`, `65e45ce`, `75b423c`, `36d7321`, `05e6950`)
- **Implementação Realizada:**
  - **Geração Canônica de Protocolo UUID:** `BankingProtocolGenerator` gerando UUID nativo (`UUID.randomUUID().toString()`) com persistência em coluna `protocol_number VARCHAR(64)` (Flyway V4) e propagação em toda a malha de eventos Kafka.
  - **Humanização em Português:** Lançamentos com rótulos amigáveis (*Transferência enviada*, *Transferência recebida*, *Depósito em conta corrente*, *Saque em terminal*) e identificação clara de contraparte com prefixos "De: / Para:".
  - **Comprovante Oficial Individual:** Modal de comprovante oficial com protocolo canônico UUID, hash de autenticação SHA-256 e suporte nativo a impressão limpa sem ruídos de tela (`window.print()` e `@media print`).
  - **Sanitização de Dados:** Função `parseAccountDetails()` para decompor e sanitizar dados legados e compostos no Destino (Favorecido), eliminando duplicações de rótulos e quebras de linha indesejadas.

---

## 5. Matriz de Priorização Recomendada (Impacto vs. Esforço)

| Iniciativa | Impacto | Esforço | Prioridade Recomendada |
| :--- | :---: | :---: | :---: |
| ~~**Lease Timeout no Outbox Poller Multi-Instância**~~ | Alto | Médio | ✅ **Concluído (Commit `d00bb7d`)** |
| ~~**Motor de Limites Diários Parametrizáveis no Banco**~~ | Alto | Médio | ✅ **Concluído (Commits `0abf49f`, `a6330e4`, `f2fdb6a`)** |
| ~~**Consulta Segura de Favorecido (POST /lookup LGPD)**~~ | Alto | Baixo | ✅ **Concluído (Commit `0abf49f`)** |
| ~~**Gestão de Perfil de Usuário com Endereço e CEP**~~ | Médio | Médio | ✅ **Concluído (Commits `0abf49f`, `006d774`, `ce9c30d`)** |
| ~~**Protocolo Canônico UUID e Comprovante Oficial**~~ | Alto | Baixo | ✅ **Concluído (Commits `65e45ce`, `75b423c`, `05e6950`)** |
| **Server-Sent Events (SSE) no Frontend/Query** | Alto | Baixo | 🟢 **Curto Prazo (P1)** |
| **Cache de Saldo no Redis (`query-service`)** | Alto | Baixo | 🟢 **Curto Prazo (P1)** |
| **DLQ com Reprocessamento no Spring Kafka** | Alto | Médio | 🟡 **Médio Prazo (P2)** |
| **R2DBC Nativo no `auth-service`** | Médio | Médio | 🟡 **Médio Prazo (P2)** |
| **MFA (TOTP / WebAuthn FIDO2)** | Alto | Médio | 🟡 **Médio Prazo (P2)** |
| **CDC com Debezium no Outbox** | Alto | Alto | 🔴 **Longo Prazo (P3)** |
| **Módulo Pix & Conciliação SPI** | Altíssimo | Alto | 🔴 **Longo Prazo (P3)** |
| **Schema Registry com Avro** | Médio | Alto | 🔴 **Longo Prazo (P3)** |
