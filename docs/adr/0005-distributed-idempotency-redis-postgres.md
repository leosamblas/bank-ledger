# ADR 0005: Idempotência Híbrida de Dois Níveis com Redis e PostgreSQL

## Status
Aprovado

## Contexto e Pergunta do Usuário
No ecossistema de serviços bancários e pagamentos (`transaction-service`), a garantia de processamento com semântica de execução única (*exactly-once processing* do ponto de vista do cliente) é um requisito regulatório e contábil mandatório. O cliente ou canal originador (aplicativo mobile, gateway de pagamentos, internet banking) envia o cabeçalho HTTP padrão `Idempotency-Key` (UUIDv4) em operações mutáveis (saques, depósitos, transferências e pagamentos de boletos).

Foi levantada a seguinte análise pelo usuário:
> *"Avaliar Idempotency usando redis para concorrência multi instance"*

### O Gargalo da Idempotência Estritamente Relacional
Historicamente, o controle de idempotência é implementado exclusivamente no banco de dados relacional (ex: tabela `idempotency_keys` no PostgreSQL com constraint de unicidade `UNIQUE (key)` e transações com `INSERT ... ON CONFLICT`).

Sob regimes de **alta concorrência massiva** (ex: picos de 50.000+ req/s durante campanhas promocionais, viradas de lote de folha de pagamento, ou tempestades de retentativas automáticas geradas por timeouts de rede nos clientes HTTP), o modelo estritamente relacional colapsa pelos seguintes motivos:
1. **Exaustão de Conexões no Pool JDBC (HikariCP)**: Milhares de requisições concorrentes idênticas ou duplicadas abrem transações no PostgreSQL apenas para verificar se a chave existe, saturando o pool de conexões e enfileirando requisições legítimas.
2. **Contenção Violenta de Linhas e Índices (*Index Page Lock Contention*)**: Múltiplas threads disputando inserções concorrentes na mesma chave geram *deadlocks* ou esperas prolongadas no índice B-Tree da chave única.
3. **Desperdício de I/O de Disco**: Escrever e rejeitar transações no nível do WAL (*Write-Ahead Logging*) do PostgreSQL para requisições duplicadas consome I/O desnecessário da base de dados principal.

---

## Decisão Arquitetural

Adotamos uma **Arquitetura de Idempotência Híbrida em Dois Níveis (Two-Tier Hybrid Idempotency)**, combinando a velocidade em memória sub-milissegundo do **Redis** para bloqueio de requisições em trânsito (*in-flight*) com a persistência duradoura e auditável do **PostgreSQL** para histórico definitivo e conformidade financeira.

```
                           +---------------------------+
                           | Requisição HTTP           |
                           | Idempotency-Key: <UUID>   |
                           +-------------+-------------+
                                         |
                                         v
                 +-----------------------------------------------+
                 | TIER 1: In-Flight Lock (Redis)                |
                 | SET lock:idempotency:{key} {workerId} NX EX 60|
                 +-----------------------+-----------------------+
                                         |
                     +-------------------+-------------------+
                     | Lock Adquirido?                       |
                    NÃO                                     SIM
                     |                                       |
                     v                                       v
        +-------------------------+             +--------------------------+
        | HTTP 409 Conflict       |             | Executa Operação         |
        | Retry-After: 2          |             | Negocial Bancária        |
        | (ZERO carga PostgreSQL) |             +------------+-------------+
        +-------------------------+                          |
                                                             v
                                        +------------------------------------+
                                        | TIER 2: Durable Store (PostgreSQL) |
                                        | IdempotencyKeyEntity: COMPLETED    |
                                        | Salva Response Body, Status e Hash |
                                        +--------------------+---------------+
                                                             |
                                                             v
                                        +------------------------------------+
                                        | Liberação do Lock (Script Lua)     |
                                        | Opcional: Cache de Resposta Redis  |
                                        +------------------------------------+
```

---

## Detalhamento dos Níveis

### Tier 1: Fast-Fail In-Flight Lock Distribuído via Redis
- **Objetivo**: Bloqueio atômico de operações em trânsito e rejeição imediata (*fail-fast*) de concorrência duplicada, antes que qualquer recurso relacional seja alocado.
- **Operação Atômica**:
  ```redis
  SET lock:idempotency:{idempotency_key} {worker_instance_id} NX EX 60
  ```
  - `NX`: Define a chave somente se ela ainda **não** existir no Redis.
  - `EX 60`: Expiração de segurança automática (*Time-To-Live*) de 60 segundos, protegendo o sistema contra nós que sofrem crash ou *kill* abrupto antes de liberar o lock.
  - `{worker_instance_id}`: Identificador único da réplica/thread para evitar que um worker delete inadvertidamente o lock de outro.

- **Comportamento em Falha de Aquisição (Lock Negado)**:
  - Se o comando retornar `null`/`false`, significa que outra instância do cluster já está processando ativamente uma requisição com essa mesma chave de idempotência.
  - O filtro/interceptor HTTP rejeita **imediatamente** a requisição concorrente:
    - Retorno: HTTP `409 Conflict` (ou HTTP `425 Too Early`), acompanhado do cabeçalho `Retry-After: 2` e mensagem explicativa de requisição em processamento concorrente.
    - Custo no PostgreSQL: **Zero conexões HikariCP abertas, zero locks relacionais e zero I/O de disco**.

---

### Tier 2: Armazenamento Durável e Auditável em PostgreSQL (`IdempotencyKeyEntity`)
- **Objetivo**: Armazenar o ciclo de vida definitivo da transação, dados de resposta para repetições legítimas (*replay*) e hash criptográfico do payload para detecção de adulteração (*tampering*).
- **Esquema de Dados**:
  ```sql
  CREATE TABLE idempotency_records (
      idempotency_key VARCHAR(64) PRIMARY KEY,
      resource_id UUID NULL,
      status VARCHAR(20) NOT NULL, -- 'PROCESSING', 'COMPLETED', 'FAILED'
      request_hash VARCHAR(64) NOT NULL, -- SHA-256 do request body original
      response_status INT NULL, -- ex: 202, 201, 400
      response_body JSONB NULL, -- Cópia do payload retornado para replay
      response_headers JSONB NULL,
      created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
      expires_at TIMESTAMP WITH TIME ZONE NOT NULL -- Janela de retenção (ex: 24h a 7 dias)
  );

  CREATE INDEX idx_idempotency_expires ON idempotency_records(expires_at);
  ```

- **Fluxo de Conclusão**:
  1. A transação negocial do banco executa e comita os lançamentos.
  2. Na mesma transação (ou via hook pós-commit), o registro em `idempotency_records` é atualizado para `COMPLETED`, persistindo o `response_status` (ex: `202 Accepted`) e o payload de resposta original.
  3. **Liberação Segura do Lock no Redis**:
     O worker executa um script Lua atômico para garantir que só liberará o lock se ele ainda pertencer ao seu próprio `worker_instance_id`:
     ```lua
     if redis.call("get", KEYS[1]) == ARGV[1] then
         return redis.call("del", KEYS[1])
     else
         return 0
     end
     ```
  4. **Cache de Replay no Redis (Opcional)**:
     O payload de resposta é gravado no Redis com chave `cache:idempotency:{key}` e TTL de 24 horas (`EX 86400`). Requisições repetidas subsequentes pelo mesmo cliente são respondidas diretamente pelo Redis em < 1ms com o status e payload originais, sem necessidade de consultar o PostgreSQL.

---

## Mecanismos de Resiliência, Fallbacks e Disaster Recovery

### 1. Detecção de Inconsistência de Payload (Hash Mismatch)
O hash SHA-256 do corpo da requisição é computado na entrada:
- Se uma requisição com o mesmo `Idempotency-Key` chegar com um corpo diferente do que foi processado originalmente, o sistema aborta a operação retornando HTTP `422 Unprocessable Entity` com a mensagem `IDEMPOTENCY_KEY_PAYLOAD_MISMATCH`, prevenindo ataques ou reutilização inadvertida de chaves para operações distintas.

### 2. Auto-Release de TTL e Watchdog de Renovação
- Para a imensa maioria das requisições bancárias, a execução dura entre 10ms e 200ms. O TTL de 60s no Tier 1 é amplamente suficiente para cobrir oscilações normais.
- Se o container sofrer falha de hardware ou reinicialização durante o processamento, o lock no Redis se autodestrói após 60 segundos, permitindo que novas tentativas legítimas do cliente sejam reavaliadas no Tier 2 do PostgreSQL.

### 3. Política de Evicção do Redis (`maxmemory-policy`)
Para evitar que o Redis descarte chaves de lock ativas sob pressão de memória:
- A instância do Redis dedicada ao controle de concorrência é configurada com a política `noeviction` ou `volatile-lru` aplicada estritamente a chaves voláteis com TTL explícito.
- As chaves de locks in-flight recebem prioridade de isolamento.

### 4. Degradação Graciosa (Circuit Breaker para o Redis)
Se o cluster Redis sofrer indisponibilidade total ou timeout de rede:
- Um Circuit Breaker (Resilience4j) abre automaticamente, evitando travar as threads HTTP da aplicação.
- A aplicação chaveia temporariamente para o **Modo Fallback Relacional**:
  - Executa diretamente no PostgreSQL via `INSERT INTO idempotency_records (...) ON CONFLICT (idempotency_key) DO NOTHING`.
  - Essa degradação controlada prioriza a continuidade do negócio financeiro em detrimento da redução de latência, até que a conectividade com o Redis seja restabelecida.

---

## Consequências

### Positivas
- **Blindagem do PostgreSQL**: Sob rajadas de 50k+ req/s com requisições repetidas ou concorrentes, até 99% das chamadas duplicadas são barradas na camada Redis, preservando conexões do HikariCP e I/O de disco para transações novas legítimas.
- **Latência de Rejeição Sub-Milissegundo**: Requisições duplicadas concorrentes recebem HTTP 409 em menos de 1ms.
- **Replay Determinístico e Auditoria**: Respostas idempotentes retornam exatamente o mesmo payload e código HTTP original armazenado no PostgreSQL, atendendo aos requisitos de auditoria do Banco Central.
- **Segurança Criptográfica**: Verificação de integridade via SHA-256 do payload contra reaproveitamento fraudulento de chaves.

### Negativas / Trade-offs
- **Dependência de Componente de Infraestrutura**: Requer infraestrutura de Redis Cluster altamente disponível (ex: Sentinel ou Redis Enterprise).
- **Complexidade de Orquestração**: Necessidade de manter a consistência entre a liberação do lock em memória e o commit relacional, exigindo scripts Lua e tratamento de fallbacks com circuit breaker.
