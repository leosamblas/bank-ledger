# Auth & Onboarding Layout Streamlining Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Streamline the Login (`/login`) and Account Opening (`/signup`) screens by eliminating redundant exit links, duplicate breadcrumbs, and competing CTAs, while modernizing `Header.tsx` and upgrading Next.js to patch known security vulnerabilities (GHSA-mfr8-8qww-v76h, GHSA-vc7v-qv4w-wrv8).

**Architecture:** Upgrades Next.js and ESLint tooling to 16.4.6 to eliminate Turbopack `SecurityFatalError` build blocks, refactors `Header.tsx` into an ergonomic, single-row header using Tailwind CSS v4 design tokens, and removes competing navigation paths (breadcrumbs, duplicate footer back-links, and nested security badges) from both `/login` and `/signup` to maximize user focus and conversion rates.

**Tech Stack:** Next.js 16.4.6 (App Router), React 19, TypeScript 5, Tailwind CSS v4, Node.js Test Runner (`node:test`).

**Spec:** [`docs/superpowers/specs/2026-11-17-auth-onboarding-layout-streamlining-design.md`](file:///c:/Users/leosa/Documents/Projects/bank-ledger/docs/superpowers/specs/2026-11-17-auth-onboarding-layout-streamlining-design.md)

## Global Constraints
- Security: Next.js and `eslint-config-next` must be upgraded to version `16.4.6` to resolve critical vulnerabilities without breaking changes.
- Framework: Next.js 16 with React 19 App Router.
- Styling: Use semantic Tailwind CSS v4 design tokens (`bg-primary-container`, `text-on-primary`, `border-surface-container`, etc.). Zero arbitrary hardcoded hex codes.
- Touch Ergonomics: All interactive targets (buttons, links, toggles) must meet the minimum $44 \times 44$ px target size.
- Usability: Keep exactly one clear alternative action switcher per screen (in the header), eliminating duplicate links at the bottom of form cards.
- Tests: Maintain existing tests and add unit tests verifying the streamlined layout structure and security version floor.

## Review Focus
- Security Vulnerability Remediation: `next` and `eslint-config-next` in `web/package.json` must be bumped to `16.4.6`; `npm run build` must succeed without `SecurityFatalError`.
- Single Context Action: On `/login`, the only link to `/signup` should live in the header; the redundant bottom prompt must be removed.
- Single Context Action: On `/signup`, the only link to `/login` should live in the header; the redundant bottom prompt must be removed.
- Breadcrumb Removal: The breadcrumbs `<nav aria-label="Navegação estrutural">` must be removed from both `/login` and `/signup`, relying on the header logo to return home.
- Header Responsive Layout: `Header.tsx` must maintain a horizontal row (`flex-row items-center justify-between`) without stacking awkwardly on mobile.
- Accessibility: The header action buttons must maintain clear text, visible focus rings, and high contrast against their backgrounds.

---

### Task 1: Next.js Security Vulnerability Upgrade & Dependency Remediation

**Files:**
- Modify: `web/package.json`
- Test: `web/src/services/__tests__/securityUpgrade.test.ts`

**Interfaces:**
- Consumes: npm registry version `16.4.6`.
- Produces: Patched `web/package.json` and `package-lock.json` resolving GHSA-mfr8-8qww-v76h and GHSA-vc7v-qv4w-wrv8.

- [ ] **Step 1: Write the failing unit test for Next.js security version floor**

```typescript
// web/src/services/__tests__/securityUpgrade.test.ts
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

test('web/package.json uses secure Next.js version >= 16.4.6', () => {
  const pkgPath = path.resolve(process.cwd(), 'package.json');
  const pkg = JSON.parse(fs.readFileSync(pkgPath, 'utf-8'));

  const nextVersion = pkg.dependencies?.next || '';
  assert.ok(
    nextVersion === '16.4.6' || nextVersion === '^16.4.6',
    `Expected next to be 16.4.6, found: ${nextVersion}`
  );

  const eslintConfigNext = pkg.devDependencies?.['eslint-config-next'] || '';
  assert.ok(
    eslintConfigNext === '16.4.6' || eslintConfigNext === '^16.4.6',
    `Expected eslint-config-next to be 16.4.6, found: ${eslintConfigNext}`
  );
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --import tsx --test web/src/services/__tests__/securityUpgrade.test.ts` (or `npm --prefix web test`)  
Expected: FAIL with "Expected next to be 16.4.6, found: 16.4.3".

- [ ] **Step 3: Update `web/package.json` and install updated packages**

In `web/package.json`:
- Set `"next": "16.4.6"`
- Set `"eslint-config-next": "16.4.6"`

Run in `web/`:
`npm install`

- [ ] **Step 4: Run test to verify it passes**

Run: `node --import tsx --test web/src/services/__tests__/securityUpgrade.test.ts`  
Expected: PASS.

- [ ] **Step 5: Commit changes**

```bash
git add web/package.json web/package-lock.json web/src/services/__tests__/securityUpgrade.test.ts
git commit -m "fix(security): upgrade next and eslint-config-next to 16.4.6 to resolve advisories"
```

---

### Task 2: Modernize `Header.tsx` with Design Tokens, Compact Row Layout, and Clear Action Switcher

**Files:**
- Modify: `web/src/components/ui/Header.tsx:1-69`
- Test: `web/src/services/__tests__/header.test.ts`

**Interfaces:**
- Consumes: `currentPage: 'signup' | 'login'`.
- Produces: Compact, single-row `Header` with semantic tokens (`bg-primary-container`, `text-on-primary`) and touch-friendly action button.

- [x] **Step 1: Write the failing unit test for `Header.tsx`**

```typescript
// web/src/services/__tests__/header.test.ts
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

test('Header.tsx uses semantic theme tokens and no hardcoded dark navy hexes', () => {
  const headerPath = path.resolve(process.cwd(), 'src/components/ui/Header.tsx');
  const content = fs.readFileSync(headerPath, 'utf-8');

  // Must not have hardcoded #0e1c2f or #1a2d47
  assert.ok(!content.includes('#0e1c2f'), 'Should not contain hardcoded hex #0e1c2f');
  assert.ok(!content.includes('#1a2d47'), 'Should not contain hardcoded hex #1a2d47');
  assert.ok(content.includes('bg-primary-container'), 'Should use semantic bg-primary-container token');
});
```

- [x] **Step 2: Run test to verify it fails**

Run: `node --import tsx --test web/src/services/__tests__/header.test.ts` (or `npm --prefix web test`)  
Expected: FAIL with "Should not contain hardcoded hex #0e1c2f".

- [x] **Step 3: Refactor `Header.tsx` to use semantic theme tokens and compact horizontal layout**

In `web/src/components/ui/Header.tsx`:
- Replace `flex-col sm:flex-row ... pb-6` with `h-16 flex items-center justify-between gap-4 pb-4 border-b border-surface-container`.
- Replace `#0e1c2f` logo container with `bg-primary-container text-on-primary shadow-xs`.
- Replace action buttons with semantic tokens:
  - On login (`isLogin`): `<Link href="/signup" className="min-h-[44px] px-4 py-2 rounded-full bg-primary-container text-on-primary hover:bg-primary-container/90 font-body-sm font-semibold transition-all flex items-center gap-1.5 shadow-xs hover:shadow-md no-underline"><span>Criar conta</span><span className="material-symbols-outlined text-[16px]">arrow_forward</span></Link>`
  - On signup (`!isLogin`): `<Link href="/login" className="min-h-[44px] px-4 py-2 rounded-full bg-primary-container text-on-primary hover:bg-primary-container/90 font-body-sm font-semibold transition-all flex items-center gap-1.5 shadow-xs hover:shadow-md no-underline"><span>Já tem conta? Entrar</span><span className="material-symbols-outlined text-[16px]">login</span></Link>`

- [x] **Step 4: Run test to verify it passes**

Run: `node --import tsx --test web/src/services/__tests__/header.test.ts`  
Expected: PASS.

- [x] **Step 5: Commit changes**

```bash
git add web/src/components/ui/Header.tsx web/src/services/__tests__/header.test.ts
git commit -m "refactor(web): modernize Header with semantic tokens and compact single-row layout"
```

---

### Task 3: Streamline Login Page by Removing Redundant Actions and Clutter

**Files:**
- Modify: `web/src/app/login/page.tsx:105-155, 320-355`
- Test: `web/src/services/__tests__/loginStreamlining.test.ts`

**Interfaces:**
- Consumes: `Header`, `CpfInput`, `passwordToggle`.
- Produces: Focused, uncluttered authentication screen with zero redundant links.

- [ ] **Step 1: Write the failing unit test for Login streamlining**

```typescript
// web/src/services/__tests__/loginStreamlining.test.ts
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

test('login/page.tsx removes redundant breadcrumb and duplicate footer exit links', () => {
  const loginPath = path.resolve(process.cwd(), 'src/app/login/page.tsx');
  const content = fs.readFileSync(loginPath, 'utf-8');

  // Breadcrumbs should be removed
  assert.ok(!content.includes('Navegação estrutural'), 'Should not contain breadcrumbs nav');
  // Redundant "Voltar para a página inicial" below card should be removed
  assert.ok(!content.includes('Voltar para a página inicial'), 'Should not contain duplicate home link at bottom');
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --import tsx --test web/src/services/__tests__/loginStreamlining.test.ts`  
Expected: FAIL with "Should not contain breadcrumbs nav".

- [ ] **Step 3: Remove redundant elements in `web/src/app/login/page.tsx`**

In `web/src/app/login/page.tsx`:
1. Remove the `<nav aria-label="Navegação estrutural" ...>...</nav>` breadcrumbs block (lines ~108-121).
2. Remove the duplicate security pill inside the form card header (lines ~133-141): `<div className="flex items-center gap-1.5 px-3 py-1 bg-surface-container-low ...">Ambiente Seguro 256-bit</div>`. Keep only the clean title and description.
3. Remove the redundant footer section below the form card (lines ~325-350) containing `"Ainda não é cliente? Abra sua conta gratuita"` and `"Voltar para a página inicial"` (the action is now cleanly and prominently housed in the top header).
4. Replace any remaining `#0e1c2f` on the submit button with `bg-primary-container text-on-primary hover:bg-primary-container/90`.

- [ ] **Step 4: Run test to verify it passes**

Run: `node --import tsx --test web/src/services/__tests__/loginStreamlining.test.ts`  
Expected: PASS.

- [ ] **Step 5: Commit changes**

```bash
git add web/src/app/login/page.tsx web/src/services/__tests__/loginStreamlining.test.ts
git commit -m "refactor(web): streamline login screen by removing redundant breadcrumbs and links"
```

---

### Task 4: Streamline Signup Page and Form by Removing Redundant Actions

**Files:**
- Modify: `web/src/app/signup/page.tsx:20-40`
- Modify: `web/src/components/forms/OpenAccountForm.tsx:590-616`
- Test: `web/src/services/__tests__/signupStreamlining.test.ts`

**Interfaces:**
- Consumes: `Header`, `Stepper`, `OpenAccountForm`.
- Produces: Focused, 3-step progressive onboarding screen with zero duplicate exit paths.

- [ ] **Step 1: Write the failing unit test for Signup streamlining**

```typescript
// web/src/services/__tests__/signupStreamlining.test.ts
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

test('signup page and OpenAccountForm remove redundant breadcrumbs and duplicate bottom links', () => {
  const signupPath = path.resolve(process.cwd(), 'src/app/signup/page.tsx');
  const formPath = path.resolve(process.cwd(), 'src/components/forms/OpenAccountForm.tsx');

  const signupContent = fs.readFileSync(signupPath, 'utf-8');
  const formContent = fs.readFileSync(formPath, 'utf-8');

  // Breadcrumbs should be removed
  assert.ok(!signupContent.includes('Navegação estrutural'), 'Should not contain breadcrumbs nav');
  // Redundant bottom links in OpenAccountForm should be removed
  assert.ok(!formContent.includes('Já possui uma conta?'), 'Should not contain duplicate login prompt at bottom');
  assert.ok(!formContent.includes('Voltar para a página inicial'), 'Should not contain duplicate home link at bottom');
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --import tsx --test web/src/services/__tests__/signupStreamlining.test.ts`  
Expected: FAIL with "Should not contain breadcrumbs nav".

- [ ] **Step 3: Remove redundant elements in `signup/page.tsx` and `OpenAccountForm.tsx`**

In `web/src/app/signup/page.tsx`:
- Remove `<nav aria-label="Navegação estrutural" ...>...</nav>` breadcrumbs block (lines ~22-35).
- Allow the `<Stepper />` to sit cleanly below the `<Header />` with balanced spacing.

In `web/src/components/forms/OpenAccountForm.tsx`:
- Remove the bottom clutter div (lines ~594-614) containing:
  `Já possui uma conta? Faça login` and `Voltar para a página inicial`.
- Replace any hardcoded `#0e1c2f` buttons with `bg-primary-container text-on-primary hover:bg-primary-container/90`.

- [ ] **Step 4: Run test to verify it passes**

Run: `node --import tsx --test web/src/services/__tests__/signupStreamlining.test.ts`  
Expected: PASS.

- [ ] **Step 5: Commit changes**

```bash
git add web/src/app/signup/page.tsx web/src/components/forms/OpenAccountForm.tsx web/src/services/__tests__/signupStreamlining.test.ts
git commit -m "refactor(web): streamline signup flow by removing redundant breadcrumbs and duplicate links"
```

---

### Task 5: Complete Test Suite & Next.js Production Build Verification

**Files:**
- Test: All tests in `web/src/services/__tests__/*.test.ts`
- Verification: Next.js build compilation (`npm run build`)

- [ ] **Step 1: Execute all unit tests**

Run: `npm --prefix web test`  
Expected: All tests pass with zero failures.

- [ ] **Step 2: Execute Next.js build check**

Run: `npm --prefix web run build`  
Expected: Build compiles successfully with exit code 0.

- [ ] **Step 3: Commit final status**

```bash
git add -A
git commit -m "chore(web): verify streamlined auth and onboarding layouts pass all tests and builds"
```
