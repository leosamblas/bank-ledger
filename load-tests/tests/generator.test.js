import test from 'node:test';
import assert from 'node:assert/strict';
import {
  generateValidCpf,
  generateBirthDate,
  generatePhoneNumber,
  generateUniqueEmail,
  generateCustomerPayload,
  fakerPT_BR,
  faker,
  VALID_DDDS
} from '../src/generator.js';

test('faker and fakerPT_BR are properly exported and configured', () => {
  assert.ok(faker, 'faker must be exported');
  assert.ok(fakerPT_BR, 'fakerPT_BR must be exported');
  assert.equal(typeof fakerPT_BR.person.fullName, 'function');
  assert.equal(typeof fakerPT_BR.date.birthdate, 'function');
  assert.equal(typeof fakerPT_BR.internet.email, 'function');
  assert.equal(typeof fakerPT_BR.internet.password, 'function');
});

test('generateValidCpf generates an 11-digit valid CPF satisfying modulo 11', () => {
  for (let i = 0; i < 50; i++) {
    const cpf = generateValidCpf();
    assert.equal(cpf.length, 11);
    assert.match(cpf, /^\d{11}$/);
    assert.notEqual(new Set(cpf.split('')).size, 1, 'identical digits disallowed');

    // Check digit 1
    let sum1 = 0;
    for (let j = 0; j < 9; j++) sum1 += Number(cpf[j]) * (10 - j);
    let rem1 = sum1 % 11;
    let dv1 = rem1 < 2 ? 0 : 11 - rem1;
    assert.equal(Number(cpf[9]), dv1);

    // Check digit 2
    let sum2 = 0;
    for (let j = 0; j < 10; j++) sum2 += Number(cpf[j]) * (11 - j);
    let rem2 = sum2 % 11;
    let dv2 = rem2 < 2 ? 0 : 11 - rem2;
    assert.equal(Number(cpf[10]), dv2);
  }
});

function calculateExactElapsedYears(birthDateStr, referenceDate = new Date()) {
  const [bYear, bMonth, bDay] = birthDateStr.split('-').map(Number);
  const refYear = referenceDate.getUTCFullYear();
  const refMonth = referenceDate.getUTCMonth() + 1;
  const refDay = referenceDate.getUTCDate();

  let age = refYear - bYear;
  if (refMonth < bMonth || (refMonth === bMonth && refDay < bDay)) {
    age -= 1;
  }
  return age;
}

test('generateBirthDate strictly guarantees age between minAge and maxAge', () => {
  for (let i = 0; i < 50; i++) {
    const birthDate = generateBirthDate(18, 65);
    assert.match(birthDate, /^\d{4}-\d{2}-\d{2}$/);
    const age = calculateExactElapsedYears(birthDate);
    assert.ok(age >= 18, `Age must be >= 18, got ${age} for ${birthDate}`);
    assert.ok(age <= 65, `Age must be <= 65, got ${age} for ${birthDate}`);
  }
});

test('generatePhoneNumber produces valid Brazilian phone with valid DDD and E.164 +55 prefix', () => {
  for (let i = 0; i < 50; i++) {
    const phone = generatePhoneNumber();
    assert.match(phone, /^\+55\d{10,11}$/);
    const ddd = Number(phone.substring(3, 5));
    assert.ok(VALID_DDDS.includes(ddd), `DDD ${ddd} must be in VALID_DDDS list`);
    assert.equal(phone.charAt(5), '9', 'Mobile subscriber number must start with 9');
  }
});

test('generateCustomerPayload produces a valid AccountCreationRequest with age >= 18 and unique fields', () => {
  const p1 = generateCustomerPayload();
  const p2 = generateCustomerPayload();
  assert.notEqual(p1.cpf, p2.cpf);
  assert.notEqual(p1.email, p2.email);
  assert.ok(p1.fullName.length > 3);
  assert.match(p1.email, /^.+@.+\..+$/);
  assert.match(p1.phone, /^\+55\d{10,11}$/);
  assert.match(p1.birthDate, /^\d{4}-\d{2}-\d{2}$/);
  const birthYear = parseInt(p1.birthDate.substring(0, 4), 10);
  assert.ok(new Date().getFullYear() - birthYear >= 18);
  assert.ok(calculateExactElapsedYears(p1.birthDate) >= 18, 'Customer must have reached 18th birthday');
  assert.ok(p1.password.length >= 8);
});

test('generateCustomerPayload respects custom overrides', () => {
  const custom = generateCustomerPayload({
    password: 'CustomSecurePassword!2026',
    fullName: 'Test User Override'
  });
  assert.equal(custom.password, 'CustomSecurePassword!2026');
  assert.equal(custom.fullName, 'Test User Override');
  assert.match(custom.cpf, /^\d{11}$/);
  assert.match(custom.phone, /^\+55\d{10,11}$/);
});

test('generateCustomerPayload generates 100 payloads with unique CPFs and emails', () => {
  const cpfs = new Set();
  const emails = new Set();

  for (let i = 0; i < 100; i++) {
    const p = generateCustomerPayload();
    assert.ok(!cpfs.has(p.cpf), `Duplicate CPF generated: ${p.cpf}`);
    assert.ok(!emails.has(p.email), `Duplicate email generated: ${p.email}`);
    cpfs.add(p.cpf);
    emails.add(p.email);

    // Validate true elapsed calendar age matches Account Service Period.between >= 18 rule
    const exactAge = calculateExactElapsedYears(p.birthDate);
    assert.ok(exactAge >= 18 && exactAge <= 65, `Exact age ${exactAge} outside [18, 65] range for ${p.birthDate}`);

    // Verify phone DDD and format
    assert.match(p.phone, /^\+55\d{10,11}$/);
  }
});
