import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {
  getTransactionTypeLabel,
  formatCounterpartyRow,
  getCanonicalProtocolNumber,
  parseAccountDetails,
} from '../../app/dashboard/statement/page';

test('StatementPage - Humanized Portuguese transaction labels', () => {
  assert.strictEqual(getTransactionTypeLabel('TRANSFER_OUT'), 'Transferência enviada');
  assert.strictEqual(getTransactionTypeLabel('TRANSFER_IN'), 'Transferência recebida');
  assert.strictEqual(getTransactionTypeLabel('DEPOSIT'), 'Depósito em conta corrente');
  assert.strictEqual(getTransactionTypeLabel('WITHDRAWAL'), 'Saque em terminal');
  // Fallback for custom or unrecognized types
  assert.strictEqual(getTransactionTypeLabel('OUTRO_TIPO'), 'OUTRO_TIPO');
});

test('StatementPage - Counterparty row display formatting', () => {
  // DEBIT: Para: [Nome] ([Conta])
  const debitRow = formatCounterpartyRow({
    nature: 'DEBIT',
    counterpartyName: 'Carlos Silva',
    counterpartyAccount: '0001 / 12345-6',
  });
  assert.strictEqual(debitRow, 'Para: Carlos Silva (0001 / 12345-6)');

  // DEBIT sem conta
  const debitNoAccount = formatCounterpartyRow({
    nature: 'DEBIT',
    counterpartyName: 'Carlos Silva',
  });
  assert.strictEqual(debitNoAccount, 'Para: Carlos Silva');

  // CREDIT: De: [Nome] ([Conta])
  const creditRow = formatCounterpartyRow({
    nature: 'CREDIT',
    counterpartyName: 'Empresa XPTO Ltda',
    counterpartyAccount: '0001 / 99999-0',
  });
  assert.strictEqual(creditRow, 'De: Empresa XPTO Ltda (0001 / 99999-0)');

  // CREDIT sem conta
  const creditNoAccount = formatCounterpartyRow({
    nature: 'CREDIT',
    counterpartyName: 'Empresa XPTO Ltda',
  });
  assert.strictEqual(creditNoAccount, 'De: Empresa XPTO Ltda');
});

test('StatementPage - Canonical protocol number generation', () => {
  // When item already has protocolNumber from backend
  const itemWithProtocol = {
    protocolNumber: 'PRT-20261118-ABC123',
    sequence: 42,
  };
  assert.strictEqual(getCanonicalProtocolNumber(itemWithProtocol), 'PRT-20261118-ABC123');

  // When item does not have protocolNumber, generates canonical PRT-... fallback
  const itemWithoutProtocol = {
    sequence: 42,
    timestamp: '2026-11-18T10:00:00.000Z',
  };
  const generated = getCanonicalProtocolNumber(itemWithoutProtocol);
  assert.ok(generated.startsWith('PRT-'), 'Protocol fallback must start with PRT-');
  assert.ok(generated.length >= 12, 'Protocol must be canonical length');
});

test('StatementPage - Static Architecture & Accessibility Quality Guardrails', () => {
  const statementPagePath = path.resolve(
    process.cwd(),
    'src/app/dashboard/statement/page.tsx'
  );
  const content = fs.readFileSync(statementPagePath, 'utf-8');

  // Must not have ANY alert() calls anywhere in statement page
  assert.ok(!content.includes('alert('), 'StatementPage must not have any alert() calls or stubs');

  // Official Receipt Modal requirements
  assert.ok(
    content.includes('Nexus Bank S.A. - Banco 001'),
    'Receipt modal must contain official header "Nexus Bank S.A. - Banco 001"'
  );
  assert.ok(
    content.includes('Dados de Origem') || content.includes('Origem (Pagador)'),
    'Receipt modal must detail Origem / Pagador'
  );
  assert.ok(
    content.includes('Dados do Destino') || content.includes('Destino (Favorecido)'),
    'Receipt modal must detail Destino / Favorecido'
  );
  assert.ok(
    content.includes('Autenticação Eletrônica') || content.includes('Hash SHA-256'),
    'Receipt modal must include electronic authentication hash'
  );
  assert.ok(
    content.includes('window.print()'),
    'Receipt modal print button must call window.print()'
  );
  assert.ok(
    content.includes('@media print') || content.includes('print:'),
    'Must include print stylesheet rules to isolate receipt during printing'
  );

  // Verification that Consolidated Modal and Export Report were removed as requested
  assert.ok(
    !content.includes('Comprovante Consolidado'),
    'Consolidated receipt button and modal must be removed from statement page'
  );
  assert.ok(
    !content.includes('Exportar Relatório'),
    'Export report button and modal must be removed from statement page'
  );
  assert.ok(
    !content.includes('exportStatementToCsv') && !content.includes('exportStatementToOfx'),
    'StatementPage should no longer import exportStatementToCsv / exportStatementToOfx'
  );
});

test('StatementPage - parseAccountDetails decomposes composite and raw account strings', () => {
  // Caso com prefixo longo "Agência: 0001 | Conta: 100001-2" (como na tela de detalhe)
  const composite = parseAccountDetails('Agência: 0001 | Conta: 100001-2');
  assert.strictEqual(composite.agency, '0001');
  assert.strictEqual(composite.account, '100001-2');

  // Caso anômalo com prefixo Conta: duplicado na frente "Conta:Agência: 0001 | Conta: 100001-2"
  const duplicatedPrefix = parseAccountDetails('Conta:Agência: 0001 | Conta: 100001-2');
  assert.strictEqual(duplicatedPrefix.agency, '0001');
  assert.strictEqual(duplicatedPrefix.account, '100001-2');

  // Caso com barra "0001 / 100001-2"
  const slash = parseAccountDetails('0001 / 100001-2');
  assert.strictEqual(slash.agency, '0001');
  assert.strictEqual(slash.account, '100001-2');

  // Caso com apenas agência informada
  const agencyOnly = parseAccountDetails('Agência: 0001');
  assert.strictEqual(agencyOnly.agency, '0001');
  assert.strictEqual(agencyOnly.account, 'Não informada');

  // Caso simples apenas com a conta "100001-2"
  const simple = parseAccountDetails('100001-2', '0002');
  assert.strictEqual(simple.agency, '0002');
  assert.strictEqual(simple.account, '100001-2');

  // Caso nulo / vazio
  const empty = parseAccountDetails('', '0001');
  assert.strictEqual(empty.agency, '0001');
  assert.strictEqual(empty.account, 'Não informada');
});
