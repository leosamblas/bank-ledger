# ADR 0004: Concorrência Multi-Instância no OutboxPoller com PostgreSQL FOR UPDATE SKIP LOCKED

## Status
Aprovado

## Contexto e Pergunta do Usuário
O padrão **Transactional Outbox** é a espinha dorsal da integração assíncrona orientada a eventos no sistema bancário, garantindo consistência eventual entre a base relacional (PostgreSQL) dos serviços (`transaction-service`, `ledger-service`) e o Apache Kafka.

Em ambientes de produção com alta disponibilidade (HA) e escalabilidade horizontal elástica (ex: Kubernetes com HPA escalando de 2 a 16 pods), múltiplas réplicas do mesmo microsserviço executam simultaneamente instâncias do componente `OutboxPoller`.

Foi levantada a seguinte questão arquitetural pelo usuário:
> *"O OutboxPoller não tem problema de concorrência entre múltiplas instâncias?"*

Os riscos potenciais analisados sob concorrência multi-instância incluem:
1. **Publicações Duplicadas no Kafka**: Duas réplicas capturarem o mesmo registro de outbox simultaneamente e publicarem eventos duplicados na partição do Kafka.
2. **Contenção e Deadlocks no PostgreSQL**: Múltiplas instâncias disputando as mesmas linhas ou índices em queries de varredura (`polling`), provocando lentidão no banco ou *deadlocks*.
3. **Exaustão de Conexões do Pool JDBC (HikariCP)**: Segurar conexões ativas no PostgreSQL enquanto se aguarda o envio de rede com confirmação de entrega do Kafka.

---

## Decisão Arquitetural

Adotamos o padrão de **Fila Cooperativa a Nível de Linha** provido nativamente pelo PostgreSQL através da cláusula `FOR UPDATE SKIP LOCKED`, combinado com um **Padrão de Processamento em 2 Fases (Claim / Lease de Curta Duração)** para blindar o pool de conexões JDBC contra latências de mensageria.

### 1. Particionamento Cooperativo com `FOR UPDATE SKIP LOCKED`
Para recuperar os registros pendentes, o `OutboxPoller` utiliza a seguinte query SQL nativa:

```sql
SELECT id, aggregate_type, aggregate_id, event_type, payload, status
FROM outbox_events
WHERE status = 'PENDING'
ORDER BY created_at ASC
LIMIT :batchSize
FOR UPDATE SKIP LOCKED;
```

#### Como o PostgreSQL resolve a concorrência sem conflito:
- **`FOR UPDATE`**: Solicita um bloqueio exclusivo de escrita (*exclusive write row lock*) nas tuplas retornadas pela consulta.
- **`SKIP LOCKED`**: Altera radicalmente o comportamento tradicional de bloqueio. Em vez de suspender a execução aguardando a liberação do lock por outra transação (como faria um `FOR UPDATE` bloqueante) ou lançar um erro imediato (como faria `NOWAIT`), o PostgreSQL **ignora silenciosamente** as linhas que já estão bloqueadas por qualquer outra transação aberta e prossegue a varredura no índice até completar a quantidade solicitada em `:batchSize`.
- **Fatias Estritamente Disjuntas (*Non-overlapping slices*)**:
  - Se a **Instância A** e a **Instância B** executarem o polling no mesmo milissegundo buscando lotes de 50 registros:
    - A **Instância A** captura e bloqueia as linhas 1 a 50.
    - A **Instância B** detecta o lock nas linhas 1 a 50, salta-as instantaneamente e bloqueia as linhas 51 a 100.
  - **Resultado**: Duas ou mais instâncias concorrentes **nunca** capturam o mesmo registro outbox. Não ocorrem publicações duplicadas provocadas por disputa de polling, não há espera em fila de locks (*zero lock-wait overhead*) e a concorrência é particionada no nível de tupla diretamente pelo kernel do PostgreSQL.

---

### 2. O Risco de Exaustão de Pool de Conexões JDBC (HikariCP)

Apesar de o `FOR UPDATE SKIP LOCKED` resolver a disputa por tuplas, surge um anti-padrão grave se o poller adotar uma transação JDBC longa:

> **Anti-Padrão (Transação Longa com I/O de Mensageria)**:
> 1. Abre transação JDBC (`@Transactional`).
> 2. Executa `SELECT ... FOR UPDATE SKIP LOCKED`.
> 3. Para cada evento, chama `kafkaTemplate.send(topic, payload).get(5, SECONDS)`.
> 4. Executa `UPDATE outbox_events SET status = 'SENT' WHERE id IN (...)`.
> 5. Commit e fechamento da transação JDBC.

#### Por que isso é perigoso?
Se o cluster Kafka enfrentar latência transitória de rede, eleição de leader de partição ou atraso de replicação (ex: latência de ack saltando de 2ms para 2.000ms), **todas as conexões do HikariCP alocadas para o OutboxPoller ficam travadas esperando o Kafka**. Em instantes:
- O pool de conexões do HikariCP se esgota por completo.
- As requisições HTTP REST dos clientes (criação de contas, depósitos, transferências) falham por *connection acquisition timeout* (`Connection is not available, request timed out after 30000ms`).
- Ocorre **falha em cascata**: uma lentidão no Kafka paralisa o banco relacional e as APIs síncronas do microsserviço.

---

### 3. Padrão Recomendado de 2 Fases (Claim / Lease de Curta Duração)

Para desacoplar a retenção de conexões JDBC do tempo de envio no Kafka, dividimos o ciclo do `OutboxPoller` em 2 fases desacopladas:

```
[ PostgreSQL ]                                  [ Outbox Worker ]                          [ Kafka Broker ]
      |                                                |                                          |
      | --- 1. Claim (Transação Curta < 5ms) --------> |                                          |
      |     SELECT FOR UPDATE SKIP LOCKED              |                                          |
      |     UPDATE status = 'PROCESSING', lease=NOW+30s|                                          |
      | <--- COMMIT & Fecha Conexão JDBC ------------ |                                          |
      |                                                |                                          |
      |       [Conexão JDBC devolvida ao HikariCP]     |                                          |
      |                                                | --- 2. Publish (I/O de Rede Assíncrono)->|
      |                                                | <--- RecordMetadata ACK ---------------- |
      |                                                |                                          |
      | --- 3. Finalize (Transação Curta < 2ms) -----> |                                          |
      |     UPDATE status = 'SENT', processed_at=NOW   |                                          |
      | <--- COMMIT & Libera Conexão ----------------- |                                          |
```

#### Fase 1: Claim Atômico em Transação JDBC Curta (< 5ms)
Uma transação ultra-rápida adquire os registros e transiciona imediatamente o status de `PENDING` para `PROCESSING`, gravando a identidade da instância trabalhadora e um *lease timeout*:

```sql
WITH claimed_events AS (
    SELECT id
    FROM outbox_events
    WHERE status = 'PENDING'
       OR (status = 'PROCESSING' AND lease_expires_at < NOW()) -- Resgate de leases expirados
    ORDER BY created_at ASC
    LIMIT :batchSize
    FOR UPDATE SKIP LOCKED
)
UPDATE outbox_events o
SET status = 'PROCESSING',
    locked_by = :instanceIdentifier,
    locked_at = NOW(),
    lease_expires_at = NOW() + INTERVAL '30 seconds'
FROM claimed_events c
WHERE o.id = c.id
RETURNING o.*;
```
A transação é commitada imediatamente e a conexão com o banco é devolvida ao HikariCP. O lock físico do PostgreSQL é liberado; a linha permanece protegida logicamente pelo `status = 'PROCESSING'`.

#### Fase 2: Publicação Assíncrona no Kafka (Sem Conexão JDBC Presa)
O worker transmite os eventos capturados via Kafka Producer utilizando execução assíncrona ou Virtual Threads (`CompletableFuture` / non-blocking callbacks). A latência de rede do Kafka não afeta nenhuma conexão de banco de dados.

#### Fase 3: Confirmação e Finalização do Status
Após o recebimento do ACK do Kafka (`RecordMetadata` com offset garantido):
- O worker abre uma nova transação JDBC relâmpago:
  ```sql
  UPDATE outbox_events
  SET status = 'SENT',
      processed_at = NOW(),
      error_message = NULL
  WHERE id IN (:deliveredIds);
  ```
- Opcionalmente, registros em `SENT` podem ser deletados ou arquivados por um job de retenção/expurgo (ex: registros com mais de 7 dias).

#### Resgate de Falhas (Heartbeat / Sweeper)
Se uma réplica sofrer um encerramento forçado (*crash*, OOM, SIGKILL) durante a Fase 2:
- O registro permanecerá como `PROCESSING`, mas seu `lease_expires_at` (30 segundos) expirará.
- No próximo ciclo de polling de qualquer réplica saudável, a condição `(status = 'PROCESSING' AND lease_expires_at < NOW())` incluirá novamente o registro no lote de claim, garantindo que nenhum evento seja perdido.
- Como o Kafka adota semântica *at-least-once*, o downstream (`ledger-service`) é idempotente por definição, absorvendo eventuais re-entregas decorrentes de crashes.

---

## Consequências

### Positivas
- **Escala Linear Sem Coordenação Externa**: O cluster pode adicionar dezenas de réplicas sem necessidade de Redis Locks ou Zookeeper para coordenar pollers do outbox.
- **Proteção do HikariCP**: O tempo de retenção da conexão JDBC é de poucos milissegundos por lote, blindando as APIs REST de clientes contra flutuações e gargalos de rede do Kafka.
- **Imunidade a Deadlocks e Lock Contention**: `SKIP LOCKED` elimina bloqueios cooperativos e contenção de índice.
- **Garantia de Não-Duplicação no Polling**: Cada réplica recebe estritamente um subconjunto disjunto de tuplas.

### Negativas / Trade-offs
- **Dependência de Recursos do Banco**: Requer PostgreSQL 9.5+ (suporte nativo completo a `SKIP LOCKED`).
- **Mecanismo de Sweeper Obrigatório**: Torna mandatório o uso de `lease_expires_at` para recuperar mensagens presas em `PROCESSING` após crashes não graciosos do container.
- **Semântica At-Least-Once**: Na remota ocorrência de falha da instância após a entrega no broker mas antes da Fase 3, o sweeper reenviará a mensagem; todos os consumidores downstream devem manter chave de idempotência (`event_id` / `transaction_id`).
