# Bank Ledger

Bem-vindo ao repositório do **Bank Ledger**, uma plataforma de Core Bancário (Ledger) desenhada para garantir consistência financeira com alta escalabilidade. 

Este projeto baseia-se no princípio contábil das **Partidas Dobradas (Double-Entry Accounting)**, onde toda movimentação possui origens e destinos simétricos (débito e crédito), garantindo que não haja perda de valores e facilitando processos de auditoria (balancete e extrato confiáveis).

## 🏗️ Arquitetura Resumida

O sistema utiliza padrões modernos para garantir alta disponibilidade e escalabilidade de dados financeiros:
* **Arquitetura Hexagonal (Ports & Adapters)**: Desacoplamento da regra de negócio (domínio) de frameworks, bancos de dados e mensageria.
* **CQRS (Command Query Responsibility Segregation)**: Separação física e lógica das rotinas de gravação de transações (PostgreSQL) e rotinas de leitura de extrato/saldo (MongoDB).
* **Consistência Eventual Segura**: Utilização do padrão *Transactional Outbox* para publicação assíncrona confiável de eventos em tópicos do Kafka.
* **Tolerância e Resiliência**: Particionamento de tabelas por data (`pg_partman`), particionamento das contas de caixa (*shards* do Banco Central) para evitar concorrência/lock, e suporte a alta idempotência na ingestão de eventos.
* **Real-Time Updates**: Utilização de *Server-Sent Events (SSE)* no Frontend em substituição ao polling, evitando gargalos de I/O em fluxos de atualização contínua.

## 📚 Documentação Interna

Todo o planejamento de negócio, topologia do sistema, diagramas de sequência, contratos e fluxos assíncronos foram detalhados e mantidos internamente.

Você pode encontrar o projeto técnico e os fluxos de negócios detalhados neste documento:
👉 **[Plano de Desenvolvimento e Arquitetura](./docs/plano_desenvolvimento_ledger.md)**

Para facilitar o desenvolvimento, todas as **APIs REST** foram especificadas em OpenAPI (Swagger) na pasta de contratos:
👉 **[Contratos de API (Swagger/OpenAPI)](./api-contracts/)**

---

*Repositório inicializado e mantido ativamente com base nas práticas modernas de engenharia de software.*
