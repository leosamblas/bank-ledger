# Design Doc: Nexus Bank HTML Email Template & Notification Service Integration

## Intent & Why
The `notification-service` is responsible for sending transactional emails when a customer account is created and ready (`account.ready.v1`). Currently, it sends a plain-text message branded with the legacy name "BankLedger". To provide a premium, reassuring, and institutional customer experience, the service must send a responsive, professionally designed HTML email aligned with the **Nexus Bank** brand design system found in the frontend (`web/` and `design/`).

## Goals
- Create a cross-client compatible HTML email template (`account-ready-email.html`) for Nexus Bank account onboarding.
- Adhere strictly to the Nexus Bank design guidelines:
  - Deep Navy primary (`#0e1c2f` / `#0B192C`), Steel Navy secondary (`#1E3E62`), crisp borders (`#E2E8F0`), and pure white cards (`#FFFFFF`).
  - Semantic Emerald (`#10B981` / `#059669`) for active status and security badges.
  - Amber/Gold highlights (`#D4AF37`) for the Nexus Black card / sovereign tier.
  - Clear typography with web-safe fallbacks: Inter (`font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif`) and monospace for financial figures (`'JetBrains Mono', Consolas, Monaco, monospace`).
  - Trust indicators: Bacen authorization, FGC guarantee (up to R$ 250k), SSL 256-bit encryption.
  - Clear Call to Action (CTA) linking to the Nexus Bank login page.
- Integrate the template into `services/notification-service` via `EmailNotificationService` using Spring's `JavaMailSender` and `MimeMessageHelper`.
- Send a multipart message with both rich HTML and plain-text fallback.
- Provide a standalone preview HTML file (`account-ready-preview.html`) that can be opened directly in any browser for immediate visual inspection with sample data.
- Maintain high unit test coverage with Mockito and Project Reactor `StepVerifier`.

## Non-Goals & Anti-Goals
- **Non-Goals**:
  - Adding heavy external template engines (e.g. Thymeleaf, Freemarker, Pebble) when lightweight, zero-dependency token replacement suffices.
  - Modifying the Kafka payload contract (`AccountReadyPayload`).
- **Anti-Goals**:
  - Do not use client-side CSS frameworks or `<script>` tags in email (emails must use email-safe inline CSS and table layout).
  - Do not break backward compatibility or SMTP fail-safe behavior when `app.mail.enabled=false`.

## Constraints
- Email client compatibility (Gmail, Outlook Desktop & Web, Apple Mail, iOS Mail, Android Mail).
- Must operate within Spring Boot 4.1.2 / Java 25 WebFlux non-blocking application (using `Schedulers.boundedElastic()`).

## Approaches Considered
1. **Approach 1 (Selected): Embedded Inlined HTML Template with Dynamic Token Replacement + `MimeMessageHelper`**:
   - Template stored in `services/notification-service/src/main/resources/templates/account-ready-email.html`.
   - Placeholders: `{{fullName}}`, `{{branch}}`, `{{accountNumber}}`, `{{loginUrl}}`, `{{currentYear}}`.
   - Loaded and cached in memory from the classpath.
   - Built with table-based layout and inline CSS for email client compatibility.
   - Dispatched as `multipart/alternative` via `MimeMessageHelper`.
   - *Why selected*: Zero additional dependencies, high performance, clean testability, and standard Spring Mail support.
2. **Approach 2: Spring Boot Starter Thymeleaf**:
   - Add `spring-boot-starter-thymeleaf` dependency.
   - *Trade-off*: Adds unnecessary runtime dependencies and configuration complexity for a single static transactional email template.
3. **Approach 3: Static HTML file only**:
   - Only deliver the HTML file without integrating it into `EmailNotificationService`.
   - *Trade-off*: Leaves the backend still sending the legacy plain text message.

## Technical Details

### 1. Template Structure (`account-ready-email.html`)
- Preheader preview text: `"Sua conta no Nexus Bank está ativa e pronta para uso. Acesse agora."`
- Outer wrapper table: background `#F8FAFC`.
- Container table: `max-width: 600px`, centered, background `#FFFFFF`, border `1px solid #E2E8F0`, border-radius `16px`.
- Header: `#0E1C2F` with Nexus Bank logo SVG/typography and "Ambiente Seguro SSL" badge.
- Body:
  - Personal greeting: "Olá, **{{fullName}}**!"
  - Announcement badge: "CONTA ATIVA • PRONTA PARA USO" (Emerald badge).
  - Main message: "Sua conta digital no Nexus Bank foi criada com sucesso..."
  - Account Card Box (`#F1F5F9` background, `#E2E8F0` border):
    - Banco: Nexus Bank (IP 999)
    - Agência: `{{branch}}`
    - Conta Corrente: `{{accountNumber}}`
    - Titular: `{{fullName}}`
  - Feature highlights grid (Pix 24/7 gratuito, Cartão Nexus Black, Investimentos & Rendimento).
  - Primary CTA button: "Acessar Minha Conta" (`#0E1C2F`, border-radius `8px`, white text).
  - Security advice box (Alerta de segurança: Nunca solicitamos sua senha ou token por e-mail).
- Footer:
  - Nexus Bank Instituição de Pagamento S.A.
  - CNPJ / Regulamentação Bacen • Protegido pelo FGC até R$ 250.000.
  - Links: Central de Ajuda, Termos & Privacidade.

### 2. Service Implementation (`EmailNotificationService.java`)
- Read template once from classpath: `templates/account-ready-email.html`.
- Inject optional configurable property: `app.mail.login-url` (default: `http://localhost:3000/login`).
- `sendAccountReadyEmail(AccountReadyPayload payload)`:
  - Build plain text content for fallback.
  - Replace tokens in HTML template: `{{fullName}}`, `{{branch}}`, `{{accountNumber}}`, `{{loginUrl}}`, `{{currentYear}}`.
  - Use `mailSender.createMimeMessage()`.
  - Use `MimeMessageHelper(mimeMessage, true, "UTF-8")`:
    - `setFrom(fromAddress)`
    - `setTo(payload.email())`
    - `setSubject("Bem-vindo ao Nexus Bank! Sua conta está pronta para uso")`
    - `setText(plainText, htmlText)`
  - Dispatch via `mailSender.send(mimeMessage)` on `Schedulers.boundedElastic()`.

### 3. Verification & Testing
- Unit tests in `EmailNotificationServiceTest`:
  - Test sending with `mailEnabled=false` (verifies `mailSender` never invoked).
  - Test sending with `mailEnabled=true` (captures `MimeMessage`, verifies headers, recipients, subject, HTML and plain text content containing full name, branch, account number).
  - Test error propagation (`MailSendException`).
  - Test null payload validation (`NullPointerException`).
- Build verification: `.\gradlew :services:notification-service:test` passes.
- Browser preview file: `design/nexus_bank_email/preview.html` generated with real-looking preview data for the user to open in Chrome/Edge.
