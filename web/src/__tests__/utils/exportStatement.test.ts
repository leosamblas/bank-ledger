import test from 'node:test';
import assert from 'node:assert/strict';
import {
  generateStatementCsv,
  generateStatementOfx,
  getStatementExportFilename,
  exportStatementToCsv,
  exportStatementToOfx,
} from '../../utils/exportStatement';
import { StatementItem, BankingAccountInfo } from '../../services/types';

const mockAccount: BankingAccountInfo = {
  id: 'acc-123',
  agency: '0001',
  accountNumber: '123456',
  digit: '7',
  holderName: 'Carlos Eduardo Silva',
  companyName: 'Nexus Bank S.A.',
  cpfMasked: '529.***.***-25',
  status: 'ACTIVE',
};

const mockItems: StatementItem[] = [
  {
    id: 'tx-1',
    sequence: 101,
    transactionId: 'tx-uuid-001',
    timestamp: '2026-11-17T14:30:00.000Z',
    category: 'TRANSFER',
    type: 'TRANSFER_OUT',
    description: 'Transferência Pix',
    counterpartyName: 'Marina Santos',
    counterpartyDocumentMasked: '123.***.***-89',
    counterpartyAccount: '0001 / 98765-4',
    nature: 'DEBIT',
    amountCents: 15000, // R$ 150,00
    balanceAfterCents: 485000, // R$ 4.850,00
    idempotencyKey: 'idemp-001',
    protocolNumber: 'PRT-20261117-000101',
  },
  {
    id: 'tx-2',
    sequence: 102,
    transactionId: 'tx-uuid-002',
    timestamp: '2026-11-17T16:00:00.000Z',
    category: 'DEPOSIT',
    type: 'DEPOSIT',
    description: 'Depósito em dinheiro',
    counterpartyName: 'Caixa Eletrônico Nexus',
    counterpartyDocumentMasked: '***.***.***-**',
    counterpartyAccount: 'Agência 0001',
    nature: 'CREDIT',
    amountCents: 50000, // R$ 500,00
    balanceAfterCents: 535000, // R$ 5.350,00
    idempotencyKey: 'idemp-002',
    // Sem protocolNumber pré-definido, deve gerar fallback canônico PRT-...
  },
];

test('exportStatement - CSV generation', async (t) => {
  await t.test('includes UTF-8 BOM, semicolon delimiter and exact required header', () => {
    const csv = generateStatementCsv(mockItems, mockAccount);

    // BOM UTF-8
    assert.ok(csv.startsWith('\uFEFF'), 'CSV must start with UTF-8 BOM');

    // Expected exact header from specification
    const expectedHeader =
      'Data;Protocolo;Tipo;Descrição;Contraparte;Documento;Agência/Conta;Natureza;Valor (R$);Saldo Após (R$)';
    assert.ok(csv.includes(expectedHeader), 'CSV must contain required exact header columns');
  });

  await t.test('formats rows with Brazilian numbers and canonical protocol', () => {
    const csv = generateStatementCsv(mockItems, mockAccount);
    const lines = csv.split('\r\n');

    // Header is first line (after stripping BOM)
    const headerLine = lines[0].replace('\uFEFF', '');
    assert.strictEqual(
      headerLine,
      'Data;Protocolo;Tipo;Descrição;Contraparte;Documento;Agência/Conta;Natureza;Valor (R$);Saldo Após (R$)'
    );

    // Row 1: TRANSFER_OUT, DEBIT
    const row1 = lines[1];
    assert.ok(row1.includes('PRT-20261117-000101'), 'Row 1 should contain canonical protocol');
    assert.ok(
      row1.includes('Transferência enviada') || row1.includes('TRANSFER_OUT'),
      'Row 1 should include transaction type'
    );
    assert.ok(row1.includes('Marina Santos'), 'Row 1 should include counterparty');
    assert.ok(row1.includes('Débito') || row1.includes('DEBIT'), 'Row 1 should identify DEBIT');
    // Brazilian formatted number (150,00 and 4.850,00)
    assert.ok(row1.includes('150,00'), 'Row 1 must format amount in Brazilian cents');
    assert.ok(row1.includes('4.850,00'), 'Row 1 must format balance in Brazilian format');

    // Row 2: Fallback canonical protocol PRT-...
    const row2 = lines[2];
    assert.ok(/PRT-/.test(row2), 'Row 2 without protocolNumber must generate canonical PRT-... fallback');
    assert.ok(row2.includes('500,00'), 'Row 2 must format deposit amount in Brazilian cents');
    assert.ok(row2.includes('5.350,00'), 'Row 2 must format balance in Brazilian cents');
  });

  await t.test('sanitizes formula injection characters (=, +, -, @)', () => {
    const maliciousItem: StatementItem = {
      ...mockItems[0],
      description: '=CMD|"/C calc"!A0',
      counterpartyName: '+5511999999999',
    };
    const csv = generateStatementCsv([maliciousItem], mockAccount);
    assert.ok(csv.includes(`"'=CMD|""/C calc""!A0"`), 'Must sanitize formula injection in description');
    assert.ok(csv.includes(`"'+5511999999999"`), 'Must sanitize formula injection in counterparty');
  });
});

test('exportStatement - OFX generation', async (t) => {
  await t.test('generates valid OFX SGML structure with required tags', () => {
    const ofx = generateStatementOfx(mockItems, mockAccount);

    assert.ok(ofx.includes('<OFX>'), 'OFX must include <OFX>');
    assert.ok(ofx.includes('<STMTTRNRS>'), 'OFX must include <STMTTRNRS>');
    assert.ok(ofx.includes('<BANKTRANLIST>'), 'OFX must include <BANKTRANLIST>');
    assert.ok(ofx.includes('<STMTTRN>'), 'OFX must include <STMTTRN>');
    assert.ok(ofx.includes('<TRNTYPE>'), 'OFX must include <TRNTYPE>');
    assert.ok(ofx.includes('<DTPOSTED>'), 'OFX must include <DTPOSTED>');
    assert.ok(ofx.includes('<TRNAMT>'), 'OFX must include <TRNAMT>');
    assert.ok(ofx.includes('<FITID>'), 'OFX must include <FITID>');
    assert.ok(ofx.includes('<NAME>'), 'OFX must include <NAME>');
    assert.ok(ofx.includes('<MEMO>'), 'OFX must include <MEMO>');
  });

  await t.test('maps account info and transaction data correctly in OFX', () => {
    const ofx = generateStatementOfx(mockItems, mockAccount);

    assert.ok(ofx.includes('<BRANCHID>0001</BRANCHID>'), 'OFX must include branch agency');
    assert.ok(ofx.includes('<ACCTID>1234567</ACCTID>'), 'OFX must include account number and digit');
    assert.ok(ofx.includes('<NAME>Marina Santos</NAME>'), 'OFX must include counterparty in <NAME>');
    assert.ok(ofx.includes('<TRNAMT>-150.00</TRNAMT>'), 'OFX must format debit as negative amount');
    assert.ok(ofx.includes('<TRNAMT>500.00</TRNAMT>'), 'OFX must format credit as positive amount');
  });
});

test('exportStatement - Filename naming convention', () => {
  const fixedDate = new Date(2026, 10, 18); // 18 de Nov de 2026
  assert.strictEqual(getStatementExportFilename('csv', fixedDate), 'extrato-nexus-20261118.csv');
  assert.strictEqual(getStatementExportFilename('ofx', fixedDate), 'extrato-nexus-20261118.ofx');
});
