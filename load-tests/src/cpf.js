/**
 * Pure JavaScript Brazilian CPF (Cadastro de Pessoas Físicas) Generator and Validator.
 * Zero external dependencies.
 *
 * Implements the official Receita Federal do Brasil Modulo-11 check digit algorithm
 * and fiscal region coding. Fully compliant with Bank Ledger Cpf domain VO
 * (com.bankledger.account.domain.vo.Cpf).
 */

/**
 * Fiscal regions defined by the Brazilian Federal Revenue Service (Receita Federal):
 * The 9th digit of a CPF indicates the fiscal region where it was issued.
 */
export const FISCAL_REGIONS = Object.freeze({
  0: ['RS'],
  1: ['DF', 'GO', 'MS', 'MT', 'TO'],
  2: ['AC', 'AM', 'AP', 'PA', 'RO', 'RR'],
  3: ['CE', 'MA', 'PI'],
  4: ['AL', 'PB', 'PE', 'RN'],
  5: ['BA', 'SE'],
  6: ['MG'],
  7: ['ES', 'RJ'],
  8: ['SP'],
  9: ['PR', 'SC'],
});

const generatedCpfs = new Set();

/**
 * Clears the in-memory uniqueness cache of generated CPFs.
 */
export function resetGeneratedCpfs() {
  generatedCpfs.clear();
}

/**
 * Strips formatting (dots, hyphens, spaces) from a CPF string.
 *
 * @param {string} cpf
 * @returns {string} 11-digit clean string
 */
export function unformatCpf(cpf) {
  if (typeof cpf !== 'string') return '';
  return cpf.replace(/\D/g, '');
}

/**
 * Formats an 11-digit CPF into standard Brazilian format: 000.000.000-00.
 *
 * @param {string} cpf
 * @returns {string} Formatted CPF string
 */
export function formatCpf(cpf) {
  const digits = unformatCpf(cpf);
  if (digits.length !== 11) return cpf;
  return digits.replace(/(\d{3})(\d{3})(\d{3})(\d{2})/, '$1.$2.$3-$4');
}

/**
 * Calculates a single Modulo-11 check digit.
 *
 * @param {number[]} digits - Array of numeric digits
 * @param {number} startingWeight - Initial multiplier weight (10 for DV1, 11 for DV2)
 * @returns {number} Check digit (0-9)
 */
function calculateCheckDigit(digits, startingWeight) {
  let sum = 0;
  for (let i = 0; i < digits.length; i++) {
    sum += digits[i] * (startingWeight - i);
  }
  const remainder = sum % 11;
  return remainder < 2 ? 0 : 11 - remainder;
}

/**
 * Validates a Brazilian CPF according to the official modulo-11 algorithm
 * matching com.bankledger.account.domain.vo.Cpf:
 * 1. Must contain exactly 11 numeric digits.
 * 2. Rejects identical digit sequences (e.g. 111.111.111-11).
 * 3. Validates both check digits (10th and 11th digits).
 *
 * @param {string} cpf - Raw or formatted CPF string
 * @returns {boolean} True if valid, false otherwise
 */
export function validateCpf(cpf) {
  if (!cpf || typeof cpf !== 'string') return false;

  const clean = cpf.trim();
  // Validate allowed characters (digits, dots, hyphens)
  if (!/^[0-9.\-]+$/.test(clean)) return false;

  const digits = clean.replace(/\D/g, '');
  if (digits.length !== 11) return false;

  // Reject identical digits (e.g. 00000000000, 11111111111)
  if (/^(\d)\1{10}$/.test(digits)) return false;

  const numericDigits = digits.split('').map(Number);

  // Validate first check digit
  const dv1 = calculateCheckDigit(numericDigits.slice(0, 9), 10);
  if (numericDigits[9] !== dv1) return false;

  // Validate second check digit
  const dv2 = calculateCheckDigit(numericDigits.slice(0, 10), 11);
  if (numericDigits[10] !== dv2) return false;

  return true;
}

/**
 * Generates an authentic, valid Brazilian mock CPF using the official Receita Federal Modulo-11 logic.
 * Independent of external libraries (pure native JavaScript).
 *
 * @param {Object|boolean} [options={}] - Options object or boolean for formatted output
 * @param {boolean} [options.formatted=false] - When true, returns "000.000.000-00", otherwise "00000000000"
 * @param {number} [options.fiscalRegion] - Optional specific fiscal region digit (0-9)
 * @param {boolean} [options.unique=true] - When true, guarantees uniqueness within current process
 * @returns {string} Valid 11-digit CPF string
 */
export function generateValidCpf(options = {}) {
  const isFormatted = typeof options === 'boolean' ? options : Boolean(options?.formatted);
  const targetRegion = typeof options === 'object' && typeof options?.fiscalRegion === 'number'
    && options.fiscalRegion >= 0 && options.fiscalRegion <= 9
    ? options.fiscalRegion
    : null;
  const ensureUnique = typeof options === 'object' && options?.unique === false ? false : true;

  while (true) {
    const digits = [];

    // Generate first 8 random digits
    for (let i = 0; i < 8; i++) {
      digits.push(Math.floor(Math.random() * 10));
    }

    // 9th digit represents the Receita Federal Fiscal Region (0-9)
    const regionDigit = targetRegion !== null
      ? targetRegion
      : Math.floor(Math.random() * 10);
    digits.push(regionDigit);

    // Reject base numbers where all digits are identical (e.g. 000000000)
    if (digits.every(d => d === digits[0])) {
      continue;
    }

    // Calculate check digit 1 (weights: 10 down to 2)
    const dv1 = calculateCheckDigit(digits, 10);
    digits.push(dv1);

    // Calculate check digit 2 (weights: 11 down to 2)
    const dv2 = calculateCheckDigit(digits, 11);
    digits.push(dv2);

    // Safety check against all 11 identical digits
    if (digits.every(d => d === digits[0])) {
      continue;
    }

    const unformatted = digits.join('');

    // Ensure uniqueness across test execution to prevent synthetic 409 Conflict responses
    if (ensureUnique) {
      if (generatedCpfs.has(unformatted)) {
        continue;
      }
      generatedCpfs.add(unformatted);
    }

    return isFormatted ? formatCpf(unformatted) : unformatted;
  }
}
