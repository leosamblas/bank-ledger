# Plano de Adequações e Melhores Práticas Java Moderno (Pílulas de Java)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Aplicar as melhores práticas de engenharia de software em Java moderno (baseado nas "Pílulas de Java" e Java 25 LTS) nos microsserviços `account-service`, `ledger-service`, `transaction-service` e `platform-commons`, corrigindo armadilhas de inteiros negativos em hashing, implementando máquina de estados canônica com `EnumMap`, blindando entidades JPA contra N+1 em `toString`, eliminando boxing desnecessário em pipelines de domínio e otimizando a pegada de memória na JVM com Compact Object Headers.

**Architecture:** Refinamento de código idiomático com foco em segurança de tipos, imutabilidade, prevenção de bugs de concorrência e conformidade com os padrões de microserviços de missão crítica em Java 25 e Spring Boot 4.

**Tech Stack:** Java 25 LTS, Spring Boot 4.1.2, Spring Data JPA, Jackson 3, Dockerfile, JUnit 5, AssertJ, ArchUnit.

**Spec:** Guia de Boas Práticas `java-best-practices/SKILL.md` e referências `java-best-practices/references/detailed-rules.md`.

## Global Constraints

- Compatibilidade total com Java 25 LTS (utilização de recursos nativos sem bibliotecas externas desnecessárias).
- Manter 100% dos testes unitários e de integração verdes (`./gradlew test`).
- Nenhuma quebra de contrato de API REST ou de tópicos Kafka.
- Conformidade estrita com as regras do ArchUnit em todos os módulos.

## Review Focus

1. **Bug do `Math.abs(Integer.MIN_VALUE)`:** Garantir que o cálculo de shards em `CashShardResolver` nunca retorne índice negativo sob colisões de hash.
2. **Máquina de Estados de Transação:** Validar que transações em estado terminal (`COMPLETED` ou `FAILED`) nunca sofram sobrescrita de status em cenários de reentrega de mensagens.
3. **Segurança de Entidades JPA:** Certificar que nenhuma entidade utilize `@Data` e que `@ToString` / `@EqualsAndHashCode` estejam restritos ao `@Id` ou campos escalares simples.
4. **Zero Boxing em Cálculos Monetários:** Assegurar que agregações de partidas dobradas usem `mapToLong` e primitivos `long`.
5. **JVM Flags em Contêiner:** Confirmar a sintaxe correta de `-XX:+UseCompactObjectHeaders` e flags de OOM nos Dockerfiles.

---

### FASE 1: Correção de Gotchas Numéricos e Máquina de Estados

#### Task 1: Correção do Hash Code Negativo com `Math.floorMod` no `CashShardResolver`

**Files:**
- Modify: `services/ledger-service/src/main/java/com/bankledger/ledger/application/service/CashShardResolver.java:50,64`
- Test: `services/ledger-service/src/test/java/com/bankledger/ledger/application/service/CashShardResolverTest.java`

**Interfaces:**
- Consumes: `terminalId` (String), `transactionId` (UUID)
- Produces: Índice de shard sempre no intervalo `[0, 15]`.

- [ ] **Step 1: Escrever teste unitário reproduzindo o caso de borda `Integer.MIN_VALUE`**
  - Adicionar teste em `CashShardResolverTest.java`:
    ```java
    @Test
    @DisplayName("Should never produce negative shard index even when hashCode is Integer.MIN_VALUE")
    void shouldNeverProduceNegativeShardIndex() {
        // String ou objeto com hashCode resultando em Integer.MIN_VALUE
        UUID mockTxId = new UUID(0L, 0L); // testar valores variados
        // Validar que resolveShardId retorna shard válido sem ArrayIndexOutOfBounds ou negativo
    }
    ```
- [ ] **Step 2: Substituir `Math.abs(h) % N` por `Math.floorMod(h, N)`**
  - No Java, `Math.abs(Integer.MIN_VALUE)` retorna `-2147483648` (negativo), pois em complemento de dois o menor inteiro de 32 bits não tem correspondente positivo.
  - Substituir:
    ```java
    // ❌ ANTES (vulnerável a retorno negativo se hashCode == Integer.MIN_VALUE):
    int shardIndex = Math.abs(terminalId.hashCode()) % CashShardPort.TOTAL_SHARDS;
    
    // ✅ DEPOIS (Pílulas de Java #14 & JDK Math Best Practice):
    int shardIndex = Math.floorMod(terminalId.hashCode(), CashShardPort.TOTAL_SHARDS);
    ```
  - Aplicar o mesmo ajuste para o fallback de `transactionId.hashCode()`.
- [ ] **Step 3: Executar testes**
  - Rodar `./gradlew :services:ledger-service:test --tests "com.bankledger.ledger.application.service.CashShardResolverTest"`.

---

#### Task 2: Máquina de Estados Canônica com `EnumMap` no `FinancialTransaction` (#85)

**Files:**
- Modify: `services/transaction-service/src/main/java/com/bankledger/transaction/domain/model/FinancialTransaction.java:160-179`
- Modify: `services/transaction-service/src/main/java/com/bankledger/transaction/domain/model/TransactionStatus.java`
- Test: `services/transaction-service/src/test/java/com/bankledger/transaction/domain/FinancialTransactionTest.java`

**Interfaces:**
- Consumes: Eventos de liquidação contábil (`complete`, `fail`)
- Produces: Transições de estado atômicas e validadas; impede corrupção de estado por mensagens fora de ordem.

- [ ] **Step 1: Definir mapa imutável de transições válidas**
  - Em `TransactionStatus.java` ou `FinancialTransaction.java`, declarar a tabela de transições via `EnumMap<TransactionStatus, EnumSet<TransactionStatus>>` (Pílulas de Java #85):
    ```java
    private static final EnumMap<TransactionStatus, EnumSet<TransactionStatus>> VALID_TRANSITIONS =
            new EnumMap<>(TransactionStatus.class);

    static {
        VALID_TRANSITIONS.put(TransactionStatus.PENDING, EnumSet.of(TransactionStatus.COMPLETED, TransactionStatus.FAILED));
        VALID_TRANSITIONS.put(TransactionStatus.COMPLETED, EnumSet.noneOf(TransactionStatus.class));
        VALID_TRANSITIONS.put(TransactionStatus.FAILED, EnumSet.noneOf(TransactionStatus.class));
    }
    ```
- [ ] **Step 2: Implementar método `transitionTo` com validação de invariante**
  - Em `FinancialTransaction.java`:
    ```java
    private void transitionTo(TransactionStatus target) {
        EnumSet<TransactionStatus> allowed = VALID_TRANSITIONS.getOrDefault(this.status, EnumSet.noneOf(TransactionStatus.class));
        if (!allowed.contains(target)) {
            throw new IllegalStateException("Illegal transaction transition from " + this.status + " to " + target);
        }
        this.status = target;
    }
    ```
  - Adaptar `complete(...)` e `fail(...)` para invocar `transitionTo(...)`.
- [ ] **Step 3: Escrever testes unitários para transições inválidas**
  - Validar que transitar de `COMPLETED` para `FAILED` ou de `FAILED` para `COMPLETED` lança `IllegalStateException`.
- [ ] **Step 4: Executar testes de unidade e integração**
  - Rodar `./gradlew :services:transaction-service:test`.

---

### FASE 2: Higiene JPA, Programação Funcional e Imutabilidade

#### Task 3: Higiene de JPA e Prevenção de Armadilhas em `@ToString` e Chaves (#74, #41)

**Files:**
- Modify: `services/account-service/src/main/java/com/bankledger/account/adapters/out/persistence/entity/CustomerJpaEntity.java`
- Modify: `services/account-service/src/main/java/com/bankledger/account/adapters/out/persistence/entity/AccountJpaEntity.java`
- Modify: `services/ledger-service/src/main/java/com/bankledger/ledger/adapters/out/persistence/entity/LedgerAccountJpaEntity.java`
- Modify: `services/ledger-service/src/main/java/com/bankledger/ledger/adapters/out/persistence/entity/JournalJpaEntity.java`
- Modify: `services/ledger-service/src/main/java/com/bankledger/ledger/adapters/out/persistence/entity/EntryJpaEntity.java`
- Modify: `services/transaction-service/src/main/java/com/bankledger/transaction/adapters/out/persistence/entity/FinancialTransactionJpaEntity.java`
- Modify: `services/transaction-service/src/main/java/com/bankledger/transaction/adapters/out/persistence/entity/IdempotencyKeyJpaEntity.java`

**Interfaces:**
- Consumes: Entidades JPA mapeadas
- Produces: Entidades seguras contra N+1, sem dependências de coleções em `toString` ou `equalsAndHashCode`.

- [ ] **Step 1: Auditar e restringir `@ToString` e `@EqualsAndHashCode`**
  - Garantir que todas as entidades utilizem `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` com `@EqualsAndHashCode.Include` exclusivamente sobre o `@Id`.
  - No `@ToString`, incluir apenas campos escalares essenciais (ID, códigos, enums de status), excluindo explicitamente campos com texto longo (`description`, payloads JSON) ou potenciais relacionamentos futuros.
- [ ] **Step 2: Proibir o uso de `@Data` em entidades JPA**
  - Validar que nenhuma entidade utilize `@Data` do Lombok (anti-padrão JPA que gera `equals`, `hashCode` e `toString` abrangendo todos os campos mutáveis).
- [ ] **Step 3: Executar testes de persistência**
  - Rodar testes em todos os serviços.

---

#### Task 4: Programação Declarativa com Streams e Primitivos sem Boxing (#1, #8, #14)

**Files:**
- Modify: `services/ledger-service/src/main/java/com/bankledger/ledger/domain/model/Journal.java:87-95`
- Test: `services/ledger-service/src/test/java/com/bankledger/ledger/domain/model/JournalTest.java`

**Interfaces:**
- Consumes: `List<Entry>`
- Produces: Total de débitos e créditos calculado declarativamente via `LongStream` sem overhead de boxing de objetos `Long`.

- [ ] **Step 1: Refatorar validação de equilíbrio de partidas dobradas**
  - Substituir o acumulador manual por pipeline funcional com `mapToLong` (Pílulas de Java #14 - evitando boxing de `Long`):
    ```java
    long debitSum = entries.stream()
            .filter(e -> e.direction() == EntryDirection.DEBIT)
            .mapToLong(Entry::amount)
            .sum();

    long creditSum = entries.stream()
            .filter(e -> e.direction() == EntryDirection.CREDIT)
            .mapToLong(Entry::amount)
            .sum();
    ```
- [ ] **Step 2: Executar testes do domínio Ledger**
  - Confirmar aprovação de todos os testes de `JournalTest`.

---

### FASE 3: Otimizações de JVM e Contêineres

#### Task 5: Ativação de JVM Compact Object Headers no Java 25 (JEP 534 - #20)

**Files:**
- Modify: `services/account-service/Dockerfile`
- Modify: `services/ledger-service/Dockerfile`
- Modify: `services/transaction-service/Dockerfile`
- Modify: `services/query-service/Dockerfile`

**Interfaces:**
- Consumes: JDK 25 Runtime Image (`eclipse-temurin:25-jre-alpine`)
- Produces: Até 22% de redução no consumo de heap e encerramento seguro do contêiner em caso de falta de memória.

- [ ] **Step 1: Atualizar flags da JVM nos Dockerfiles**
  - Em cada `Dockerfile`, atualizar o `ENTRYPOINT` para ativar os cabeçalhos compactos de objetos do Java 25 (JEP 534) e política segura de OOM:
    ```dockerfile
    ENTRYPOINT ["java", \
                "--enable-preview", \
                "-XX:+UseZGC", \
                "-XX:+UseCompactObjectHeaders", \
                "-XX:+ExitOnOutOfMemoryError", \
                "-jar", "app.jar"]
    ```
  - *Benefício:* Reduz o cabeçalho de cada objeto de 12 para 8 bytes em sistemas de 64 bits com ponteiros compactados, otimizando cache L1/L2/L3 e reduzindo pressão sobre o Garbage Collector ZGC.
- [ ] **Step 2: Validar compilação dos Dockerfiles**
  - Testar build dos contêineres via `docker compose config`.

---

## Critérios de Aceite Globais

1. **Bug do Hashcode Corrigido:** `CashShardResolver` usa `Math.floorMod` e garante 100% de estabilidade mesmo sob `Integer.MIN_VALUE`.
2. **Máquina de Estados Confiável:** `FinancialTransaction` utiliza `EnumMap` impedindo transições ilegais ou sobrescrita de estados finais.
3. **Entidades JPA Higienizadas:** Nenhuma entidade usa `@Data`, e `@ToString`/`@EqualsAndHashCode` estão estritamente controlados.
4. **Zero Boxing em Partidas Dobradas:** Streams usam `mapToLong` com soma de primitivos.
5. **JVM Otimizada:** Dockerfiles configurados com `-XX:+UseCompactObjectHeaders` no Java 25 LTS.
6. **Suíte 100% Verde:** Todos os testes unitários, testes de integração e verificações ArchUnit passando sem falhas.
