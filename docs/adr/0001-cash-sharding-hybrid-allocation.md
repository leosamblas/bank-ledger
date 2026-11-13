# Alocação Híbrida de Shards de Caixa (SYS-CASH)

Para diluir a contenção de lock em depósitos e saques sem comprometer a semântica bancária ou o determinismo em retentativas, dividimos a conta de caixa do banco em 16 shards (`SYS-CASH-00` a `SYS-CASH-15`) e adotamos uma estratégia híbrida de alocação: o lançamento contábil respeita o terminal físico/lógico quando informado na requisição (`terminalId`), espelhando a realidade dos caixas eletrônicos e agências; em requisições genéricas ou sem terminal explícito, recorre ao hash determinístico do identificador da transação (`hash(transactionId) % 16`), garantindo distribuição de carga uniforme (~6,25% por shard) e estrita idempotência.

