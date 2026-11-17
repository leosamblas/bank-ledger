# Relatório de Pesquisa Técnica: Análise de Backend para Novas Regras de Negócio e Perfil

**Destino:** `docs/research/2026-11-17-business-rules-backend-analysis.md`  
**Autor:** Backend Research Specialist (Research Subagent)  
**Data:** 17 de Novembro de 2026  
**Status:** Concluído com Citações Primárias e Evidências Concretas  

---

## Sumário Executivo

Esta pesquisa examinou minuciosamente todos os microserviços de backend (`account-service`, `transaction-service`, `query-service`, `ledger-service`, `auth-service`, `platform-commons`) e contratos OpenAPI (`api-contracts/*.yaml`) do repositório `bank-ledger`.

O objetivo foi avaliar a viabilidade e mapear as lacunas técnicas para os quatro novos requisitos de negócio:
1. **Limite Diário de Transferências (Validação e Governança)**
2. **Extrato Bancário Humanizado, Contraparte (Origem/Favorecido) e Protocolo Canônico**
3. **Transferência por CPF e Resolução/Lookup de Destinatário**
4. **Edição de Perfil de Usuário e Dados de Endereço Residencial**

Abaixo estão as descobertas detalhadas com arquivos, linhas de código, modelos e recomendações arquiteturais.

---

## 1. Limite Diário de Transferências (Daily Transfer Limit)

### 1.1. Estado Atual no Código-Fonte

* **`account-service`**:
  * Na tabela `account` (`services/account-service/src/main/resources/db/migration/V1__init_account_schema.sql:26-41`), existem apenas as colunas: `id`, `customer_id`, `branch`, `account_number`, `check_digit`, `status`, `version`, `created_at`, `updated_at`, `closed_at`.
  * **Não existe** coluna nem conceito de limite (`daily_limit`, `transfer_limit`) na entidade `AccountEntity` (`AccountEntity.java:36-70`) ou no modelo de domínio `Account` (`Account.java:20-50`).

* **`transaction-service`**:
  * Em `TransferRequest.java:15` (`services/transaction-service/src/main/java/com/bankledger/transaction/adapters/in/rest/dto/TransferRequest.java`):
    ```java
    @NotNull(message = "amount must not be null")
    @Positive(message = "amount must be greater than zero")
    @Max(value = 5_000_000L, message = "amount must not exceed 5000000 cents (R$ 50.000,00)")
    Long amount
    ```
    Existe apenas validação Bean Validation de limite *por transação individual* (teto de R$ 50.000,00).
  * Em `TransactionController.java:188-256` e `CreateTransferService.java:43-98`:
    * É checada a idempotência (`idempotencyValidatorUseCase.validateOrLock`) e a igualdade entre contas (`sourceAccountId != destinationAccountId`).
    * **Não é feita nenhuma consulta ou validação de saldo nem de limite diário acumulado**.
    * A transação é gravada na tabela `financial_transaction` com status `PENDING` e um evento `PostJournalCommand` é enviado para o outbox (`ledger.commands.v1`).

* **`ledger-service`**:
  * `TransferPostingStrategy.java:49-89` apenas valida saldo suficiente na conta de origem (`sourceAccount.applyEntry(EntryDirection.DEBIT, command.amount())`). Se o saldo for insuficiente, lança `InsufficientFundsException` e rejeita a transação contabilmente (`REJECTED`, `INSUFFICIENT_FUNDS`).
  * Não há verificação de limite diário no ledger.

* **`query-service`**:
  * Em `BalanceResponse.java:32-33` (`services/query-service/src/main/java/com/bankledger/query/adapters/in/rest/dto/BalanceResponse.java`):
    ```java
    return new BalanceResponse(
        accountId, balance, balance, 0L,
        5000000L, // dailyLimitCents fixo (R$ 50.000,00)
        0L,       // usedTodayCents fixo
        0.0, currency != null ? currency : "BRL", sequence, updatedAt
    );
    ```
    O valor de `dailyLimitCents` é **hardcoded** em 50.000,00 reais e `usedTodayCents` é **hardcoded em 0L**.
  * A OpenAPI `api-contracts/query-service.yaml:14-29` sequer documenta os campos `dailyLimitCents` e `usedTodayCents` (embora o DTO Java os contenha).

### 1.2. Como Modelar os Limites Diários

1. **Configuração de Limite (Por Conta vs Global):**
   * **Nível de Banco/Global:** Definir valor padrão via Spring Properties em `application.yml` (ex: `bankledger.limits.default-daily-transfer-cents: 5000000` = R$ 50.000,00).
   * **Nível de Conta (Customizável):** Adicionar coluna `daily_transfer_limit_cents BIGINT NOT NULL DEFAULT 5000000` na tabela `account` (migration `V3__add_account_limits.sql` no `account-service`).
2. **Cálculo do Limite Utilizado no Dia (`usedTodayCents`):**
   * **Em `transaction-service` (Validação Síncrona pré-aceite):**
     * Para garantir integridade sem condições de corrida, antes de aceitar a transferência (`createTransfer`), o `transaction-service` calcula a soma de transferências do dia corrente:
       ```sql
       SELECT COALESCE(SUM(amount), 0) 
       FROM financial_transaction 
       WHERE source_account_id = :sourceAccountId 
         AND type = 'TRANSFER' 
         AND status IN ('PENDING', 'COMPLETED') 
         AND created_at >= :startOfDayBrt;
       ```
     * Incluir status `PENDING` é indispensável para evitar que múltiplas transferências concorrentes quase simultâneas ultrapassem o teto diário antes da liquidação contábil.
     * O fuso horário de corte deve ser explicitamente `America/Sao_Paulo` (UTC-3), iniciando à 00:00:00 do dia civil corrente.
     * Se `(usedTodayCents + request.amount) > dailyLimitCents`, rejeitar imediatamente com HTTP 422 Unprocessable Entity (ProblemDetail: `DAILY_LIMIT_EXCEEDED`, "Limite diário de transferências excedido").
   * **Em `query-service` (Projeção CQRS para Tela/Dashboard):**
     * No endpoint `GET /api/v1/queries/accounts/{accountId}/balance`, calcular `usedTodayCents` consultando `statement_entry`:
       ```sql
       SELECT COALESCE(SUM(amount), 0)
       FROM statement_entry
       WHERE account_id = :accountId
         AND direction = 'DEBIT'
         AND category = 'TRANSFER'
         AND posted_at >= :startOfDayBrt;
       ```
     * Retornar no `BalanceResponse`: `dailyLimitCents` (5.000.000 ou parametrizado), `usedTodayCents` (calculado) e `availableLimitCents` (`dailyLimitCents - usedTodayCents`).

---

## 2. Extrato (Statement), Contraparte (Origem/Favorecido) e Protocolo

### 2.1. Estado Atual no Código-Fonte

* **`transaction-service`**:
  * Tabela `financial_transaction` (`V1__init_transaction_schema.sql:32-49`):
    * Campos existentes: `id`, `type`, `status`, `source_account_id`, `destination_account_id`, `amount`, `currency`, `description`, `requested_by`, `failure_code`, `failure_reason`, `correlation_id`, `created_at`, `updated_at`, `completed_at`, `version`.
    * **Campos ausentes:** NÃO existe campo `protocol` nem dados cadastrais da contraparte (nome do favorecido/pagador, CPF da contraparte, agência/conta da contraparte).
    * O campo `description` fornecido pelo cliente na API (`TransferRequest.description`) é salvo em `financial_transaction`, mas **não é repassado** no comando `PostJournalCommand` enviado ao ledger (`CreateTransferService.java:62-73`).
* **`ledger-service`**:
  * Em `TransferPostingStrategy.java:28`:
    * A descrição no journal é fixa: `public static final String DESCRIPTION = "Transferência contábil";`.
  * Em `EntryPostedEvent.java:17-40`:
    * O evento emitido para o Kafka (`ledger.entries.v1`) contém apenas:
      `entryId`, `journalId`, `transactionId`, `accountId`, `direction`, `amount`, `balanceAfter`, `sequence`, `postedAt`.
    * O evento contábil é agnóstico em relação à contraparte, tipos de negócio e protocolos comerciais.
* **`query-service` (Lacuna Crítica Descoberta)**:
  * A tabela `statement_entry` (`V1__init_query_schema.sql:42-65`) **já possui** as colunas:
    `counterparty_name`, `counterparty_document_masked`, `counterparty_account`, `idempotency_key`, `category`, `type`, `description`.
  * Porém, em `QueryEventConsumer.java:104-311`:
    * O consumidor escuta apenas o tópico contábil `ledger.entries.v1`.
    * Como o `EntryPostedEvent` do ledger não traz esses dados, o `QueryEventConsumer` insere **`NULL`** em `counterparty_name`, `counterparty_document_masked`, `counterparty_account`!
    * Além disso, a categoria e descrição sofrem fallback cego:
      ```java
      // QueryEventConsumer.java:266-281
      if ("CREDIT".equalsIgnoreCase(direction)) {
          category = "DEPOSIT";
          description = "Depósito em conta";
      } else {
          category = "WITHDRAWAL";
          description = "Saque em conta";
      }
      ```
    * Consequência: Toda transferência enviada vira "WITHDRAWAL / Saque em conta" e toda transferência recebida vira "DEPOSIT / Depósito em conta", com contraparte nula!
* **Protocolo de Auditoria**:
  * **Não existe** campo `protocol` em nenhum serviço (`financial_transaction`, `journal`, `entry` ou `statement_entry`).
  * O frontend atualmente exibe o `#sequence` da conta (ex: `#1`, `#2`) como "Nº do Comprovante" e exibe o `idempotencyKey` UUID como "Protocolo de Autenticação".

### 2.2. Como Gerar e Propagar Protocolo e Contraparte

1. **Geração do Protocolo Canônico:**
   * Formato padrão bancário brasileiro: `PRT-YYYYMMDD-XXXXXX`
     Exemplo: `PRT-20261117-9F8A2D` (onde `XXXXXX` são 6 dígitos hexadecimais em maiúsculas gerados deterministicamente dos primeiros 6 caracteres do UUID da transação ou gerados aleatoriamente).
   * O protocolo deve ser gerado no momento da criação da transação em `CreateTransferService` e salvo na coluna `protocol` de `financial_transaction`.
2. **Propagação de Dados da Contraparte para o Extrato:**
   * Quando `transaction-service` processa o resultado contábil (`HandleJournalResultService:51`), ou no momento do envio do `PostJournalCommand`, o `transaction-service` conhece `sourceAccountId`, `destinationAccountId`, `description` e `protocol`.
   * `query-service` preenche `statement_entry` com:
     * Para o pagador (`sourceAccountId`): `type = "TRANSFER_OUT"`, `description = "Transferência enviada"`, contraparte = dados do favorecido.
     * Para o favorecido (`destinationAccountId`): `type = "TRANSFER_IN"`, `description = "Transferência recebida"`, contraparte = dados do pagador.
     * `protocolNumber` = `PRT-YYYYMMDD-XXXXXX`.

---

## 3. Transferência por CPF e Recipient Lookup

### 3.1. Estado Atual no Código-Fonte

* **`account-service`**:
  * Em `AccountController.java:76-88` e `api-contracts/account-service.yaml:160-184`:
    * Existe apenas: `GET /api/v1/accounts/lookup?agency={agency}&number={number}`.
    * **Não existe endpoint para buscar conta por CPF**.
  * No repositório Spring Data `CustomerSpringDataRepository.java:14`:
    * Já existe o método pronto: `Optional<CustomerEntity> findByCpf(String cpf);`.
  * No repositório `AccountSpringDataRepository.java:17`:
    * Já existe o método pronto: `Optional<AccountEntity> findByCustomerId(UUID customerId);`.
  * Ou seja, a camada de banco já possui 100% das consultas necessárias para localizar a conta a partir de um CPF.
  * O retorno atual de `AccountLookupResponse.java` é:
    ```java
    public record AccountLookupResponse(
        UUID accountId,
        String agency,
        String number,
        String holderNameMasked
    )
    ```
    Onde `holderNameMasked` mascara todas as palavras com asteriscos exceto a primeira letra (`Carlos Eduardo Silva` -> `C*** E*** S***`).
* **`transaction-service`**:
  * `TransferRequest.java:9-20` exige obrigatoriamente: `@NotNull UUID destinationAccountId`.
  * `transaction-service` não possui clientes HTTP configurados para chamar o `account-service` de forma síncrona.

### 3.2. Análise do Fluxo de Transferência por CPF

* **Padrão de Mercado e Arquitetura Ideal (Padrão PIX em 2 Etapas):**
  1. **Etapa 1 - Consulta de Destinatário (Lookup):**
     * O frontend chama o endpoint de lookup do `account-service`:
       `GET /api/v1/accounts/lookup?cpf=52998224725` (ou `?agency=0001&number=100000`).
     * O `account-service` resolve `Customer` por CPF -> resolve `Account` por `customerId` -> retorna dados para confirmação:
       ```json
       {
         "accountId": "a3b4c5d6-...",
         "agency": "0001",
         "number": "100000",
         "digit": "4",
         "holderName": "Carlos Eduardo Silva",
         "cpf": "529.982.247-25",
         "bankName": "Nexus Bank S.A.",
         "bankCode": "001"
       }
       ```
  2. **Etapa 2 - Confirmação e Envio:**
     * O usuário visualiza o nome e dados do favorecido na tela de conferência.
     * Ao clicar em "Confirmar Transferência", o frontend chama `POST /api/v1/transactions/transfers` passando o `destinationAccountId` já resolvido.
  * **Vantagem Arquitetural:** Não requer chamadas síncronas entre microserviços (`transaction-service` continua desacoplado de `account-service`), mantém o fluxo de conferência de dados idêntico ao PIX/Bacen e evita expor CPFs desnecessariamente no tráfego interno de transações.

---

## 4. Edição de Perfil de Usuário e Dados de Endereço

### 4.1. Estado Atual no Código-Fonte

* **Entidades e Tabelas de `Customer` e `Account`**:
  * Em `V1__init_account_schema.sql:8-23` e `CustomerEntity.java:36-79`:
    * Campos existentes: `id`, `full_name`, `cpf`, `email`, `phone`, `birth_date`, `password_hash`, `failed_login_attempts`, `locked_until`, `version`, `created_at`, `updated_at`.
    * **Não existe nenhuma coluna de endereço** (`street`, `number`, `complement`, `neighborhood`, `city`, `state`, `postal_code`).
  * No modelo de domínio `Customer.java:139-147`:
    * Já existem métodos mutadores para contato: `updateEmail(Email email, Instant now)` e `updatePhone(Phone phone, Instant now)`.
* **Endpoints de Perfil**:
  * `AccountController.java` possui apenas:
    * `POST /api/v1/accounts`: Criação de conta.
    * `GET /api/v1/accounts/me`: Consulta da conta do usuário autenticado. Retorna `AccountResponse` (`id`, `customerId`, `agency`, `number`, `digit`, `status`, `openedAt`, `holderName`). **Não retorna** CPF, e-mail, telefone nem data de nascimento para o frontend.
    * `GET /api/v1/accounts/{accountId}`: Detalhes por ID com validação de ownership.
  * **Não existe nenhum endpoint de atualização** (`PUT /accounts/{id}`, `PATCH /accounts/{id}`, `PUT /accounts/me/profile`).

### 4.2. Como Modelar a Edição de Perfil e Endereço

1. **Migração de Banco de Dados (`V3__add_customer_address.sql` no `account-service`):**
   ```sql
   ALTER TABLE customer
       ADD COLUMN IF NOT EXISTS postal_code VARCHAR(9),
       ADD COLUMN IF NOT EXISTS street VARCHAR(150),
       ADD COLUMN IF NOT EXISTS number VARCHAR(20),
       ADD COLUMN IF NOT EXISTS complement VARCHAR(100),
       ADD COLUMN IF NOT EXISTS neighborhood VARCHAR(100),
       ADD COLUMN IF NOT EXISTS city VARCHAR(100),
       ADD COLUMN IF NOT EXISTS state CHAR(2);
   ```
2. **Mapeamento JPA e Modelo de Domínio:**
   * Criar Value Object ou Embeddable `Address` (`postalCode`, `street`, `number`, `complement`, `neighborhood`, `city`, `state`).
   * Adicionar `Address address` em `Customer` e `CustomerEntity`.
3. **Novo Endpoint de Perfil:**
   * `GET /api/v1/accounts/me`:
     * Enriquecer a resposta para retornar os dados cadastrais completos: `holderName`, `cpf`, `email`, `phone`, `birthDate`, `address: { ... }`.
   * `PUT /api/v1/accounts/me/profile`:
     * Payload: `phone`, `email`, `address` (`postalCode`, `street`, `number`, `complement`, `neighborhood`, `city`, `state`).
     * **Regra de Imutabilidade Bancária:** O backend deve rejeitar qualquer tentativa de alteração de `cpf`, `birthDate` ou `fullName` nesta rota (campos imutáveis por autoatendimento).
     * Se o `email` for alterado, emitir evento no outbox para que o `auth-service` atualize a tabela `auth_credentials`.

---

## 5. Resumo das Tarefas de Implementação Recomendadas

| Módulo | Ações Necessárias no Backend |
| :--- | :--- |
| **Limite Diário** | 1. Criar migration `V3` em `account-service` com `daily_transfer_limit_cents`.<br>2. Adicionar validação de limite diário cumulativo em `CreateTransferService` (`transaction-service`).<br>3. Projetar dinamicamente `usedTodayCents` e `availableLimitCents` no `QueryController` (`query-service`). |
| **Extrato e Contraparte** | 1. Adicionar `protocol` (`PRT-YYYYMMDD-XXXXXX`) na tabela `financial_transaction` e gerá-lo em `CreateTransferService`.<br>2. Propagar dados de contraparte e protocolo para o `query-service` preencher as colunas já existentes de `statement_entry`. |
| **Transferência por CPF** | 1. Expandir `LookupAccountUseCase` e `AccountController.lookupAccount` para aceitar `?cpf={cpf}` além de `?agency=...&number=...`.<br>2. Retornar dados enriquecidos do favorecido para exibição e confirmação no frontend. |
| **Perfil e Endereço** | 1. Criar migration `V3__add_customer_address.sql` no `account-service`.<br>2. Atualizar `CustomerEntity` e criar `Address`.<br>3. Implementar `PUT /api/v1/accounts/me/profile` e expandir `GET /api/v1/accounts/me`. |
