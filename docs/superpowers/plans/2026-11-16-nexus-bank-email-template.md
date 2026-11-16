# Nexus Bank HTML Email Template Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a responsive, brand-aligned HTML email template for Nexus Bank account onboarding, integrate it into `notification-service` using `MimeMessageHelper`, and provide a browser preview.

**Architecture:** The HTML template is packaged as a classpath resource with inline styles for cross-client compatibility. `EmailNotificationService` loads the template, substitutes dynamic parameters (`fullName`, `branch`, `accountNumber`, `loginUrl`, `currentYear`), and sends a multipart message with both rich HTML and plain-text fallback via Spring's `JavaMailSender`.

**Tech Stack:** Java 25, Spring Boot 4.1.2 (WebFlux, Spring Mail, Jakarta Mail), HTML5/CSS (inline email-safe styling), Project Reactor, JUnit 5, AssertJ, Mockito.

**Spec:** [docs/superpowers/specs/2026-11-16-nexus-bank-email-template-design.md](file:///c:/Users/leosa/Documents/Projects/bank-ledger/docs/superpowers/specs/2026-11-16-nexus-bank-email-template-design.md)

## Global Constraints

- Palette: Deep Navy (`#0E1C2F`), Steel Navy (`#1E3E62`), Surface Canvas (`#F8FAFC`), Card Container (`#FFFFFF`), Border (`#E2E8F0`), Emerald (`#10B981` / `#059669`), Gold/Amber (`#D4AF37` / `#F59E0B`).
- Typography: Inter (`'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif`) with monospace for numbers (`'JetBrains Mono', Consolas, Monaco, monospace`).
- Compatibility: All styles must be inlined; layout must use robust table structures (`<table role="presentation">`).
- Email subject: `"Bem-vindo ao Nexus Bank! Sua conta está pronta para uso"`
- Feature flag: When `app.mail.enabled=false`, SMTP sending must be skipped with `Mono.empty()`.

## Review Focus

- Missing or empty placeholder values: `EmailNotificationService` handles null or empty fields cleanly without throwing NPE.
- Special HTML characters in `fullName`: names containing `<` or `&` must be sanitized or properly rendered.
- Classpath template missing: clear error handling with fallback or explicit startup failure.
- MailSendException propagation: reactor chain propagates failure to caller.
- Non-blocking execution: mail dispatch remains on `Schedulers.boundedElastic()`.

---

### Task 1: Create the HTML Email Template & Browser Preview

**Files:**
- Create: `services/notification-service/src/main/resources/templates/account-ready-email.html`
- Create: `design/nexus_bank_email/preview.html`

**Interfaces:**
- Produces: `templates/account-ready-email.html` containing tokens: `{{fullName}}`, `{{branch}}`, `{{accountNumber}}`, `{{loginUrl}}`, `{{currentYear}}`.
- Produces: `design/nexus_bank_email/preview.html` with hardcoded sample values for browser visual inspection.

- [ ] **Step 1: Write `services/notification-service/src/main/resources/templates/account-ready-email.html`**
  - Implement full responsive HTML email template using standard HTML email boilerplate (`<!DOCTYPE html>`, `<html lang="pt-BR">`, `<head>` with viewport and preheader, `<body style="...">`).
  - Table wrapper with background `#F8FAFC`.
  - Centered card (`max-width: 600px`, background `#FFFFFF`, border `1px solid #E2E8F0`, border-radius `16px`).
  - Brand header: Deep Navy `#0E1C2F` with Nexus Bank logo symbol, bank name, Bacen subtitle, and SSL badge.
  - Active status pill: `#ECFDF5` background, `#059669` text, `#A7F3D0` border ("CONTA ATIVA • PRONTA PARA USO").
  - Personalized greeting: "Olá, **{{fullName}}**!"
  - Account info container (`#F1F5F9` background, `#E2E8F0` border, `rounded-lg`):
    - Banco: Nexus Bank (IP 999)
    - Agência: `{{branch}}`
    - Conta Corrente: `{{accountNumber}}`
    - Titular: `{{fullName}}`
  - Benefits grid: Pix sem taxas, Cartão Nexus Black físico e virtual, Proteção FGC até R$ 250k.
  - Call to Action button: "Acessar Minha Conta" linking to `{{loginUrl}}`.
  - Security warning box: "Segurança Nexus: Nunca solicitamos sua senha ou token por e-mail ou telefone."
  - Institutional footer: Nexus Bank S.A., Bacen authorization, support contacts, and copyright `{{currentYear}}`.

- [ ] **Step 2: Write `design/nexus_bank_email/preview.html`**
  - Duplicate template with realistic sample data:
    - `{{fullName}}` -> `Carlos Eduardo Silva`
    - `{{branch}}` -> `0001`
    - `{{accountNumber}}` -> `104829-3`
    - `{{loginUrl}}` -> `http://localhost:3000/login`
    - `{{currentYear}}` -> `2026`

- [ ] **Step 3: Verify preview HTML layout and rendering**
  - Verify file exists and has valid HTML syntax without unclosed tags.

- [ ] **Step 4: Commit Task 1**
  ```bash
  git add services/notification-service/src/main/resources/templates/account-ready-email.html design/nexus_bank_email/preview.html
  git commit -m "feat(notification): add Nexus Bank responsive HTML email template and preview"
  ```

---

### Task 2: Update `EmailNotificationService` to Load Template and Send Rich MimeMessage

**Files:**
- Modify: `services/notification-service/src/main/java/com/bankledger/notification/application/service/EmailNotificationService.java`
- Modify: `services/notification-service/src/main/resources/application.yml`

**Interfaces:**
- Consumes: `templates/account-ready-email.html` from classpath.
- Consumes: `@Value("${app.mail.login-url:http://localhost:3000/login}") String loginUrl`.
- Produces: `EmailNotificationService.sendAccountReadyEmail(AccountReadyPayload payload) -> Mono<Void>` sending multipart MimeMessage via `mailSender.send(MimeMessage)`.

- [ ] **Step 1: Write template loading and placeholder replacement logic in `EmailNotificationService`**
  - Load template content during service construction or first call using `ClassLoader.getResourceAsStream("templates/account-ready-email.html")` with UTF-8 encoding.
  - Implement helper method `renderHtml(AccountReadyPayload payload)` replacing:
    - `{{fullName}}` with `payload.fullName()`
    - `{{branch}}` with `payload.branch()`
    - `{{accountNumber}}` with `payload.accountNumber()`
    - `{{loginUrl}}` with `this.loginUrl`
    - `{{currentYear}}` with `String.valueOf(Year.now().getValue())`
  - Implement helper method `buildPlainText(AccountReadyPayload payload)` for plain-text fallback.

- [ ] **Step 2: Update `sendAccountReadyEmail` to send MimeMessage**
  - When `mailEnabled` is false, log and return `Mono.empty()`.
  - When `mailEnabled` is true, inside `Mono.fromRunnable`:
    - `MimeMessage mimeMessage = mailSender.createMimeMessage();`
    - `MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());`
    - `helper.setFrom(fromAddress);`
    - `helper.setTo(payload.email());`
    - `helper.setSubject("Bem-vindo ao Nexus Bank! Sua conta está pronta para uso");`
    - `helper.setText(plainText, htmlText);`
    - `mailSender.send(mimeMessage);`
  - Subscribe on `Schedulers.boundedElastic()`.

- [ ] **Step 3: Add `app.mail.login-url` to `application.yml`**
  - In `services/notification-service/src/main/resources/application.yml`, under `app.mail`, add:
    ```yaml
    login-url: ${LOGIN_URL:http://localhost:3000/login}
    ```

- [ ] **Step 4: Commit Task 2**
  ```bash
  git add services/notification-service/src/main/java/com/bankledger/notification/application/service/EmailNotificationService.java services/notification-service/src/main/resources/application.yml
  git commit -m "feat(notification): update EmailNotificationService to send rich HTML email"
  ```

---

### Task 3: Update Unit Tests in `EmailNotificationServiceTest`

**Files:**
- Modify: `services/notification-service/src/test/java/com/bankledger/notification/application/service/EmailNotificationServiceTest.java`

**Interfaces:**
- Consumes: `EmailNotificationService` constructor with `(mailSender, mailEnabled, fromAddress, loginUrl)`.
- Verifies: `mailSender.createMimeMessage()` and `mailSender.send(MimeMessage)`.

- [ ] **Step 1: Write failing/updated tests for MimeMessage dispatch**
  - Update `setUp()` to instantiate `MimeMessage` using a real `Session` (e.g. `Session.getInstance(new Properties())`) or mock `JavaMailSender.createMimeMessage()`.
  - Test 1: `whenMailDisabled_shouldNotSendEmailAndReturnEmptyMono()`
    - Verify `mailSender.send(any(MimeMessage.class))` is never called.
  - Test 2: `whenMailEnabled_shouldConstructCorrectMimeMessageAndSend()`
    - Mock `mailSender.createMimeMessage()` returning `new MimeMessage(session)`.
    - Execute `service.sendAccountReadyEmail(payload).block()`.
    - Capture `MimeMessage` passed to `mailSender.send(MimeMessage)`.
    - Verify recipient, from, subject ("Bem-vindo ao Nexus Bank! Sua conta está pronta para uso").
    - Verify content contains `Alice Silva`, `0001`, `123456-7`, and HTML elements.
  - Test 3: `whenMailEnabledAndMailSenderFails_shouldPropagateError()`
    - Verify `MailSendException` is propagated through Reactor `StepVerifier`.
  - Test 4: `whenPayloadIsNull_shouldThrowNullPointerException()`
    - Verify NPE with message "payload must not be null".

- [ ] **Step 2: Run tests to verify they pass**
  - Command: `.\gradlew :services:notification-service:test`
  - Expected: BUILD SUCCESSFUL with all tests passing.

- [ ] **Step 3: Commit Task 3**
  ```bash
  git add services/notification-service/src/test/java/com/bankledger/notification/application/service/EmailNotificationServiceTest.java
  git commit -m "test(notification): update unit tests for rich HTML MimeMessage sending"
  ```

---

### Task 4: End-to-End Build and Verification

**Files:**
- Check: All modified and created files.

- [ ] **Step 1: Run complete notification-service verification**
  - Run `.\gradlew :services:notification-service:check`
  - Verify zero compiler warnings and all tests passing.

- [ ] **Step 2: Inspect standalone preview file**
  - Verify `design/nexus_bank_email/preview.html` opens cleanly in browser or renders properly.

- [ ] **Step 3: Final branch commit**
  ```bash
  git status
  ```
