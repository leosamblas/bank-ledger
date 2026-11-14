import test from 'node:test';
import assert from 'node:assert/strict';
import { generateValidCpf, generateCustomerPayload } from '../src/generator.js';

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
  assert.ok(p1.password.length >= 8);
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

    // Validate birthDate range (e.g., between 18 and 80 years old)
    const birthDate = new Date(p.birthDate);
    const now = new Date();
    const age = now.getFullYear() - birthDate.getFullYear();
    assert.ok(age >= 18 && age <= 100, `Age ${age} outside valid range`);

    // Verify phone DDD and format
    assert.match(p.phone, /^\+55\d{10,11}$/);
  }
});
