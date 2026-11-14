/**
 * Mock Customer Data Generator for Bank Ledger Account Service Load Tests.
 * Powered by @faker-js/faker (fakerPT_BR) for realistic Brazilian customer data.
 */

import { fakerPT_BR, faker } from '@faker-js/faker';

export { fakerPT_BR, faker };

export const VALID_DDDS = [
  11, 12, 13, 14, 15, 16, 17, 18, 19,
  21, 22, 24, 27, 28,
  31, 32, 33, 34, 35, 37, 38,
  41, 42, 43, 44, 45, 46, 47, 48, 49,
  51, 53, 54, 55,
  61, 62, 63, 64, 65, 66, 67, 68, 69,
  71, 73, 74, 75, 77, 79,
  81, 82, 83, 84, 85, 86, 87, 88, 89,
  91, 92, 93, 94, 95, 96, 97, 98, 99
];

const generatedCpfs = new Set();
const generatedEmails = new Set();
let sequenceCounter = 0;

/**
 * Generates an 11-digit Brazilian CPF strictly adhering to the modulo-11 checksum
 * algorithm matching com.bankledger.account.domain.vo.Cpf.
 * Ensures all digits are not identical and uniquely generated per process run.
 *
 * @returns {string} 11-digit CPF string
 */
export function generateValidCpf() {
  while (true) {
    const digits = [];
    for (let i = 0; i < 9; i++) {
      digits.push(fakerPT_BR.number.int({ min: 0, max: 9 }));
    }

    // Rejection of identical digits in base (e.g. 111111111)
    if (digits.every(d => d === digits[0])) {
      continue;
    }

    // Check digit 1
    let sum1 = 0;
    for (let i = 0; i < 9; i++) {
      sum1 += digits[i] * (10 - i);
    }
    const rem1 = sum1 % 11;
    const dv1 = rem1 < 2 ? 0 : 11 - rem1;
    digits.push(dv1);

    // Check digit 2
    let sum2 = 0;
    for (let i = 0; i < 10; i++) {
      sum2 += digits[i] * (11 - i);
    }
    const rem2 = sum2 % 11;
    const dv2 = rem2 < 2 ? 0 : 11 - rem2;
    digits.push(dv2);

    // Full 11-digit identical check
    if (new Set(digits).size === 1) {
      continue;
    }

    const cpf = digits.join('');
    if (generatedCpfs.has(cpf)) {
      continue;
    }

    generatedCpfs.add(cpf);
    return cpf;
  }
}

/**
 * Generates a birth date formatted as YYYY-MM-DD for a customer aged between minAge and maxAge.
 * Uses fakerPT_BR.date.birthdate({ min, max, mode: 'age' }) while guaranteeing that the customer
 * has strictly reached their minAge birthday relative to today (UTC),
 * preventing CustomerUnderageException in Account Service domain validation.
 *
 * @param {number} [minAge=18]
 * @param {number} [maxAge=65]
 * @returns {string} ISO date string (YYYY-MM-DD)
 */
export function generateBirthDate(minAge = 18, maxAge = 65) {
  while (true) {
    const birthDateObj = fakerPT_BR.date.birthdate({ min: minAge, max: maxAge, mode: 'age' });
    const year = birthDateObj.getUTCFullYear();
    const month = String(birthDateObj.getUTCMonth() + 1).padStart(2, '0');
    const day = String(birthDateObj.getUTCDate()).padStart(2, '0');
    const dateStr = `${year}-${month}-${day}`;

    // Verify UTC calendar age strictly >= minAge and <= maxAge
    const now = new Date();
    const refYear = now.getUTCFullYear();
    const refMonth = now.getUTCMonth() + 1;
    const refDay = now.getUTCDate();
    let age = refYear - year;
    if (refMonth < Number(month) || (refMonth === Number(month) && refDay < Number(day))) {
      age -= 1;
    }

    if (age >= minAge && age <= maxAge) {
      return dateStr;
    }
  }
}

/**
 * Generates a valid Brazilian mobile phone number with international prefix +55 and valid DDD.
 * Format: +55<DDD>9<8 digits>
 *
 * @returns {string} E.164-compatible phone number
 */
export function generatePhoneNumber() {
  const ddd = fakerPT_BR.helpers.arrayElement(VALID_DDDS);
  const subscriberNumber = `9${fakerPT_BR.string.numeric(8)}`;
  return `+55${ddd}${subscriberNumber}`;
}

/**
 * Generates a realistic and unique email using fakerPT_BR.internet.email,
 * salted with sequence and timestamp to guarantee uniqueness across high-throughput load tests.
 *
 * @param {string} [firstName]
 * @param {string} [lastName]
 * @returns {string} unique email
 */
export function generateUniqueEmail(firstName, lastName) {
  sequenceCounter += 1;
  while (true) {
    const salt = `${sequenceCounter}_${Date.now()}`;
    const emailCandidate = fakerPT_BR.internet.email({
      firstName: firstName ? `${firstName}_${salt}` : `user_${salt}`,
      lastName: lastName || undefined,
      provider: 'loadtest.bankledger.com'
    }).toLowerCase();

    if (!generatedEmails.has(emailCandidate)) {
      generatedEmails.add(emailCandidate);
      return emailCandidate;
    }
  }
}

/**
 * Generates a customer payload compliant with AccountCreationRequest DTO
 * using fakerPT_BR for highly realistic Brazilian customer mock data.
 *
 * @param {Partial<{
 *   fullName: string,
 *   cpf: string,
 *   birthDate: string,
 *   email: string,
 *   phone: string,
 *   password: string
 * }>} [overrides={}]
 * @returns {{
 *   fullName: string,
 *   cpf: string,
 *   birthDate: string,
 *   email: string,
 *   phone: string,
 *   password: string
 * }}
 */
export function generateCustomerPayload(overrides = {}) {
  const firstName = fakerPT_BR.person.firstName();
  const lastName = fakerPT_BR.person.lastName();
  const fullName = fakerPT_BR.person.fullName({ firstName, lastName });

  const cpf = generateValidCpf();
  const birthDate = generateBirthDate(18, 65);
  const email = generateUniqueEmail(firstName, lastName);
  const phone = generatePhoneNumber();
  const password = fakerPT_BR.internet.password({ length: 12, memorable: false });

  return {
    fullName,
    cpf,
    birthDate,
    email,
    phone,
    password,
    ...overrides
  };
}
