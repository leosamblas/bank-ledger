# Design Doc: Racionalização de Ações e Otimização de Menus nas Telas de Login e Cadastro

> **Autor:** Antigravity AI (com base em `@[plugin:ui-ux-pro-max]`)  
> **Data:** 17 de Novembro de 2026  
> **Status:** Proposta de Arquitetura & Design de Interface  
> **Alvo:** Telas de Autenticação (`/login`), Onboarding (`/signup`), `Header.tsx` e `OpenAccountForm.tsx`  

---

## 1. Intenção & Diagnóstico do Problema

As páginas de **Login (`/login`)** e **Abertura de Conta (`/signup`)** são os dois funis mais sensíveis de conversão e retenção do **Nexus Bank**. Ao analisar a estrutura atual com as heurísticas do `ui-ux-pro-max`, foram identificados gargalos de sobrecarga visual e redundância de ações que dispersam a atenção do usuário:

### 1.1 Tripla Redundância de Navegação para a Home ("Voltar para o início")
Em ambas as telas, o usuário se depara com 3 caminhos concorrentes para a mesma ação:
1. **Logo da Marca no Header:** `<Link href="/">Nexus Bank</Link>`
2. **Breadcrumb Trail:** `<nav><Link href="/">Início</Link> &gt; Acesso à Conta</nav>`
3. **Rodapé abaixo do Card:** `<Link href="/">Voltar para a página inicial</Link>`
*Impacto UX:* Funis de autenticação e cadastro exigem foco absoluto (*tunnel vision* / layout enxuto). Múltiplos links de saída reduzem a taxa de conversão e criam ruído visual desnecessário.

### 1.2 Ações Cruzadas Duplicadas (Login $\leftrightarrow$ Cadastro)
* Na tela de Login:
  - Botão no topo direito do Header: `"Abrir Conta Gratuita &rarr;"`
  - Texto no rodapé do formulário: `"Ainda não é cliente? Abra sua conta gratuita &rarr;"`
* Na tela de Cadastro:
  - Botão no topo direito do Header: `"Já tem conta? Entrar &rarr;"`
  - Texto no rodapé do formulário: `"Já possui uma conta? Faça login"`
*Impacto UX:* Ter dois botões/links principais para a mesma página de destino confunde a hierarquia visual (*Rule Category 4: Primary Action discipline*).

### 1.3 Selos de Segurança Duplicados
Na tela de Login, o usuário visualiza:
- `"Ambiente Seguro SSL"` com pulso verde no Header.
- `"Ambiente Seguro 256-bit"` com ícone no cabeçalho interno do formulário.
- `"Segurança de Nível Institucional"` na coluna direita.
*Impacto UX:* A insistência visual repetida gera poluição e reduz a clareza dos elementos primários.

### 1.4 Limitações do `Header.tsx` Atual
- Cores hardcoded (`#0e1c2f`, `#1a2d47`) que não utilizam os tokens do `@theme` do Tailwind v4 (`--color-primary-container`, `--color-surface-container`).
- Layout que quebra em coluna em smartphones (`flex-col sm:flex-row`), ocupando espaço vertical precioso antes da visualização do formulário.

---

## 2. Princípios de Solução (`ui-ux-pro-max`)

1. **Hierarquia Única de Ação Alternativa (Single Clear Switcher):**
   * O cabeçalho passa a conter o switcher contextual elegante e oficial:
     - No Login: *"Novo por aqui? Criar conta"* (botão pílula secundário ou link semântico com alto contraste).
     - No Cadastro: *"Já possui conta? Entrar"*.
   * Eliminação dos links repetidos abaixo do formulário.
2. **Eliminação dos Breadcrumbs em Funis de Conversão:**
   * Páginas de login e signup são telas de destino focadas (*focus flow*), não páginas hierárquicas profundas. O logo no topo já provê o retorno natural à página inicial.
3. **Cabeçalho Compacto e Responsivo:**
   * Altura fixa padronizada (`h-16`), alinhamento horizontal contínuo mesmo em mobile (`flex row justify-between items-center`), mantendo os alvos de toque $\ge 44 \times 44$ px.
4. **Respiro Visual no Formulário:**
   * Com a remoção do breadcrumb e dos links de rodapé duplicados, o card de login e o wizard de cadastro ganham centralização e clareza visual, acelerando o preenchimento.

---

## 3. Especificação das Mudanças

### 3.1 Componente `Header.tsx` (`web/src/components/ui/Header.tsx`)
* **Estrutura:**
  * Linha única horizontal: Logo Nexus Bank à esquerda, Selo Seguro sutil ao centro/direita (em desktop), e Botão de Alternância à direita.
  * Tokens de cores: Substituir `#0e1c2f` por `bg-primary-container text-on-primary hover:bg-primary-container/90`.
  * Área de toque: `min-h-[44px]` para o botão de ação.

### 3.2 Página de Login (`web/src/app/login/page.tsx`)
* **Remoções:**
  * Remover o `<nav aria-label="Navegação estrutural">` (breadcrumbs).
  * Remover o bloco inferior com `"Voltar para a página inicial"` e `"Ainda não é cliente? Abra sua conta gratuita"`.
  * Remover a pílula interna duplicada `"Ambiente Seguro 256-bit"` dentro do card esquerdo, mantendo o título limpo: *"Acesse sua conta Nexus Bank"*.
* **Resultado:** Formulário limpo, centrado verticalmente, com o foco total no preenchimento de CPF e Senha.

### 3.3 Página de Cadastro (`web/src/app/signup/page.tsx` & `OpenAccountForm.tsx`)
* **Remoções:**
  * Remover o `<nav aria-label="Navegação estrutural">` (breadcrumbs) em `signup/page.tsx`.
  * Em `OpenAccountForm.tsx`: remover o bloco inferior com `"Já possui uma conta? Faça login"` e `"Voltar para a página inicial"`.
* **Resultado:** O usuário foca 100% no Stepper de 3 etapas sem distrações laterais.

---

## 4. Análise e Correção de Vulnerabilidade de Segurança do Next.js

### 4.1 Diagnóstico da Vulnerabilidade
Durante a execução de compilação em produção (`next build`), o motor Turbopack do Next.js emitiu um alerta crítico `SecurityFatalError` notificando sobre vulnerabilidades conhecidas na versão instalada (`16.4.3`):
* **GHSA-mfr8-8qww-v76h (Severidade Moderada):** Divulgação indevida de informações (*Information disclosure*) em parâmetros raiz quando uma função `'use cache'` é invocada via `unstable_cache()`.
* **GHSA-vc7v-qv4w-wrv8 (Severidade Moderada):** Vazamento de cache (*Cache leak*) entre valores de parâmetros raiz em funções aninhadas com `'use cache'` através de metadados atrasados de stream interno.
* **GHSA-fc5q-39xc-vjpf (Severidade Alta):** ReDoS em `picomatch` por repetição de extglobs em branch único através de dependências de ferramentas de linting.

### 4.2 Faixa Afetada vs. Versão de Correção
* **Versão no Projeto:** Next.js `16.4.3` (faixa afetada: `16.2.0 - 16.4.4`).
* **Versão Corrigida e Homologada:** Next.js **`16.4.6`** e `eslint-config-next@16.4.6`.

### 4.3 Plano de Mitigação e Upgrade Seguro
1. **Atualização de Pacotes em `web/package.json`:**
   * Atualizar `next` de `16.4.3` para `16.4.6`.
   * Atualizar `eslint-config-next` de `16.4.3` para `16.4.6`.
2. **Resolução de Dependências Transitivas:**
   * Inclusão de override seguro em `package.json` para pacotes de globbing/parsing se necessário para sanear alertas de ReDoS sem breaking changes.
3. **Validação de Build e Zero Regressão:**
   * Executar `npm --prefix web install` para reconciliar o `package-lock.json`.
   * Executar `npm --prefix web run build` para confirmar a total extinção do erro `SecurityFatalError` do Turbopack.
   * Executar toda a suíte de 18 testes automatizados (`npm --prefix web test`) para garantir 100% de integridade com o React 19.3.0.

