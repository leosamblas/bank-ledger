# Lock Pessimista com Ordenação Anti-Deadlock no Core Contábil

No processamento de lançamentos de partidas dobradas no `ledger-service`, utilizamos bloqueio pessimista a nível de linha (`SELECT ... FOR UPDATE`) com ordenação determinística por UUID (`min(idA, idB)` antes de `max(idA, idB)`) dentro da mesma transação ACID do PostgreSQL. Esta decisão substitui o lock otimista para prevenir tempestades de retentativas (*retry storms*) sob concorrência intensa na mesma conta e elimina matematicamente a possibilidade de *deadlocks* ou saldos negativos em contas de clientes.

