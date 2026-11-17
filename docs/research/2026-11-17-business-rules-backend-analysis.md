# Relatório de Pesquisa Técnica: Análise de Backend para Novas Regras de Negócio e Perfil

**Destino:** `docs/research/2026-11-17-business-rules-backend-analysis.md`  
**Autor:** Backend Research Specialist (Research Subagent)  
**Data:** 17 de Novembro de 2026 (Atualizado com Revisão de Segurança e Limites Dinâmicos)  
**Status:** Concluído com Citações Primárias e Evidências Concretas  

---

## Sumário Executivo

Esta pesquisa examinou minuciosamente todos os microserviços de backend (`account-service`, `transaction-service`, `query-service`, `ledger-service`, `auth-service`, `platform-commons`) e contratos OpenAPI (`api-contracts/*.yaml`) do repositório `bank-ledger`.

O objetivo foi avaliar a viabilidade e mapear as lacunas técnicas para os novos requisitos de negócio, incorporando as duas diretrizes mandatórias:
1. **Validação de Limite no Banco de Dados (Sem Validação Estática no Código):** O teto de transferência não deve ser fixado em anotações estáticas no código (`@Max(5_000_000L)`). Cada cliente pode ter aprovações de limites diferenciadas, devendo o backend sempre consultar o banco de dados.
2. **Segurança de PII (Sem CPF em Requisições GET):** Por conformidade estrita com LGPD e normas bancárias, CPFs e dados de conta não devem ser transmitidos via query parameters em requisições `GET` (que são persistidos em logs de servidores web, proxies reversos e cabeçalhos Referer). A consulta de conta/favorecido deve ser feita sempre via **`POST /api/v1/accounts/lookup`** com corpo JSON cifrado.

---

## 1. Limite Diário de Transferências Dinâmico no Banco de Dados

### 1.1. Estado Atual no Código-Fonte e Limitação Estática

* **`account-service`**:
  * Na tabela `account` (`services/account-service/src/main/resources/db/migration/V1__init_account_schema.sql:26-41`), existem apenas as colunas: `id`, `customer_id`, `branch`, `account_number`, `check_digit`, `status`, `version`, `created_at`, `updated_at`, `closed_at`.
  * **Não existe** coluna nem conceito de limite (`daily_limit`, `daily_transfer_limit_cents`) na entidade `AccountEntity` (`AccountEntity.java:36-70`).

* **`transaction-service`**:
  * Em `TransferRequest.java:15` (`services/transaction-service/src/main/java/com/bankledger/transaction/adapters/in/rest/dto/TransferRequest.java`):
    ```java
    @NotNull(message = "amount must not be null")
    @Positive(message = "amount must be greater than zero")
    @Max(value = 5_000_000L, message = "amount must not exceed 5000000 cents (R$ 50.000,00)")
    Long amount
    ```
    Existe uma anotação estática `@Max` travando o valor máximo em R$ 50.000,00 para qualquer usuário. **Essa anotação estática deve ser removida** para permitir que clientes com limites aprovados superiores ou inferiores operem de acordo com a aprovação registrada no banco de dados.
  * Em `TransactionController.java:188-256` e `CreateTransferService.java:43-98`:
    * A transação é gravada na tabela `financial_transaction` sem consultar o limite aprovado da conta no banco de dados.

* **`query-service`**:
  * Em `BalanceResponse.java:32-33`:
    ```java
    return new BalanceResponse(
        accountId, balance, balance, 0L,
        5000000L, // dailyLimitCents fixo (R$ 50.000,00)
        0L,       // usedTodayCents fixo
        0.0, currency != null ? currency : "BRL", sequence, updatedAt
    );
    ```
    O valor de `dailyLimitCents` está **hardcoded** em 50.000,00 reais e `usedTodayCents` está **hardcoded em 0L**.

### 1.2. Modelagem Dinâmica Baseada em Banco de Dados

1. **Tabela e Limite Individual Aprovado por Conta:**
   * **No `account-service`:**
     * Migration `V3__add_customer_address_and_account_limits.sql`: adiciona coluna `daily_transfer_limit_cents BIGINT NOT NULL DEFAULT 5000000` na tabela `account`.
   * **No `transaction-service`:**
     * Migration `V3__add_account_limit_table.sql`: cria tabela `account_limit`:
       ```sql
       CREATE TABLE IF NOT EXISTS account_limit (
           account_id                 UUID PRIMARY KEY,
           daily_transfer_limit_cents BIGINT NOT NULL DEFAULT 5000000,
           approved_by                VARCHAR(80) NOT NULL DEFAULT 'SYSTEM_DEFAULT',
           updated_at                 TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
       );
       ```
     * Permite que rotinas de backoffice ou aprovação bancária definam limites específicos para cada correntista diretamente no banco de dados.
2. **Validação Dinâmica em Tempo de Execução (`CreateTransferService`):**
   * Antes de aceitar qualquer transferência, o `transaction-service`:
     1. Consulta o limite individual aprovado no banco de dados:
        ```java
        long approvedLimit = accountLimitRepository.findById(command.sourceAccountId())
                .map(AccountLimitEntity::getDailyTransferLimitCents)
                .orElse(5_000_000L);
        ```
     2. Consulta no banco de dados a soma de transferências já realizadas/pendentes daquela conta na data de hoje (considerando o início do dia civil em horário de Brasília `America/Sao_Paulo`):
        ```sql
        SELECT COALESCE(SUM(amount), 0)
        FROM financial_transaction
        WHERE source_account_id = :sourceAccountId
          AND type = 'TRANSFER'
          AND status IN ('PENDING', 'COMPLETED')
          AND created_at >= :startOfDayBrt;
        ```
     3. Valida: Se `(usedTodayCents + command.amount()) > approvedLimit`, rejeita com HTTP `422 Unprocessable Entity` (`ProblemDetail: DAILY_LIMIT_EXCEEDED`, informando limite aprovado e total consumido).
   * Desta forma, a validação é **100% dinâmica via banco de dados** e suporta limites customizados por cliente.

---

## 2. Consulta Segura de Conta e Favorecido via POST (Segurança de PII)

### 2.1. Vulnerabilidade do Modelo Anterior com GET

* O endpoint atual (`GET /api/v1/accounts/lookup?agency=...&number=...`) e qualquer proposta de passar CPF na query string (`GET /api/v1/accounts/lookup?cpf=...`) apresentam riscos graves de segurança:
  1. **Vazamento em Logs de Acesso:** Servidores web (Tomcat, Nginx, API Gateways) gravam a URL completa com a query string nos logs de acesso (`access.log`), expondo CPFs e dados bancários em texto claro.
  2. **Cache e Histórico:** Navegadores e proxies intermediários armazenam URLs de requisições `GET` em histórico e memórias cache locais.
  3. **Cabeçalho `Referer`:** Navegadores podem vazar a query string com o CPF em requisições de terceiros.

### 2.2. Solução Mandatória: Endpoint POST com Corpo Cifrado

* **Novo Endpoint no `account-service`:**  
  `POST /api/v1/accounts/lookup`
* **Contrato do Payload (`AccountLookupRequest`):**
  ```json
  {
    "searchType": "CPF",
    "cpf": "52998224725",
    "agency": null,
    "accountNumber": null
  }
  ```
  Ou, para busca tradicional por conta:
  ```json
  {
    "searchType": "ACCOUNT",
    "cpf": null,
    "agency": "0001",
    "accountNumber": "100000"
  }
  ```
* **Vantagens de Segurança:**
  * O corpo da requisição `POST` é transmitido dentro da carga cifrada pelo TLS/HTTPS.
  * O CPF e dados bancários **nunca** aparecem em logs de URL de roteadores, gateways, proxies ou histórico de browser.
  * Resposta desmascarada para conferência no frontend antes do envio da transferência:
    ```json
    {
      "accountId": "a3b4c5d6-...",
      "branch": "0001",
      "accountNumber": "100000",
      "checkDigit": "4",
      "holderName": "Carlos Eduardo Silva",
      "cpf": "529.982.247-25",
      "bankCode": "001",
      "bankName": "Nexus Bank S.A."
    }
    ```

---

## 3. Extrato Bancário, Contraparte e Protocolo Canônico

### 3.1. Geração de Protocolo Canônico Bancário

* Formato padronizado: `PRT-YYYYMMDD-XXXXXX`
* O protocolo é gerado no momento da recepção da transação pelo `transaction-service`, gravado na coluna `protocol_number` da tabela `financial_transaction` e propagado para o `query-service`.
* Permite conciliação auditável e identificação unívoca nos comprovantes e extratos.

### 3.2. Enriquecimento de Dados no Extrato (`query-service`)

* A tabela `statement_entry` já possui colunas para contraparte (`counterparty_name`, `counterparty_document_masked`, `counterparty_account`).
* O consumidor do `query-service` preenche essas colunas com os dados do pagador ou favorecido correspondente, além de atribuir as categorias semânticas corretas:
  * Débito de transferência: `type = "TRANSFER_OUT"`, `description = "Transferência enviada"`
  * Crédito de transferência: `type = "TRANSFER_IN"`, `description = "Transferência recebida"`
  * Depósito: `type = "DEPOSIT"`, `description = "Depósito em conta corrente"`
  * Saque: `type = "WITHDRAWAL"`, `description = "Saque em terminal"`

---

## 4. Perfil do Usuário e Dados de Endereço

### 4.1. Migração e Modelo de Domínio no `account-service`

* Migration `V3__add_customer_address_and_account_limits.sql`:
  * Adiciona na tabela `customer`: `postal_code`, `street`, `number`, `complement`, `neighborhood`, `city`, `state`.
* Criação do Value Object `Address` e integração nas entidades `Customer` e `CustomerEntity`.
* Criação do endpoint `PUT /api/v1/accounts/me/profile` para atualização de telefone, e-mail e endereço residencial.
* Enriquecimento de `GET /api/v1/accounts/me` para retornar todos os dados cadastrais e endereço completo.

---

## 5. Matriz de Contratos Refinada

| Serviço | Método | Endpoint | Descrição / Payload |
| :--- | :---: | :--- | :--- |
| `account-service` | `POST` | `/api/v1/accounts/lookup` | **Body:** `{ searchType, cpf?, agency?, accountNumber? }` -> Retorna dados do favorecido |
| `account-service` | `GET` | `/api/v1/accounts/me` | Retorna perfil completo do cliente, endereço e limite aprovado |
| `account-service` | `PUT` | `/api/v1/accounts/me/profile` | **Body:** `{ phone, email, address: { ... } }` -> Atualiza contato e endereço |
| `transaction-service` | `POST` | `/api/v1/transactions/transfers` | Executa transferência checando limite individual aprovado no banco de dados |
| `query-service` | `GET` | `/api/v1/queries/accounts/{id}/balance` | Retorna saldo atual, limite aprovado individual e limite usado hoje |
| `query-service` | `GET` | `/api/v1/queries/accounts/{id}/statement` | Retorna extrato com protocolo bancário canônico e contraparte |
