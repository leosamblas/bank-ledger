# Especificação Técnica e Arquitetural: Regras de Negócio e Gestão de Perfil

**Data:** 17 de Novembro de 2026 (Revisão 2 - Governança no Banco de Dados e Segurança PII)  
**Status:** Aprovado para Planejamento e Execução  
**Branch:** `feature/business-rules-overhaul`  
**Escopo:** Backend (Microserviços Spring Boot / PostgreSQL / Kafka) e Frontend (Next.js 16 / React 19 / Tailwind v4)

---

## 1. Visão Geral e Princípios Fundamentais

Esta especificação define a evolução arquitetural do **Nexus Bank Ledger** com ênfase em conformidade regulatória, integridade contábil e proteção a dados pessoais (LGPD):

### Princípios Chave de Arquitetura e Segurança:
1. **Governança Dinâmica de Limites no Banco de Dados (Sem Validação Estática):**
   - A validação de limites de transferência não pode depender de anotações estáticas no código (`@Max` fixo).
   - Cada correntista possui um limite diário de transferências aprovado individualmente no banco de dados (`daily_transfer_limit_cents`), permitindo limites diferenciados por perfil/aprovação de risco.
   - O `transaction-service` sempre consulta o banco de dados antes de processar a operação: busca o limite aprovado da conta de origem e calcula a soma das transferências do dia corrente.
2. **Proteção Rigorosa de Dados Pessoais (Sem CPF em Métodos GET):**
   - CPFs e números de conta nunca devem ser passados como parâmetros de URL (`GET /lookup?cpf=...`). Parâmetros de URL são registrados em texto claro em logs de proxies, gateways, balanceadores e histórico do navegador.
   - Toda consulta de destinatário/conta utiliza **`POST /api/v1/accounts/lookup`** com corpo JSON criptografado na camada de transporte TLS.
3. **Extrato Humanizado e Protocolo Canônico:**
   - Protocolo bancário gerado no formato `PRT-YYYYMMDD-XXXXXX` para rastreabilidade auditável.
   - Identificação transparente de favorecido e pagador com descrições em língua portuguesa.
4. **Comprovantes e Ações Reais:**
   - Impressão A4 limpa com isolamento via folha de estilo `@media print`.
   - Comprovante consolidado do período com resumo patrimonial.
   - Exportação funcional de relatórios em CSV (Excel BR) e OFX (contábil).
5. **Gestão de Perfil e Endereço Completo:**
   - Cadastro e persistência de endereço residencial no backend e interface com busca automática por CEP.

---

## 2. Arquitetura da Solução

```mermaid
flowchart TD
    subgraph Frontend["Frontend (Next.js 16 / React 19)"]
        UI_Transfer["Tela de Transferência\n(Toggle: Agência/Conta ou CPF)"]
        UI_Statement["Tela de Extrato\n(Filtros, CSV, OFX, Impressão)"]
        UI_Receipt["Modal de Comprovante\n(Protocolo Canônico & Hash)"]
        UI_Profile["Modal/Tela de Perfil\n(Edição Contato e Endereço)"]
        UI_Limit["Barra de Limite Diário\n(Cálculo Reativo ao Digitar)"]
    end

    subgraph Gateway["API Gateway / BFF (Porta 8080)"]
        RT_Account["/api/v1/accounts/*"]
        RT_Tx["/api/v1/transactions/*"]
        RT_Query["/api/v1/queries/*"]
    end

    subgraph Backend["Microserviços Backend"]
        subgraph AccountService["account-service (8081)"]
            AS_Lookup["POST /accounts/lookup\n(Busca segura por Conta ou CPF via Body JSON)"]
            AS_Profile["Atualização de Perfil & Endereço\n(V3 Flyway Migration)"]
        end

        subgraph TransactionService["transaction-service (8082)"]
            TS_Limit["Validação Dinâmica no Banco:\n1. Busca limite aprovado da conta\n2. Soma transferências do dia civil\n3. Valida teto"]
            TS_Protocol["Geração de Protocolo Canônico (PRT-...)"]
            TS_Outbox["Outbox PostJournalCommand"]
        end

        subgraph LedgerService["ledger-service (8084)"]
            LS_Core["Motor Contábil de Partidas Dobradas\n(Journal & Ledger Entries)"]
        end

        subgraph QueryService["query-service (8083)"]
            QS_Balance["Saldo + Limite Aprovado no Banco + Usado Hoje"]
            QS_Statement["Extrato com Contraparte e Protocolo"]
        end
    end

    UI_Transfer -->|POST /accounts/lookup (JSON Body)| RT_Account
    UI_Transfer -->|POST /transactions/transfers| RT_Tx
    UI_Statement --> RT_Query
    UI_Receipt --> RT_Query
    UI_Profile --> RT_Account
    UI_Limit --> RT_Query

    RT_Account --> AS_Lookup
    RT_Account --> AS_Profile
    RT_Tx --> TS_Limit
    RT_Tx --> TS_Protocol
    TS_Outbox --> LS_Core
    LS_Core --> QS_Statement
    LS_Core --> QS_Balance
```

---

## 3. Especificação Detalhada por Módulo

### 3.1. Limite Diário de Transferências Dinâmico no Banco de Dados

#### Regra de Negócio:
- Cada conta possui seu limite diário individual aprovado e registrado no banco de dados (`daily_transfer_limit_cents`), permitindo limites diferenciados (R$ 5.000,00, R$ 50.000,00, R$ 250.000,00, etc.).
- **Remoção de Limitações Estáticas:** A anotação `@Max(5_000_000L)` é removida do DTO `TransferRequest.java`.
- **Validação Dinâmica no `transaction-service`:**
  - Consulta no banco de dados a tabela `account_limit` para obter o limite aprovado da conta de origem.
  - Consulta no banco de dados a tabela `financial_transaction` somando as transferências efetuadas no dia corrente (UTC-3 / Horário de Brasília):
    ```sql
    SELECT COALESCE(SUM(amount), 0)
    FROM financial_transaction
    WHERE source_account_id = :sourceAccountId
      AND type = 'TRANSFER'
      AND status IN ('PENDING', 'COMPLETED')
      AND created_at >= :startOfDayBrt;
    ```
  - Se `(usedTodayCents + request.amount) > approvedLimit`, rejeita com HTTP `422 Unprocessable Entity` (`ProblemDetail: DAILY_LIMIT_EXCEEDED`, detalhando o limite aprovado e o valor restante).
- **Projeção no `query-service`:**
  - O endpoint `GET /api/v1/queries/accounts/{id}/balance` retorna o `dailyLimitCents` específico cadastrado no banco para a conta, juntamente com o `usedTodayCents` calculado dinamicamente das operações do dia.
- **Frontend:**
  - Exibe os valores reais do correntista: Total Aprovado, Consumido Hoje e Restante Disponível.
  - Barra de progresso visual atualiza em tempo real enquanto o usuário digita o valor, bloqueando a confirmação caso o valor informado ultrapasse o limite disponível do cliente.

---

### 3.2. Consulta Segura de Destinatário via POST e Transferência por CPF

#### Regra de Negócio:
- **Segurança de PII:** Nenhum CPF ou agência/conta deve ser passado em query parameters de requisições `GET`.
- **Endpoint POST:** `POST /api/v1/accounts/lookup`
  ```json
  {
    "searchType": "CPF", // ou "ACCOUNT"
    "cpf": "52998224725",
    "agency": null,
    "accountNumber": null
  }
  ```
- **Resposta Desmascarada para Conferência:**
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
- **Frontend:**
  - Na tela de transferência, o usuário pode escolher transferir "Por Agência e Conta" ou "Por CPF".
  - O frontend submete o JSON via `POST /api/v1/accounts/lookup`.
  - O card de confirmação exibe o nome real completo e CPF formatado do favorecido para verificação visual antes do envio.

---

### 3.3. Extrato Bancário Humanizado e Contraparte

#### Regra de Negócio:
- Mapeamento em português claro:
  - `TRANSFER_OUT`: **Transferência enviada**
  - `TRANSFER_IN`: **Transferência recebida**
  - `DEPOSIT`: **Depósito em conta corrente**
  - `WITHDRAWAL`: **Saque em terminal**
- Exibição explícita de **"De:"** (origem) em créditos e **"Para:"** (favorecido) em débitos, incluindo nome completo, documento formatado e agência/conta.

---

### 3.4. Comprovante Canônico e Auditoria

#### Regra de Negócio:
- Protocolo único canônico gerado pelo backend: `PRT-YYYYMMDD-XXXXXX`.
- Modal de Comprovante com:
  - Dados completos de Pagador e Favorecido.
  - Data/hora exata de liquidação contábil.
  - Protocolo bancário canônico.
  - Código hash SHA-256 de autenticação eletrônica.
  - Botão **"Imprimir Comprovante"** acionando impressão isolada em folha A4 limpa (`@media print`).

---

### 3.5. Comprovante Consolidado e Exportação de Relatórios

#### Regra de Negócio:
1. **Comprovante Consolidado:**
   - Modal com balanço financeiro periódico: Entradas (+), Saídas (-), Saldo Período, tabela consolidada e impressão.
2. **Exportação de Relatórios (CSV e OFX):**
   - **CSV:** Formato Excel PT-BR (delimitador `;` e codificação UTF-8 com BOM).
   - **OFX:** Padrão bancário internacional (SGML/XML) para conciliação contábil em softwares de gestão.

---

### 3.6. Gestão de Perfil de Usuário e Endereço

#### Regra de Negócio:
- **Campos Imutáveis:** Nome Completo, CPF, Data de Nascimento, Conta.
- **Campos Editáveis:** Telefone com máscara, E-mail e Endereço Completo (CEP com busca e autopreenchimento, logradouro, número, complemento, bairro, cidade, UF).
- **Backend:**
  - Tabela `customer` recebe colunas de endereço via migration `V3`.
  - Endpoint `PUT /api/v1/accounts/me/profile` atualiza os dados com validação.
  - Endpoint `GET /api/v1/accounts/me` retorna todos os dados cadastrais do correntista.

---

## 4. Matriz Atualizada de Contratos de API

| Microserviço | Método | Rota | Payload / Formato | Descrição |
| :--- | :---: | :--- | :--- | :--- |
| `account-service` | `POST` | `/api/v1/accounts/lookup` | **Body:** `{ searchType, cpf?, agency?, accountNumber? }` | Busca segura de conta/favorecido sem vazar PII em logs |
| `account-service` | `GET` | `/api/v1/accounts/me` | Header: `Authorization: Bearer <jwt>` | Retorna dados completos do perfil, endereço e limite aprovado |
| `account-service` | `PUT` | `/api/v1/accounts/me/profile` | **Body:** `{ phone, email, address: { ... } }` | Atualização de dados de contato e endereço residencial |
| `transaction-service` | `POST` | `/api/v1/transactions/transfers` | **Body:** `{ destinationAccountId, amount, description }` | Validação dinâmica no banco contra o limite individual aprovado |
| `query-service` | `GET` | `/api/v1/queries/accounts/{id}/balance` | Path: `accountId` | Retorna saldo e limite individual aprovado no banco + usado hoje |
| `query-service` | `GET` | `/api/v1/queries/accounts/{id}/statement` | Query: `from`, `to`, `size` | Retorna lançamentos com protocolo bancário canônico e contraparte |
