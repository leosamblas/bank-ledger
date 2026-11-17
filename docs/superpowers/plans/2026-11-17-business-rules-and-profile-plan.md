# Business Rules and Profile Overhaul Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the major business rules update for backend microservices and frontend application, including daily transfer limit governance, transfer by CPF, unmasked recipient confirmation, canonical banking protocols, print and consolidated statement actions, export reports (CSV/OFX), and user profile/address management.

**Architecture:** Distributed banking architecture using Spring Boot 4 / Java 25 microservices (`account-service`, `transaction-service`, `query-service`, `ledger-service`) communicating via Kafka transactional outbox/inbox, and a Next.js 16 / React 19 frontend consuming REST APIs.

**Tech Stack:** Java 25, Spring Boot 4.1.2, Spring Security, PostgreSQL, Flyway, Apache Kafka, JUnit 5, AssertJ, Mockito, Next.js 16.4.6, React 19, Tailwind CSS v4, Lucide React, Vitest / Testing Library.

**Spec:** [`docs/superpowers/specs/2026-11-17-business-rules-and-profile-design.md`](file:///c:/Users/leosa/Documents/Projects/bank-ledger/docs/superpowers/specs/2026-11-17-business-rules-and-profile-design.md)  
**Separation Reference:** [`docs/business-rules-overhaul-separation.md`](file:///c:/Users/leosa/Documents/Projects/bank-ledger/docs/business-rules-overhaul-separation.md)

## Global Constraints

- **Language & Runtime:** Backend requires Java 25 with preview/standard features; Frontend requires Next.js 16.4.6 and React 19.
- **Branch:** All changes must remain on branch `feature/business-rules-overhaul`.
- **TDD Requirement:** Each task must write a failing test first, confirm failure, implement minimal solution, confirm green, and commit.
- **Accessibility:** Frontend components must satisfy WCAG AAA standards ($44 \times 44\text{ px}$ clickable targets, semantic contrast, aria attributes).
- **Security:** Maintain OWASP protections; endpoints must respect authentication and authorization.

## Review Focus

1. **Daily Limit Computation Boundary:** Ensure daily limit calculates today's transactions in local civil day (UTC-3 / Brazilian standard time) without time-zone drift.
2. **Protocol Generation Uniqueness:** Protocol format `PRT-YYYYMMDD-XXXXXX` must not collide under concurrent requests.
3. **CPF Validation & Masking:** Transfer lookup by CPF must accept both clean (11 digits) and formatted (`000.000.000-00`) input.
4. **Print Isolation:** Printing receipts must isolate only the receipt canvas via `@media print`, suppressing navigation, buttons, and app shell.
5. **Address Optionality:** Address fields must be backward compatible with existing customer records without causing null pointer exceptions.

---

## Backend Tasks

### Task 1: `account-service` Flyway Migration and Customer Address Model

**Files:**
- Create: `services/account-service/src/main/resources/db/migration/V3__add_customer_address.sql`
- Create: `services/account-service/src/main/java/com/bankledger/account/domain/vo/Address.java`
- Modify: `services/account-service/src/main/java/com/bankledger/account/adapters/out/persistence/entity/CustomerEntity.java`
- Modify: `services/account-service/src/main/java/com/bankledger/account/domain/model/Customer.java`
- Test: `services/account-service/src/test/java/com/bankledger/account/domain/CustomerAddressTest.java`

- [ ] **Step 1:** Write failing unit test `CustomerAddressTest.java` verifying that `Customer` and `CustomerEntity` support the `Address` value object (street, number, complement, neighborhood, city, state, postalCode).
- [ ] **Step 2:** Run `./gradlew :account-service:test --tests com.bankledger.account.domain.CustomerAddressTest` to verify it fails compilation/assertion.
- [ ] **Step 3:** Implement `Address.java` VO, update `Customer.java`, `CustomerEntity.java`, and create migration `V3__add_customer_address.sql`.
- [ ] **Step 4:** Run `./gradlew :account-service:test --tests com.bankledger.account.domain.CustomerAddressTest` to verify it passes.
- [ ] **Step 5:** Commit: `git commit -m "feat(account): add customer address model and V3 database migration"`.

---

### Task 2: `account-service` Profile Update Endpoint and Enhanced Account Response

**Files:**
- Create: `services/account-service/src/main/java/com/bankledger/account/application/port/in/UpdateProfileCommand.java`
- Create: `services/account-service/src/main/java/com/bankledger/account/application/port/in/UpdateProfileUseCase.java`
- Create: `services/account-service/src/main/java/com/bankledger/account/adapters/in/rest/dto/UpdateProfileRequest.java`
- Modify: `services/account-service/src/main/java/com/bankledger/account/adapters/in/rest/AccountController.java`
- Modify: `services/account-service/src/main/java/com/bankledger/account/adapters/in/rest/dto/AccountResponse.java`
- Modify: `services/account-service/src/main/java/com/bankledger/account/application/service/AccountQueryService.java`
- Test: `services/account-service/src/test/java/com/bankledger/account/adapters/in/rest/AccountProfileControllerTest.java`

- [ ] **Step 1:** Write failing unit/slice test `AccountProfileControllerTest.java` testing `PUT /api/v1/accounts/me/profile` and verifying that `GET /api/v1/accounts/me` returns address fields.
- [ ] **Step 2:** Run `./gradlew :account-service:test --tests com.bankledger.account.adapters.in.rest.AccountProfileControllerTest` to verify failure.
- [ ] **Step 3:** Implement `UpdateProfileRequest`, `UpdateProfileUseCase`, update `AccountController` with `PUT /me/profile`, and include address in `AccountResponse`.
- [ ] **Step 4:** Run `./gradlew :account-service:test --tests com.bankledger.account.adapters.in.rest.AccountProfileControllerTest` to verify passing.
- [ ] **Step 5:** Commit: `git commit -m "feat(account): add profile update endpoint and address fields in account response"`.

---

### Task 3: `account-service` Unmasked Recipient Lookup by Account and CPF

**Files:**
- Modify: `services/account-service/src/main/java/com/bankledger/account/adapters/in/rest/AccountController.java`
- Modify: `services/account-service/src/main/java/com/bankledger/account/adapters/in/rest/dto/AccountLookupResponse.java`
- Modify: `services/account-service/src/main/java/com/bankledger/account/application/service/AccountQueryService.java`
- Modify: `services/account-service/src/main/java/com/bankledger/account/application/port/in/LookupAccountUseCase.java`
- Test: `services/account-service/src/test/java/com/bankledger/account/application/AccountLookupByCpfTest.java`

- [ ] **Step 1:** Write failing test `AccountLookupByCpfTest.java` testing lookup by CPF (`52998224725`) and verifying that `AccountLookupResponse` returns unmasked `holderName`, `cpf`, `branch`, `number`, `digit`, and `accountId`.
- [ ] **Step 2:** Run `./gradlew :account-service:test --tests com.bankledger.account.application.AccountLookupByCpfTest` to verify failure.
- [ ] **Step 3:** Implement lookup by CPF in `AccountQueryService`, update `AccountController.lookupAccount` to accept optional `cpf` param or `agency+number`, and return unmasked recipient data.
- [ ] **Step 4:** Run `./gradlew :account-service:test --tests com.bankledger.account.application.AccountLookupByCpfTest` to verify passing.
- [ ] **Step 5:** Commit: `git commit -m "feat(account): support unmasked recipient lookup by account or cpf"`.

---

### Task 4: `transaction-service` Canonical Banking Protocol & Daily Limit Validation

**Files:**
- Create: `services/transaction-service/src/main/java/com/bankledger/transaction/domain/service/BankingProtocolGenerator.java`
- Modify: `services/transaction-service/src/main/java/com/bankledger/transaction/domain/model/FinancialTransaction.java`
- Modify: `services/transaction-service/src/main/java/com/bankledger/transaction/application/service/CreateTransferService.java`
- Modify: `services/transaction-service/src/main/java/com/bankledger/transaction/adapters/in/rest/dto/TransactionResponse.java`
- Test: `services/transaction-service/src/test/java/com/bankledger/transaction/domain/BankingProtocolGeneratorTest.java`
- Test: `services/transaction-service/src/test/java/com/bankledger/transaction/application/CreateTransferWithLimitTest.java`

- [ ] **Step 1:** Write failing test `BankingProtocolGeneratorTest.java` asserting protocol format `PRT-YYYYMMDD-XXXXXX`.
- [ ] **Step 2:** Write failing test `CreateTransferWithLimitTest.java` verifying that transfers generate a protocol and reject amounts exceeding remaining limit.
- [ ] **Step 3:** Implement `BankingProtocolGenerator`, add `protocolNumber` to `FinancialTransaction`, update `CreateTransferService` and `TransactionResponse`.
- [ ] **Step 4:** Run `./gradlew :transaction-service:test` to verify tests pass.
- [ ] **Step 5:** Commit: `git commit -m "feat(transaction): add canonical banking protocol generator and daily transfer validation"`.

---

### Task 5: `query-service` Daily Used Transfer Calculation & Statement Protocol Projections

**Files:**
- Modify: `services/query-service/src/main/java/com/bankledger/query/adapters/in/rest/QueryController.java`
- Modify: `services/query-service/src/main/java/com/bankledger/query/adapters/in/rest/dto/BalanceResponse.java`
- Modify: `services/query-service/src/main/java/com/bankledger/query/adapters/in/rest/dto/StatementItemResponse.java`
- Modify: `services/query-service/src/main/java/com/bankledger/query/adapters/out/persistence/StatementEntryEntity.java`
- Modify: `services/query-service/src/main/java/com/bankledger/query/adapters/out/persistence/StatementEntryRepository.java`
- Test: `services/query-service/src/test/java/com/bankledger/query/application/DailyLimitAndStatementProtocolTest.java`

- [ ] **Step 1:** Write failing test `DailyLimitAndStatementProtocolTest.java` verifying that `getBalance` calculates `usedTodayCents` from today's debit transfers and `getStatement` returns `protocolNumber` and unmasked counterparty info.
- [ ] **Step 2:** Run `./gradlew :query-service:test --tests com.bankledger.query.application.DailyLimitAndStatementProtocolTest` to verify failure.
- [ ] **Step 3:** Add query method `sumAmountByAccountIdAndCategoryAndPostedAtAfter` in `StatementEntryRepository`, calculate `usedTodayCents` in `QueryController`, and expose `protocolNumber` in `StatementItemResponse`.
- [ ] **Step 4:** Run `./gradlew :query-service:test --tests com.bankledger.query.application.DailyLimitAndStatementProtocolTest` to verify passing.
- [ ] **Step 5:** Commit: `git commit -m "feat(query): calculate dynamic usedTodayCents and project protocolNumber in statement"`.

---

## Frontend Tasks

### Task 6: Frontend Types, Services and Banking Hook Updates

**Files:**
- Modify: `web/src/services/types.ts`
- Modify: `web/src/services/accountService.ts`
- Modify: `web/src/services/queryService.ts`
- Modify: `web/src/hooks/useBanking.ts`
- Test: `web/src/__tests__/services/bankingServices.test.ts`

- [ ] **Step 1:** Write failing test in `web/src/__tests__/services/bankingServices.test.ts` for lookup by CPF, profile update, and enriched statement types.
- [ ] **Step 2:** Run `npm --prefix web test` to confirm test fails.
- [ ] **Step 3:** Update `types.ts` with Address, CustomerProfile, AccountLookupResponse (unmasked), and StatementEntry (with protocolNumber); update `accountService.ts` with `updateProfile` and `lookupAccount({agency, number, cpf})`.
- [ ] **Step 4:** Run `npm --prefix web test` to confirm test passes.
- [ ] **Step 5:** Commit: `git commit -m "feat(web): update banking types, account service and hooks for profile and lookup"`.

---

### Task 7: Transfer Screen Overhaul (Transfer by CPF, Recipient Card, Reactive Limit Bar)

**Files:**
- Modify: `web/src/app/dashboard/transfer/page.tsx`
- Test: `web/src/__tests__/dashboard/TransferPage.test.tsx`

- [ ] **Step 1:** Write failing tests in `TransferPage.test.tsx` verifying:
  - Tab switching between "Agência e Conta" and "Por CPF".
  - CPF auto-masking `000.000.000-00`.
  - Recipient verified card showing unmasked Name and CPF.
  - Live reactive limit progress bar showing available limit deducting entered amount.
  - Submit button disabled when entered amount > available limit.
- [ ] **Step 2:** Run `npm --prefix web test -- TransferPage` to verify failure.
- [ ] **Step 3:** Implement tab switcher, CPF input with mask, unmasked recipient display, and dynamic limit progress bar in `web/src/app/dashboard/transfer/page.tsx`.
- [ ] **Step 4:** Run `npm --prefix web test -- TransferPage` to verify all tests pass.
- [ ] **Step 5:** Commit: `git commit -m "feat(web): enhance transfer page with cpf mode, unmasked recipient and dynamic limit bar"`.

---

### Task 8: Statement Screen Overhaul (PT-BR Descriptions, Origin/Destination, Printable Receipt)

**Files:**
- Modify: `web/src/app/dashboard/statement/page.tsx`
- Test: `web/src/__tests__/dashboard/StatementPage.test.tsx`

- [ ] **Step 1:** Write failing tests in `StatementPage.test.tsx` verifying:
  - Portuguese transaction badges and descriptions.
  - Clear "De:" (origem) and "Para:" (favorecido).
  - Comprovante modal rendering canonical protocol (`PRT-...`), origin, counterparty, and hash.
  - "Imprimir Comprovante" triggering `window.print()` without alert.
  - "Comprovante Consolidado" opening consolidated period statement modal.
- [ ] **Step 2:** Run `npm --prefix web test -- StatementPage` to verify failure.
- [ ] **Step 3:** Implement humanized Brazilian Portuguese descriptions, counterparty badge, printable receipt modal with `@media print` styles, and consolidated statement modal.
- [ ] **Step 4:** Run `npm --prefix web test -- StatementPage` to verify tests pass.
- [ ] **Step 5:** Commit: `git commit -m "feat(web): overhaul statement page with localized descriptions, printable receipt and consolidated modal"`.

---

### Task 9: Export Report Implementation (CSV and OFX)

**Files:**
- Modify: `web/src/utils/exportStatement.ts`
- Modify: `web/src/app/dashboard/statement/page.tsx`
- Test: `web/src/__tests__/utils/exportStatement.test.ts`

- [ ] **Step 1:** Write failing tests in `exportStatement.test.ts` asserting:
  - CSV output is properly formatted UTF-8 with BOM, semicolon delimiter, containing protocol and counterparty.
  - OFX output is valid SGML/XML banking structure with `<STMTTRN>`.
- [ ] **Step 2:** Run `npm --prefix web test -- exportStatement` to verify failure.
- [ ] **Step 3:** Implement real CSV and OFX exporters in `exportStatement.ts` and hook them up to "Exportar Relatório" in `statement/page.tsx`.
- [ ] **Step 4:** Run `npm --prefix web test -- exportStatement` to verify tests pass.
- [ ] **Step 5:** Commit: `git commit -m "feat(web): implement working CSV and OFX report export"`.

---

### Task 10: User Profile & Address Management

**Files:**
- Create: `web/src/app/dashboard/profile/page.tsx`
- Modify: `web/src/components/Header.tsx` (add link/modal trigger to Profile)
- Test: `web/src/__tests__/dashboard/ProfilePage.test.tsx`

- [ ] **Step 1:** Write failing test in `ProfilePage.test.tsx` verifying profile form loads customer data, has read-only identity fields, editable phone/email/address, and CEP auto-complete.
- [ ] **Step 2:** Run `npm --prefix web test -- ProfilePage` to verify failure.
- [ ] **Step 3:** Implement `ProfilePage` with responsive layout, CEP masking, auto-fill, and validation; add navigation link in Header.
- [ ] **Step 4:** Run `npm --prefix web test -- ProfilePage` to verify tests pass.
- [ ] **Step 5:** Commit: `git commit -m "feat(web): add user profile and address management page"`.

---

### Task 11: End-to-End Build and Verification

**Files:**
- Test all services and frontend build.

- [ ] **Step 1:** Run full backend test suite: `./gradlew test`.
- [ ] **Step 2:** Run full frontend test suite: `npm --prefix web test`.
- [ ] **Step 3:** Run frontend production build: `npm --prefix web run build`.
- [ ] **Step 4:** Verify zero regressions, clean audit, and confirm all 11 user requirements are met.
- [ ] **Step 5:** Commit: `git commit -m "chore: verify e2e builds and tests across backend and frontend"`.
