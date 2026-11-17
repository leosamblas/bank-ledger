# Frontend Layout & Usability Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement comprehensive UX, layout, and accessibility improvements across the Nexus Bank Web Banking interface (`web/`), adhering to `ui-ux-pro-max` guidelines (WCAG 2.2 AA, touch targets, progressive disclosure, balance privacy, and mobile navigation).

**Architecture:** Enhances the existing Next.js 16 / React 19 / Tailwind CSS v4 frontend with modular client components (accessible Toasts, mobile BottomNav, balance privacy toggle, copy button, and skeleton shimmer loaders) and refactors the account creation form into a progressive 3-step wizard synchronized with the visual Stepper.

**Tech Stack:** Next.js 16.4.3 (App Router), React 19, TypeScript 5, Tailwind CSS v4, Node.js Test Runner (`node:test`).

**Spec:** [`docs/superpowers/specs/2026-11-17-frontend-layout-ux-improvements-design.md`](file:///c:/Users/leosa/Documents/Projects/bank-ledger/docs/superpowers/specs/2026-11-17-frontend-layout-ux-improvements-design.md)

## Global Constraints
- Framework: Next.js 16 with React 19 App Router (use `"use client"` where state/hooks are required).
- Styling: Tailwind CSS v4 design tokens defined in `@theme` (`globals.css`). Zero arbitrary raw hex colors in components where theme tokens exist.
- Accessibility: Minimum 4.5:1 text contrast, visible focus rings on interactive elements, descriptive `aria-label` on all icon-only buttons, and minimum $44 \times 44$ px touch targets on mobile.
- Zero External Heavy Dependencies: Build lightweight, native React/TypeScript components without adding third-party UI component libraries (no Radix/MUI npm packages required).
- Tests: Maintain and expand unit tests using Node.js native test runner (`node:test` via `npm run test` or `npx tsx --test`).

## Review Focus
- Password Visibility & Form Context: Password inputs on both Login (`/login`) and Signup (`OpenAccountForm`) must default to `type="password"`, toggle to `type="text"` when clicking the eye button, display distinct `visibility` / `visibility_off` icons, provide dynamic `aria-label` ("Ocultar senha" / "Exibir senha"), and have touch target $\ge 44 \times 44$ px with proper input attributes (`autoComplete`, `inputMode`).
- Privacy Toggle: When balance privacy mode is enabled, all financial figures (available balance, blocked balance, and daily limits) must display masked dots (`••••••••`) while preserving exact formatting when revealed.
- Mobile Navigation: The mobile bottom navigation bar must only render on screens `< 768px` (`md:hidden`), must not obstruct scrollable content, and must respect `padding-bottom: env(safe-area-inset-bottom)`.
- Multi-step Stepper: In `OpenAccountForm`, the user cannot advance to Step 2 without a valid Name, CPF (Modulo 11), and Age $\ge 18$, and cannot advance to Step 3 without valid Email and Phone.
- Toast Feedback: Toasts must render with `role="status"` (or `role="alert"` for errors), auto-dismiss after 4 seconds, and provide an accessible close button.
- Performance: Eliminate redundant Google font `<link>` tags in `layout.tsx` to reduce network round-trips and layout shifts.

---

### Task 1: Optimize Fonts, Head Resources, and Global Accessibility Styles

**Files:**
- Modify: `web/src/app/layout.tsx:25-38`
- Modify: `web/src/app/globals.css:1-3, 186-207`
- Test: `web/src/services/__tests__/layout.test.ts`

**Interfaces:**
- Consumes: Google Fonts preconnect and Tailwind CSS v4 `@theme`.
- Produces: Cleaned HTML `<head>` with single Material Symbols import, focus-visible utility rings, and `prefers-reduced-motion` global support.

- [ ] **Step 1: Write the failing test for layout head deduplication**

```typescript
// web/src/services/__tests__/layout.test.ts
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

test('layout.tsx contains deduplicated font stylesheets and viewport configuration', () => {
  const layoutPath = path.resolve(process.cwd(), 'src/app/layout.tsx');
  const content = fs.readFileSync(layoutPath, 'utf-8');

  // Must not have duplicate Material Symbols stylesheets
  const matches = content.match(/fonts\.googleapis\.com\/css2\?family=Material\+Symbols\+Outlined/g) || [];
  assert.strictEqual(matches.length, 1, 'Should have exactly one Material Symbols font stylesheet link');
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --import tsx --test web/src/services/__tests__/layout.test.ts` (or `npm --prefix web test`)  
Expected: FAIL with "Should have exactly one Material Symbols font stylesheet link" (currently 2 links exist in `layout.tsx`).

- [ ] **Step 3: Deduplicate font link in `web/src/app/layout.tsx` and add accessibility utilities in `web/src/app/globals.css`**

In `web/src/app/layout.tsx`:
Remove the duplicate second `<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:wght,FILL@100..700,0..1&display=swap" />`.

In `web/src/app/globals.css`:
Add focus ring and reduced motion rules under `@layer base`:
```css
@media (prefers-reduced-motion: reduce) {
  *, ::before, ::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
    scroll-behavior: auto !important;
  }
}

:focus-visible {
  outline: 2px solid var(--color-secondary);
  outline-offset: 2px;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `node --import tsx --test web/src/services/__tests__/layout.test.ts`  
Expected: PASS.

- [ ] **Step 5: Commit changes**

```bash
git add web/src/app/layout.tsx web/src/app/globals.css web/src/services/__tests__/layout.test.ts
git commit -m "style(web): deduplicate font imports and add global accessibility focus/motion styles"
```

---

### Task 2: Enhanced Form Field Ergonomics and Password Visibility Toggles (`login/page.tsx` & `OpenAccountForm.tsx`)

**Files:**
- Create: `web/src/utils/formUtils.ts`
- Modify: `web/src/app/login/page.tsx:210-245`
- Modify: `web/src/components/forms/OpenAccountForm.tsx:25-35, 330-365`
- Test: `web/src/services/__tests__/passwordToggle.test.ts`

**Interfaces:**
- Consumes: Component state (`showPassword: boolean`, `showConfirmPassword: boolean`).
- Produces: `getPasswordInputType(isVisible: boolean): 'text' | 'password'` and `getPasswordToggleAriaLabel(fieldLabel: string, isVisible: boolean): string`.

- [ ] **Step 1: Write failing test for password toggle helper**

```typescript
// web/src/services/__tests__/passwordToggle.test.ts
import test from 'node:test';
import assert from 'node:assert/strict';

test('formUtils returns correct input type and accessible aria-labels', async () => {
  const { getPasswordInputType, getPasswordToggleAriaLabel } = await import('../../utils/formUtils');

  assert.strictEqual(getPasswordInputType(false), 'password');
  assert.strictEqual(getPasswordInputType(true), 'text');

  assert.strictEqual(getPasswordToggleAriaLabel('Senha de Acesso', false), 'Exibir Senha de Acesso');
  assert.strictEqual(getPasswordToggleAriaLabel('Senha de Acesso', true), 'Ocultar Senha de Acesso');
  assert.strictEqual(getPasswordToggleAriaLabel('Confirmar Senha', false), 'Exibir Confirmar Senha');
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --import tsx --test web/src/services/__tests__/passwordToggle.test.ts`  
Expected: FAIL with "Cannot find module '../../utils/formUtils'".

- [ ] **Step 3: Implement `web/src/utils/formUtils.ts` and integrate password toggles and field context**

Create `web/src/utils/formUtils.ts`:
```typescript
export function getPasswordInputType(isVisible: boolean): 'text' | 'password' {
  return isVisible ? 'text' : 'password';
}

export function getPasswordToggleAriaLabel(fieldLabel: string, isVisible: boolean): string {
  return isVisible ? `Ocultar ${fieldLabel}` : `Exibir ${fieldLabel}`;
}
```

In `web/src/app/login/page.tsx`:
- Use `getPasswordToggleAriaLabel('senha', showPassword)` and `aria-pressed={showPassword}` on the visibility toggle button.
- Ensure the button has a touch area of `h-11 w-11` (`min-h-[44px] min-w-[44px] flex items-center justify-center`).
- Add `spellCheck={false}`, `autoCorrect="off"`, `autoCapitalize="none"` to the password input.

In `web/src/components/forms/OpenAccountForm.tsx`:
- Add `showPassword` and `showConfirmPassword` state hooks (`useState(false)`).
- Add eye toggle buttons to both "Senha de Acesso" (`id="password"`) and "Confirmar Senha" (`id="confirmPassword"`) with `pr-12` padding.
- Add `autoComplete="name"`, `autoCapitalize="words"`, `autoCorrect="off"` to the Full Name field.
- Add `autoComplete="bday"` and `max` date to the Birth Date field.
- Add `autoComplete="email"`, `inputMode="email"`, `autoCapitalize="none"`, `spellCheck={false}` to the Email field.
- Add `autoComplete="tel"`, `inputMode="tel"` to the Phone field.
- Add `autoComplete="new-password"`, `spellCheck={false}`, `autoCorrect="off"`, `autoCapitalize="none"` to both password fields.

- [ ] **Step 4: Run test to verify it passes**

Run: `node --import tsx --test web/src/services/__tests__/passwordToggle.test.ts`  
Expected: PASS.

- [ ] **Step 5: Commit changes**

```bash
git add web/src/utils/formUtils.ts web/src/app/login/page.tsx web/src/components/forms/OpenAccountForm.tsx web/src/services/__tests__/passwordToggle.test.ts
git commit -m "feat(web): add accessible password visibility toggles and contextual input attributes"
```

---

### Task 3: Implement Accessible Toast System and Eliminate `window.alert()`

**Files:**
- Create: `web/src/components/ui/Toast.tsx`
- Modify: `web/src/components/banking/BankingHeader.tsx:85-105`
- Test: `web/src/services/__tests__/toast.test.ts`

**Interfaces:**
- Consumes: React Context / Event bus.
- Produces: `ToastProvider`, `useToast()`, `showToast(message, type)`.

- [ ] **Step 1: Write the failing unit test for Toast manager**

```typescript
// web/src/services/__tests__/toast.test.ts
import test from 'node:test';
import assert from 'node:assert/strict';

test('Toast state manager adds and auto-dismisses toast notifications', async () => {
  // Test standalone toast event bus
  const { toastManager } = await import('../../components/ui/Toast');
  
  let receivedToast: { id: string; message: string; type: string } | null = null;
  const unsubscribe = toastManager.subscribe((toast) => {
    receivedToast = toast;
  });

  toastManager.notify('Teste de notificação segura', 'info');

  assert.ok(receivedToast);
  assert.strictEqual(receivedToast.message, 'Teste de notificação segura');
  assert.strictEqual(receivedToast.type, 'info');

  unsubscribe();
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --import tsx --test web/src/services/__tests__/toast.test.ts`  
Expected: FAIL with "Cannot find module '../../components/ui/Toast'".

- [ ] **Step 3: Implement `web/src/components/ui/Toast.tsx`**

Create `web/src/components/ui/Toast.tsx`:
Export `toastManager`, `useToast()`, `ToastContainer` with:
- Types: `'success' | 'info' | 'warning' | 'error'`
- `role="status"` for info/success, `role="alert"` for error.
- Auto-dismiss after 4000ms.
- Close button with `aria-label="Fechar notificação"`.
- Clean styling using theme tokens (`bg-surface-container-lowest`, `border-surface-container`, shadow).

- [ ] **Step 4: Update `BankingHeader.tsx` to use `toastManager.notify` instead of `alert()`**

Replace `onClick={() => alert('Você não possui novas notificações no momento.')}` with `onClick={() => toastManager.notify('Você não possui novas notificações no momento.', 'info')}` and embed `<ToastContainer />`.

- [ ] **Step 5: Run test to verify it passes**

Run: `node --import tsx --test web/src/services/__tests__/toast.test.ts`  
Expected: PASS.

- [ ] **Step 6: Commit changes**

```bash
git add web/src/components/ui/Toast.tsx web/src/components/banking/BankingHeader.tsx web/src/services/__tests__/toast.test.ts
git commit -m "feat(web): add accessible Toast notification system and eliminate window.alert"
```

---

### Task 4: Implement Balance Privacy Mode (Ocultar/Exibir Saldo)

**Files:**
- Create: `web/src/components/banking/PrivacyToggle.tsx`
- Modify: `web/src/app/dashboard/page.tsx:28-60, 100-140`
- Test: `web/src/services/__tests__/privacyToggle.test.ts`

**Interfaces:**
- Consumes: `localStorage` key `'nexus_balance_hidden'`.
- Produces: `isPrivate: boolean`, `togglePrivacy()`, `maskValue(formattedValue: string): string`.

- [ ] **Step 1: Write failing test for privacy toggle utility**

```typescript
// web/src/services/__tests__/privacyToggle.test.ts
import test from 'node:test';
import assert from 'node:assert/strict';

test('maskFinancialValue masks monetary string when private is true', async () => {
  const { maskFinancialValue } = await import('../../components/banking/PrivacyToggle');

  const visible = 'R$ 15.420,50';
  assert.strictEqual(maskFinancialValue(visible, false), 'R$ 15.420,50');
  assert.strictEqual(maskFinancialValue(visible, true), 'R$ ••••••••');
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --import tsx --test web/src/services/__tests__/privacyToggle.test.ts`  
Expected: FAIL with "Cannot find module '../../components/banking/PrivacyToggle'".

- [ ] **Step 3: Implement `PrivacyToggle.tsx` and integrate into `DashboardPage`**

Create `web/src/components/banking/PrivacyToggle.tsx`:
- Export `maskFinancialValue(val: string, isPrivate: boolean): string`.
- Export `PrivacyToggle` component with eye icon (`visibility` / `visibility_off`), `aria-label={isPrivate ? "Exibir saldos" : "Ocultar saldos"}`, and `aria-pressed={isPrivate}`.
- Store preference in `localStorage`.

In `web/src/app/dashboard/page.tsx`:
- Import `PrivacyToggle` and `maskFinancialValue`.
- Place `PrivacyToggle` alongside the hero balance title.
- Wrap `formatCentsToBrl(balance.availableCents)`, `formatCentsToBrl(balance.blockedCents)`, etc., with `maskFinancialValue(..., isPrivate)`.

- [ ] **Step 4: Run test to verify it passes**

Run: `node --import tsx --test web/src/services/__tests__/privacyToggle.test.ts`  
Expected: PASS.

- [ ] **Step 5: Commit changes**

```bash
git add web/src/components/banking/PrivacyToggle.tsx web/src/app/dashboard/page.tsx web/src/services/__tests__/privacyToggle.test.ts
git commit -m "feat(web): add balance privacy toggle with localStorage persistence"
```

---

### Task 5: Implement Mobile Bottom Navigation Bar for Web Banking

**Files:**
- Create: `web/src/components/banking/BottomNav.tsx`
- Modify: `web/src/app/dashboard/layout.tsx:15-25`
- Test: `web/src/services/__tests__/bottomNav.test.ts`

**Interfaces:**
- Consumes: `usePathname()` from `'next/navigation'`.
- Produces: Responsive bottom bar for mobile screens (`md:hidden`) with 4 core navigation items.

- [ ] **Step 1: Write failing test for BottomNav component structure**

```typescript
// web/src/services/__tests__/bottomNav.test.ts
import test from 'node:test';
import assert from 'node:assert/strict';

test('BottomNav items definitions match core Web Banking routes', async () => {
  const { BOTTOM_NAV_ITEMS } = await import('../../components/banking/BottomNav');

  assert.strictEqual(BOTTOM_NAV_ITEMS.length, 4);
  const hrefs = BOTTOM_NAV_ITEMS.map((i) => i.href);
  assert.ok(hrefs.includes('/dashboard'));
  assert.ok(hrefs.includes('/dashboard/transfer'));
  assert.ok(hrefs.includes('/dashboard/operations'));
  assert.ok(hrefs.includes('/dashboard/statement'));
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --import tsx --test web/src/services/__tests__/bottomNav.test.ts`  
Expected: FAIL with "Cannot find module '../../components/banking/BottomNav'".

- [ ] **Step 3: Implement `web/src/components/banking/BottomNav.tsx` and integrate into `DashboardLayout.tsx`**

Create `web/src/components/banking/BottomNav.tsx`:
- Export `BOTTOM_NAV_ITEMS = [{ label: 'Início', href: '/dashboard', icon: 'dashboard' }, ...]`
- Render fixed bar at `bottom-0 left-0 right-0 z-40 bg-surface-container-lowest/95 backdrop-blur-md border-t border-surface-container md:hidden pb-[env(safe-area-inset-bottom)]`.
- Minimum tap targets: `min-h-[48px] min-w-[48px]`.
- Active item indicator with `text-primary font-semibold` and subtle emerald accent dot.

In `web/src/app/dashboard/layout.tsx`:
- Import `BottomNav` and render it below `{children}` inside `<AuthGuard>`.
- Add `pb-20 md:pb-12` to `<main>` to prevent fixed bottom bar from covering page content.

- [ ] **Step 4: Run test to verify it passes**

Run: `node --import tsx --test web/src/services/__tests__/bottomNav.test.ts`  
Expected: PASS.

- [ ] **Step 5: Commit changes**

```bash
git add web/src/components/banking/BottomNav.tsx web/src/app/dashboard/layout.tsx web/src/services/__tests__/bottomNav.test.ts
git commit -m "feat(web): add ergonomic mobile bottom navigation bar for web banking"
```

---

### Task 6: Progressive Disclosure Stepper in Account Creation (`/signup`)

**Files:**
- Modify: `web/src/components/ui/Stepper.tsx:1-52`
- Modify: `web/src/components/forms/OpenAccountForm.tsx:1-120`
- Modify: `web/src/app/signup/page.tsx:30-45`
- Test: `web/src/services/__tests__/stepper.test.ts`

**Interfaces:**
- Consumes: Step state (`currentStep: 1 | 2 | 3`).
- Produces: Interactive Stepper synchronized with form sections, eliminating single-card cognitive overload.

- [ ] **Step 1: Write failing test for Stepper component props**

```typescript
// web/src/services/__tests__/stepper.test.ts
import test from 'node:test';
import assert from 'node:assert/strict';

test('Stepper step computation returns correct active and completed states', async () => {
  const { computeStepState } = await import('../../components/ui/Stepper');

  assert.strictEqual(computeStepState(1, 1), 'active');
  assert.strictEqual(computeStepState(2, 1), 'upcoming');
  assert.strictEqual(computeStepState(1, 2), 'completed');
  assert.strictEqual(computeStepState(2, 3), 'completed');
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --import tsx --test web/src/services/__tests__/stepper.test.ts`  
Expected: FAIL with "computeStepState is not a function".

- [ ] **Step 3: Update `Stepper.tsx` and refactor `OpenAccountForm.tsx` into 3 progressive steps**

In `web/src/components/ui/Stepper.tsx`:
- Export `computeStepState(stepNumber: number, currentStep: number): 'completed' | 'active' | 'upcoming'`.
- Accept props: `currentStep: number`, `onStepClick?: (step: number) => void`.
- Render checkmark icon for `completed`, active badge for `active`, and muted styling for `upcoming`.

In `web/src/components/forms/OpenAccountForm.tsx`:
- Add `currentStep` state (`1 | 2 | 3`) and notify parent (`onStepChange`).
- **Passo 1 (Dados Pessoais):** Nome Completo, CPF, Data de Nascimento. CTA: "Avançar para Contato". Disabled se Nome, CPF ou Data forem inválidos.
- **Passo 2 (Contato):** E-mail, Celular. CTA: "Voltar" e "Avançar para Segurança". Disabled se E-mail ou Celular forem vazios.
- **Passo 3 (Segurança):** Senha, Confirmação de Senha, Medidor de Força e Termos. CTA: "Voltar" e "Finalizar Abertura".

In `web/src/app/signup/page.tsx`:
- Pass `currentStep` and `setCurrentStep` to both `<Stepper />` and `<OpenAccountForm />`.

- [ ] **Step 4: Run test to verify it passes**

Run: `node --import tsx --test web/src/services/__tests__/stepper.test.ts`  
Expected: PASS.

- [ ] **Step 5: Commit changes**

```bash
git add web/src/components/ui/Stepper.tsx web/src/components/forms/OpenAccountForm.tsx web/src/app/signup/page.tsx web/src/services/__tests__/stepper.test.ts
git commit -m "feat(web): implement interactive progressive stepper in account onboarding flow"
```

---

### Task 7: Implement Accessible Copy Button (1-Clique) for Identifiers

**Files:**
- Create: `web/src/components/ui/CopyButton.tsx`
- Modify: `web/src/app/dashboard/statement/page.tsx:280-320`
- Modify: `web/src/app/dashboard/transfer/page.tsx:180-220`
- Test: `web/src/services/__tests__/copyButton.test.ts`

**Interfaces:**
- Consumes: `navigator.clipboard.writeText(text)`.
- Produces: `CopyButton` component with icon, tooltip "Copiado!", and `aria-label`.

- [ ] **Step 1: Write failing test for CopyButton helper**

```typescript
// web/src/services/__tests__/copyButton.test.ts
import test from 'node:test';
import assert from 'node:assert/strict';

test('CopyButton helper returns formatted aria-label', async () => {
  const { getCopyAriaLabel } = await import('../../components/ui/CopyButton');

  assert.strictEqual(getCopyAriaLabel('ID da Transação'), 'Copiar ID da Transação');
  assert.strictEqual(getCopyAriaLabel('Chave PIX'), 'Copiar Chave PIX');
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --import tsx --test web/src/services/__tests__/copyButton.test.ts`  
Expected: FAIL with "Cannot find module '../../components/ui/CopyButton'".

- [ ] **Step 3: Implement `CopyButton.tsx` and integrate into Statement & Transfer**

Create `web/src/components/ui/CopyButton.tsx`:
- Export `getCopyAriaLabel(label: string): string`.
- Export `CopyButton({ textToCopy, label }: { textToCopy: string; label: string })`.
- Icon switches to checkmark with emerald color for 2000ms upon success, announcing "Copiado para a área de transferência" via `aria-live="polite"`.

Integrate into:
- `StatementPage`: modal de recibo (copiar ID da transação e código de autenticação).
- `TransferPage`: comprovante de transferência concluída.

- [ ] **Step 4: Run test to verify it passes**

Run: `node --import tsx --test web/src/services/__tests__/copyButton.test.ts`  
Expected: PASS.

- [ ] **Step 5: Commit changes**

```bash
git add web/src/components/ui/CopyButton.tsx web/src/app/dashboard/statement/page.tsx web/src/app/dashboard/transfer/page.tsx web/src/services/__tests__/copyButton.test.ts
git commit -m "feat(web): add accessible 1-click CopyButton for transaction IDs and account data"
```

---

### Task 8: Implement Skeleton Shimmer Loaders for Async States

**Files:**
- Create: `web/src/components/ui/Skeleton.tsx`
- Modify: `web/src/app/dashboard/page.tsx:100-150`
- Modify: `web/src/app/dashboard/statement/page.tsx:100-140`
- Test: `web/src/services/__tests__/skeleton.test.ts`

**Interfaces:**
- Consumes: Tailwind `animate-pulse` with `bg-surface-container`.
- Produces: `Skeleton`, `BalanceCardSkeleton`, `StatementRowSkeleton`.

- [ ] **Step 1: Write failing test for Skeleton utility**

```typescript
// web/src/services/__tests__/skeleton.test.ts
import test from 'node:test';
import assert from 'node:assert/strict';

test('Skeleton renders with aria-busy and accessible loading attributes', async () => {
  const { getSkeletonAriaAttributes } = await import('../../components/ui/Skeleton');

  const attrs = getSkeletonAriaAttributes('Carregando informações financeiras');
  assert.strictEqual(attrs['aria-busy'], true);
  assert.strictEqual(attrs['aria-label'], 'Carregando informações financeiras');
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --import tsx --test web/src/services/__tests__/skeleton.test.ts`  
Expected: FAIL with "Cannot find module '../../components/ui/Skeleton'".

- [ ] **Step 3: Implement `Skeleton.tsx` and integrate into Dashboard and Statement**

Create `web/src/components/ui/Skeleton.tsx`:
- Export `getSkeletonAriaAttributes(label?: string)`.
- Export `Skeleton({ className })`.
- Export `BalanceCardSkeleton()` and `StatementRowSkeleton()`.

In `DashboardPage`:
- While `isLoading && !account.id`: display `BalanceCardSkeleton` instead of blank space.

In `StatementPage`:
- While loading statement entries: display 4 `StatementRowSkeleton` placeholders.

- [ ] **Step 4: Run test to verify it passes**

Run: `node --import tsx --test web/src/services/__tests__/skeleton.test.ts`  
Expected: PASS.

- [ ] **Step 5: Commit changes**

```bash
git add web/src/components/ui/Skeleton.tsx web/src/app/dashboard/page.tsx web/src/app/dashboard/statement/page.tsx web/src/services/__tests__/skeleton.test.ts
git commit -m "feat(web): add accessible skeleton shimmer loaders for banking async states"
```

---

### Task 9: Verification of Full Test Suite & Next.js Build

**Files:**
- Test: All unit tests in `web/src/services/__tests__/*.test.ts`
- Verification: Next.js build compilation (`npm run build`)

- [ ] **Step 1: Execute all unit tests**

Run: `npm --prefix web test`  
Expected: All tests pass with zero failures.

- [ ] **Step 2: Execute Next.js build check**

Run: `npm --prefix web run build`  
Expected: Build finishes with exit code 0 and valid static/dynamic routes.

- [ ] **Step 3: Commit final verification status**

```bash
git add -A
git commit -m "chore(web): verify test suite and production build compilation"
```
