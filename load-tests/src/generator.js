/**
 * Mock Customer Data Generator for Bank Ledger Account Service Load Tests.
 * Pure Vanilla JavaScript (ES Module) with zero external dependencies.
 */

const FIRST_NAMES = [
  'Lucas', 'Ana', 'Gabriel', 'Beatriz', 'Mateus', 'Juliana', 'Rodrigo', 'Camila',
  'Thiago', 'Mariana', 'Felipe', 'Larissa', 'Bruno', 'Fernanda', 'Diego', 'Patricia',
  'Rafael', 'Amanda', 'Gustavo', 'Letícia', 'Carlos', 'Carolina', 'Eduardo', 'Helena'
];

const LAST_NAMES = [
  'Silva', 'Santos', 'Oliveira', 'Souza', 'Rodrigues', 'Ferreira', 'Alves', 'Pereira',
  'Lima', 'Gomes', 'Costa', 'Ribeiro', 'Carvalho', 'Martins', 'Araújo', 'Melo',
  'Barbosa', 'Cardoso', 'Nascimento', 'Almeida'
];

const VALID_DDDS = [
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
      digits.push(Math.floor(Math.random() * 10));
    }

    // Rejection of identical digits in base
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
 * Guarantees that the customer has strictly reached their minAge birthday relative to today (UTC),
 * preventing CustomerUnderageException in Account Service domain validation.
 *
 * @param {number} [minAge=18]
 * @param {number} [maxAge=65]
 * @returns {string} ISO date string (YYYY-MM-DD)
 */
function generateBirthDate(minAge = 18, maxAge = 65) {
  const now = new Date();
  const maxBirth = new Date(Date.UTC(now.getUTCFullYear() - minAge, now.getUTCMonth(), now.getUTCDate()));
  const minBirth = new Date(Date.UTC(now.getUTCFullYear() - maxAge, now.getUTCMonth(), now.getUTCDate()));

  const birthTimestamp = minBirth.getTime() + Math.random() * (maxBirth.getTime() - minBirth.getTime());
  const birthDate = new Date(birthTimestamp);

  const year = birthDate.getUTCFullYear();
  const month = String(birthDate.getUTCMonth() + 1).padStart(2, '0');
  const day = String(birthDate.getUTCDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

/**
 * Generates a valid Brazilian phone number with international prefix +55 and valid DDD.
 * Format: +55<DDD>9<8 digits>
 *
 * @returns {string} E.164-compatible phone number
 */
function generatePhoneNumber() {
  const ddd = VALID_DDDS[Math.floor(Math.random() * VALID_DDDS.length)];
  let subscriberNumber = '9';
  for (let i = 0; i < 8; i++) {
    subscriberNumber += Math.floor(Math.random() * 10);
  }
  return `+55${ddd}${subscriberNumber}`;
}

/**
 * Generates a customer payload compliant with AccountCreationRequest DTO.
 *
 * @returns {{
 *   fullName: string,
 *   cpf: string,
 *   birthDate: string,
 *   email: string,
 *   phone: string,
 *   password: string
 * }}
 */
export function generateCustomerPayload() {
  sequenceCounter += 1;

  const firstName = FIRST_NAMES[Math.floor(Math.random() * FIRST_NAMES.length)];
  const lastName = LAST_NAMES[Math.floor(Math.random() * LAST_NAMES.length)];
  const fullName = `${firstName} ${lastName}`;

  const cpf = generateValidCpf();
  const birthDate = generateBirthDate(18, 65);

  let email;
  while (true) {
    const timestamp = Date.now();
    const randomSuffix = Math.random().toString(36).substring(2, 8);
    email = `user_${timestamp}_${sequenceCounter}_${randomSuffix}@loadtest.bankledger.com`;
    if (!generatedEmails.has(email)) {
      generatedEmails.add(email);
      break;
    }
  }

  const phone = generatePhoneNumber();
  const password = 'Password@123';

  return {
    fullName,
    cpf,
    birthDate,
    email,
    phone,
    password
  };
}
