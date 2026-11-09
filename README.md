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
```bash
cd web
npm install
npm run dev
# Acesse http://localhost:3000
```

---

*Repositório inicializado e mantido ativamente com base nas práticas modernas de engenharia de software.*
