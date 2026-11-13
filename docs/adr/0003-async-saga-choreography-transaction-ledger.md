# Orquestração Assíncrona de Lançamentos Contábeis via Kafka

Para isolar a resiliência e a disponibilidade do core bancário (`ledger-service`), a comunicação entre o `transaction-service` e o `ledger-service` é 100% assíncrona, orientada a eventos e comandos via Kafka (`ledger.commands.v1` e `ledger.journal-results.v1`), intermediada pelo padrão Transactional Outbox e Inbox com execução em Virtual Threads. A API pública responde imediatamente HTTP `202 Accepted` com cabeçalho `Location`, permitindo que o cliente acompanhe a resolução via *polling* rápido, garantindo resiliência contra indisponibilidades transitórias e eliminando acoplamento temporal síncrono entre microsserviços.

