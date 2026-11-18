import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {
  calculateAvailableLimit,
  calculateLimitUsage,
  formatRecipientDetails,
  formatCpfInput,
} from '../../app/dashboard/transfer/page';

test('TransferPage - calculateAvailableLimit logic', () => {
  // Normal available limit
  assert.strictEqual(calculateAvailableLimit(5000000, 1000000), 4000000);
  // Full consumption
  assert.strictEqual(calculateAvailableLimit(5000000, 5000000), 0);
  // Used exceeds daily limit (edge case protection, must not return negative)
  assert.strictEqual(calculateAvailableLimit(5000000, 6000000), 0);
});

test('TransferPage - calculateLimitUsage reactive color states', () => {
  const dailyLimit = 1000000; // R$ 10.000,00
  const usedToday = 200000;   // R$ 2.000,00 (20%)

  // Green state: total projected < 70%
  // 20% + 30% = 50%
  const green = calculateLimitUsage(dailyLimit, usedToday, 300000);
  assert.strictEqual(green.colorState, 'green');
  assert.strictEqual(green.isExceeded, false);
  assert.strictEqual(green.percentage, 50);

  // Amber state: 70% <= total projected <= 90%
  // 20% + 55% = 75%
  const amber = calculateLimitUsage(dailyLimit, usedToday, 550000);
  assert.strictEqual(amber.colorState, 'amber');
  assert.strictEqual(amber.isExceeded, false);
  assert.strictEqual(amber.percentage, 75);

  // Red state: total projected > 90%
  // 20% + 75% = 95%
  const redOver90 = calculateLimitUsage(dailyLimit, usedToday, 750000);
  assert.strictEqual(redOver90.colorState, 'red');
  assert.strictEqual(redOver90.isExceeded, false);
  assert.strictEqual(redOver90.percentage, 95);

  // Red state: amount exceeds available limit (available is 800000, requesting 850000)
  const redExceeded = calculateLimitUsage(dailyLimit, usedToday, 850000);
  assert.strictEqual(redExceeded.colorState, 'red');
  assert.strictEqual(redExceeded.isExceeded, true);
});

test('TransferPage - formatRecipientDetails displays unmasked info and canonical institution', () => {
  const mockRecipient = {
    accountId: 'rec-uuid-1',
    branch: '0001',
    agency: '0001',
    accountNumber: '100456',
    number: '100456',
    checkDigit: '7',
    digit: '7',
    holderName: 'Carlos Eduardo Silva',
    holderNameMasked: 'C*** E*** S***',
    cpf: '52998224725',
    bankCode: '001',
    bankName: 'Nexus Bank S.A.',
  };

  const details = formatRecipientDetails(mockRecipient);
  assert.strictEqual(details.holderName, 'Carlos Eduardo Silva');
  assert.strictEqual(details.formattedCpf, '529.982.247-25');
  assert.ok(details.institution.includes('Nexus Bank S.A.'));
  assert.ok(details.institution.includes('001'));
  assert.strictEqual(details.accountString, 'Agência: 0001 | Conta: 100456-7');
});

test('TransferPage - formatCpfInput numeric-only and 14 chars max', () => {
  assert.strictEqual(formatCpfInput('52998224725'), '529.982.247-25');
  assert.strictEqual(formatCpfInput('529.982.247-25'), '529.982.247-25');
  assert.strictEqual(formatCpfInput('abc529xyz982'), '529.982');
  // Extra characters truncated to 11 digits / 14 formatted chars
  assert.strictEqual(formatCpfInput('529982247259999999'), '529.982.247-25');
});

test('TransferPage - Static Architecture, WCAG AAA and Security Guardrails', () => {
  const pagePath = path.resolve(process.cwd(), 'src/app/dashboard/transfer/page.tsx');
  const content = fs.readFileSync(pagePath, 'utf-8');

  // Tab Switcher with accessibility
  assert.ok(content.includes('Por Agência e Conta'), 'Must include tab "Por Agência e Conta"');
  assert.ok(content.includes('Por CPF'), 'Must include tab "Por CPF"');
  assert.ok(content.includes('role="tablist"'), 'Must contain accessible tablist');
  assert.ok(content.includes('role="tab"'), 'Must contain accessible tab elements');

  // CPF input with mask and validation
  assert.ok(content.includes('000.000.000-00'), 'Must include CPF mask 000.000.000-00');
  assert.ok(content.includes('maxLength={14}'), 'Must restrict CPF input length to 14 characters');

  // Verification button
  assert.ok(
    content.includes('Verificar Conta') || content.includes('Verificar'),
    'Must contain button to trigger account/CPF verification'
  );

  // Recipient Card
  assert.ok(
    content.includes('001 - Nexus Bank S.A.') || content.includes('Nexus Bank S.A.'),
    'Must display official institution 001 - Nexus Bank S.A.'
  );

  // Dynamic Daily Limit Bar
  assert.ok(content.includes('Limite Total Diário'), 'Must show card Limite Total Diário');
  assert.ok(content.includes('Consumido Hoje'), 'Must show card Consumido Hoje');
  assert.ok(content.includes('Disponível para Transferir'), 'Must show card Disponível para Transferir');
  assert.ok(content.includes('role="progressbar"'), 'Must contain accessible progressbar');

  // Limit error alert
  assert.ok(
    content.includes('O valor informado ultrapassa o limite diário disponível'),
    'Must show validation error when amount exceeds available limit'
  );

  // Security (LGPD/OWASP): No GET query params for account lookup
  assert.ok(
    !content.includes('/accounts/lookup?'),
    'Must never contain GET query params for /accounts/lookup'
  );
});

test('TransferPage - transfer payload includes counterparty details and protocol receipt', () => {
  const pagePath = path.resolve(process.cwd(), 'src/app/dashboard/transfer/page.tsx');
  const content = fs.readFileSync(pagePath, 'utf-8');

  assert.ok(content.includes('destinationHolderName: details.holderName'), 'Must forward recipient name');
  assert.ok(content.includes('destinationDocument: details.formattedCpf'), 'Must forward recipient document');
  assert.ok(content.includes('destinationAccountFormatted:'), 'Must forward recipient account details');
  assert.ok(content.includes('protocolNumber: protocol'), 'Must produce protocol number on receipt');
});
