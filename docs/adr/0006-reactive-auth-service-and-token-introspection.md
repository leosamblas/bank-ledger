# ADR 0006: Microsserviço Reativo de Autenticação e Validação Descentralizada com JWKS

## Status
Aprovado

## Contexto e Pergunta do Usuário
O ecossistema de microsserviços do banco necessita de uma solução corporativa para gerenciamento de identidades, autenticação de clientes (mobile/web) e credenciais de segurança entre microsserviços (mTLS e OAuth2 Client Credentials).

Foi formulada pelo usuário a seguinte proposta arquitetural para avaliação:
> *"Se eu tenho um fluxo de login, não compensa ter um micro serviço separado para lidar com essa lógica e chamar ele quando eu precisar fazer as validações? Quero revisar essa parte para poder incluir no projeto de forma apartada e cada serviço que precise utilizar faça as chamadas internamente de forma reativa."*

Essa questão abrange duas decisões de engenharia cruciais e distintas:
1. **Separação de Serviço**: Deve o fluxo de login e gestão de credenciais residir em um microsserviço independente (`auth-service`)?
2. **Estratégia de Validação de Tokens**: Devem os serviços negociais (`account-service`, `transaction-service`, `ledger-service`) chamar o `auth-service` internamente (mesmo que com clientes HTTP reativos assíncronos) a cada requisição bancária recebida para validar a autenticidade do token?

---

## Decisão Arquitetural

Aprovamos a **separação de um microsserviço dedicado e reativo (`auth-service`)**, mas **rejeitamos a validação por chamadas internas de introspecção a cada requisição**, adotando como padrão arquitetural a **Validação Criptográfica Descentralizada com JWKS (RFC 7517) e Criptografia Assimétrica (RS256/EdDSA)**.

```
+--------------------------------------------------------------------------------------------------+
| FLUXO DE LOGIN (Baixa frequência / Alto consumo de CPU para Hashing de Senhas)                    |
+--------------------------------------------------------------------------------------------------+
  [ Cliente ]                                      [ auth-service (Reativo) ]
       |                                                       |
       | --- 1. POST /auth/login (credenciais + MFA) --------> |  (Argon2 / BCrypt)
       | <--- 2. Retorna Access Token JWT (assinado com Chave Privada)
       |          + Refresh Token opaco rotativo ------------ |


+--------------------------------------------------------------------------------------------------+
| FLUXO DE TRANSAÇÕES BANCÁRIAS (Alta frequência: 50.000+ req/s / Validação Local Descentralizada)  |
+--------------------------------------------------------------------------------------------------+
  [ Cliente ]                                 [ transaction-service ]            [ auth-service ]
       |                                                 |                              |
       |                                       [ Inicialização / Cache ]                |
       |                                                 | --- GET /.well-known/jwks -> |
       |                                                 | <--- Chaves Públicas (JWKS)- |
       |                                                 |                              |
       | --- 3. POST /transactions --------------------> |                              |
       |        Authorization: Bearer <JWT>              |                              |
       |                                                 |                              |
       |                                        [ Validação Criptográfica ]             |
       |                                        - Assinatura RS256 em memória           |
       |                                        - Claims: iss, aud, exp, roles          |
       |                                        - Custo: < 1 µs (ZERO chamada de rede)  |
       |                                                 |                              |
       | <--- 4. HTTP 202 Accepted --------------------- |                              |
```

---

## Detalhamento das Avaliações

### 1. Separação de Serviço: Criação do `auth-service` (APROVADO)
A segregação do ciclo de vida de autenticação em um serviço dedicado (`auth-service`) é altamente recomendada pelas seguintes razões:

- **Princípio da Responsabilidade Única (SRP)**: O `auth-service` concentra exclusivamente os fluxos de login, registro, recuperação de senha, hashing criptográfico (Argon2id / BCrypt), desafios de Multi-Factor Authentication (MFA via TOTP / WebAuthn FIDO2), rotação de *Refresh Tokens* e proteção contra ataques de força bruta (*rate limiting* e *lockout* por IP/usuário).
- **Isolamento de Credenciais e Conformidade de Segurança (PCI-DSS / LGPD)**: As credenciais brutas, hashes de senhas e segredos de MFA ficam restritos ao banco de dados exclusivo do `auth-service`. Microsserviços de contas, transações e contabilidade jamais têm acesso às tabelas de credenciais dos clientes.
- **Escalabilidade Assimétrica Reativa**: O cálculo de hashes de senha é deliberadamente projetado para ser computacionalmente custoso em termos de CPU e memória. Construir o `auth-service` com **Spring Boot Reativo (WebFlux / R2DBC ou Virtual Threads do Java 21+)** permite que ele escale horizontalmente sob picos de login sem comprometer ou consumir as threads de execução dos serviços contábeis (`ledger-service`).

---

### 2. Validação por Chamadas HTTP Internas: Avaliação de Riscos (REJEITADO)
A proposta de *"cada serviço que precise utilizar faça as chamadas internamente de forma reativa"* contra o `auth-service` (Token Introspection síncrona / RFC 7662) para cada transação bancária é **inviável em arquiteturas de alto volume**:

| Critério | Chamada Interna ao `auth-service` a Cada Requisição | Validação Descentralizada com JWKS |
| :--- | :--- | :--- |
| **Resiliência / Ponto Único de Falha (SPOF)** | **Crítico**: Se o `auth-service` sofrer degradação, GC pause ou restart, **todos** os serviços bancários param imediatamente (falha em cascata). | **Excelente**: Se o `auth-service` estiver temporariamente indisponível, os demais serviços continuam validando transações normalmente via chaves em cache. |
| **Latência por Transação** | **Ruim**: Acrescenta de 10ms a 30ms de round-trip de rede HTTP em cada lançamento financeiro. | **Zero Overhead de Rede**: Validação em memória sub-microssegundo (< 1 microssegundo). |
| **Consumo de Infraestrutura** | **Insustentável**: A 50.000 transações/s, o `auth-service` precisaria processar 50.000 requisições de validação/s adicionais, exigindo dezenas de pods extras apenas para introspecção. | **Mínimo**: Zero tráfego de rede para validação de tokens em operação rotineira. |
| **Acoplamento Temporal** | Forte acoplamento síncrono. | Totalmente desacoplado. |

---

### 3. Padrão Recomendado: Criptografia Assimétrica com JWKS (RFC 7517 / RS256)

#### Como Funciona a Validação Descentralizada:
1. **Assinatura com Chave Privada**: O `auth-service` gera um par de chaves assimétricas (RSA 2048/4096 bits ou curvas elípticas Ed25519). A **chave privada** é mantida em segredo absoluto (armazenada em Vault / HSM) e usada para assinar digitalmente os JWTs emitidos no login.
2. **Exposição do JWKS**: O `auth-service` expõe um endpoint estático público: `GET /.well-known/jwks.json`, contendo unicamente as chaves públicas identificadas por um `kid` (Key ID).
3. **Cache de Chaves Públicas nos Microsserviços Negociais**:
   - Serviços como `account-service`, `transaction-service` e `ledger-service` utilizam o **Spring Security Resource Server** com Nimbus JOSE.
   - O conjunto de chaves públicas é baixado na inicialização e mantido em cache local em memória (com TTL de atualização configurável, ex: 24 horas).
4. **Validação Criptográfica Local**:
   - Ao receber uma requisição HTTP com o header `Authorization: Bearer <JWT>`, o microsserviço negocial:
     - Identifica o `kid` no cabeçalho do token.
     - Obtém a chave pública correspondente diretamente da sua memória local.
     - Valida matematicamente a assinatura criptográfica, a expiração (`exp`), o início de validade (`nbf`), o emissor confiável (`iss = https://auth.bank.internal`) e a audiência pretendida (`aud`).
     - Extrai as permissões (*roles* / *scopes*) contidas nas *claims* do JWT.
   - **Tempo total de validação**: Frações de microssegundo (< 1 µs), sem emitir um único pacote de rede.
5. **Rotação de Chaves Segura**: Se um token chegar com um `kid` desconhecido, o Resource Server consulta o endpoint `/jwks.json` uma única vez para atualizar seu cache de chaves públicas, suportando rotações transparentes de chaves sem indisponibilidade.

---

### 4. Tratamento de Revogação de Tokens e Introspecção Reativa Especializada

Para atender a cenários onde a invalidação imediata de sessões é necessária antes do tempo de expiração do JWT (ex: cancelamento emergencial de conta por fraude ou troca de senha):

#### Estratégia de Dupla Camada para Revogação:
- **Camada 1: Access Tokens de Vida Curta**:
  Os tokens de acesso possuem TTL curto (5 a 15 minutos). O risco residual de descontinuidade de estado é limitado a essa janela temporal estrita.
- **Camada 2: Blacklist de Revogação Reativa via Kafka/Redis**:
  - Quando um usuário realiza logout explícito ou uma conta é bloqueada, o `auth-service` publica um evento assíncrono no tópico `auth.token-revoked.v1` contendo o `jti` (JWT ID) ou `userId` e o timestamp de invalidação.
  - Os microsserviços negociais consomem o evento e atualizam uma blacklist distribuída em memória local (Caffeine Cache / Redis SET com TTL correspondente ao tempo restante do token).
- **Introspecção Reativa Pontual (Apenas para Transações de Risco Extremo)**:
  - Se for estritamente mandatório auditar o status da sessão em tempo real para operações de altíssimo valor (ex: transferências acima de R$ 1.000.000,00 ou alterações de chaves PIX sensíveis):
    - O microsserviço pode utilizar um cliente reativo assíncrono (`WebClient` ou cliente HTTP baseado em Virtual Threads do Java 21) chamando o endpoint `POST /oauth2/introspect`.
    - Essa chamada reativa deve ser encapsulada em um **Circuit Breaker** (Resilience4j) com timeout agressivo (ex: 150ms) e política de fallback explícita, garantindo que a indisponibilidade do `auth-service` não derrube os serviços contábeis.

---

## Consequências

### Positivas
- **Isolamento de Segurança e Falha**: O `auth-service` cuida de segredos sensíveis e suporta picos de autenticação de forma elástica, sem risco de derrubar o processamento bancário rotineiro.
- **Desempenho Extremo no Core Bancário**: Validação de autorização em memória local em < 1 µs por requisição, suportando mais de 50.000 transações/s sem sobrecarregar a infraestrutura de rede.
- **Alta Resiliência e Desacoplamento Operacional**: Se o `auth-service` for reiniciado ou sofrer manutenção temporária, os clientes autenticados continuam operando normalmente até o término da validade de seus tokens de acesso.
- **Compatibilidade com Padrões Abertos**: Uso estrito de padrões da indústria (OAuth2, OIDC, RFC 7519 JWT, RFC 7517 JWKS, RFC 7662 Token Introspection).

### Negativas / Trade-offs
- **Janela de Revogação de Tokens**: Em caso de revogação emergencial, tokens continuam criptograficamente válidos até a expiração (5-15 min), a menos que a infraestrutura de blacklist em tempo real (Kafka/Redis) esteja ativa.
- **Necessidade de Sincronização de Relógios (NTP)**: Servidores de todos os microsserviços devem manter sincronização rigorosa de horário via protocolo NTP para evitar falhas em validações de `exp` e `nbf`.
