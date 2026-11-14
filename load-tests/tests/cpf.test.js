import test from 'node:test';
import assert from 'node:assert/strict';
import {
  generateValidCpf,
  validateCpf,
  formatCpf,
  unformatCpf,
  FISCAL_REGIONS,
  resetGeneratedCpfs
} from '../src/cpf.js';

test('validateCpf correctly validates standard valid and invalid CPFs', () => {
  // Known valid CPFs with correct check digits
  assert.ok(validateCpf('52998224725'));
  assert.ok(validateCpf('529.982.247-25'));
  assert.ok(validateCpf('06855110908'));
  assert.ok(validateCpf('068.551.109-08'));

  // Invalid: identical digits
  assert.equal(validateCpf('00000000000'), false);
  assert.equal(validateCpf('11111111111'), false);
  assert.equal(validateCpf('999.999.999-99'), false);

  // Invalid: wrong check digits
  assert.equal(validateCpf('52998224726'), false);
  assert.equal(validateCpf('06855110990'), false);

  // Invalid: bad length or characters
  assert.equal(validateCpf('12345'), false);
  assert.equal(validateCpf(''), false);
  assert.equal(validateCpf(null), false);
  assert.equal(validateCpf('5299822472A'), false);
});

test('generateValidCpf produces 100% valid CPFs verified by validateCpf and Modulo-11 logic', () => {
  resetGeneratedCpfs();
  for (let i = 0; i < 100; i++) {
    const cpf = generateValidCpf();
    assert.equal(cpf.length, 11);
    assert.match(cpf, /^\d{11}$/);
    assert.ok(validateCpf(cpf), `Generated CPF ${cpf} must be valid according to official algorithm`);

    // Explicit check digit 1 calculation (weights 10 to 2)
    let sum1 = 0;
    for (let j = 0; j < 9; j++) sum1 += Number(cpf[j]) * (10 - j);
    let rem1 = sum1 % 11;
    let dv1 = rem1 < 2 ? 0 : 11 - rem1;
    assert.equal(Number(cpf[9]), dv1);

    // Explicit check digit 2 calculation (weights 11 to 2)
    let sum2 = 0;
    for (let j = 0; j < 10; j++) sum2 += Number(cpf[j]) * (11 - j);
    let rem2 = sum2 % 11;
    let dv2 = rem2 < 2 ? 0 : 11 - rem2;
    assert.equal(Number(cpf[10]), dv2);
  }
});

test('generateValidCpf supports formatting and specific fiscal regions', () => {
  // Test formatting option
  const formatted = generateValidCpf({ formatted: true });
  assert.match(formatted, /^\d{3}\.\d{3}\.\d{3}-\d{2}$/);
  assert.ok(validateCpf(formatted));

  // Test boolean shorthand for formatted
  const formattedBool = generateValidCpf(true);
  assert.match(formattedBool, /^\d{3}\.\d{3}\.\d{3}-\d{2}$/);
  assert.ok(validateCpf(formattedBool));

  // Test fiscal regions (0 = RS, 8 = SP, 6 = MG, 7 = RJ/ES)
  for (const region of [0, 6, 7, 8]) {
    const regionalCpf = generateValidCpf({ fiscalRegion: region });
    assert.equal(Number(regionalCpf[8]), region, `9th digit of CPF must be fiscal region ${region}`);
    assert.ok(validateCpf(regionalCpf));
  }
});

test('formatCpf and unformatCpf behave correctly', () => {
  const raw = '52998224725';
  const formatted = formatCpf(raw);
  assert.equal(formatted, '529.982.247-25');
  assert.equal(unformatCpf(formatted), raw);
  assert.equal(unformatCpf('abc123def456ghi78901'), '12345678901');
});

test('FISCAL_REGIONS maps all Brazilian regions 0 through 9', () => {
  for (let r = 0; r <= 9; r++) {
    assert.ok(Array.isArray(FISCAL_REGIONS[r]), `Region ${r} must map to state array`);
    assert.ok(FISCAL_REGIONS[r].length > 0);
  }
  assert.deepEqual(FISCAL_REGIONS[8], ['SP']);
  assert.deepEqual(FISCAL_REGIONS[0], ['RS']);
});
