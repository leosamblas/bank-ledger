/**
 * Utilitários para validação e formatação de CPF (Cadastro de Pessoas Físicas)
 * Baseado no algoritmo oficial da Receita Federal do Brasil (Módulo 11)
 */

export function cleanCpf(cpf: string): string {
  return cpf.replace(/\D/g, '');
}

export function formatCpf(value: string): string {
  const digits = cleanCpf(value).slice(0, 11);

  if (digits.length > 9) {
    return digits.replace(/^(\d{3})(\d{3})(\d{3})(\d{0,2})/, '$1.$2.$3-$4');
  }
  if (digits.length > 6) {
    return digits.replace(/^(\d{3})(\d{3})(\d{0,3})/, '$1.$2.$3');
  }
  if (digits.length > 3) {
    return digits.replace(/^(\d{3})(\d{0,3})/, '$1.$2');
  }
  return digits;
}

/**
 * Validação algorítmica matemática oficial do CPF (Módulo 11)
 * Verifica os dois dígitos verificadores e rejeita sequências repetidas.
 */
export function isValidCpf(cpf: string): boolean {
  const digits = cleanCpf(cpf);

  // Deve ter exatamente 11 dígitos numéricos
  if (digits.length !== 11) {
    return false;
  }

  // Rejeita sequências de dígitos iguais conhecidas (ex: 000.000.000-00, 111.111.111-11...)
  if (/^(\d)\1{10}$/.test(digits)) {
    return false;
  }

  // Cálculo e verificação do primeiro dígito verificador
  let sum = 0;
  for (let i = 0; i < 9; i++) {
    sum += parseInt(digits.charAt(i), 10) * (10 - i);
  }
  let rest = 11 - (sum % 11);
  let digit1 = rest >= 10 ? 0 : rest;

  if (digit1 !== parseInt(digits.charAt(9), 10)) {
    return false;
  }

  // Cálculo e verificação do segundo dígito verificador
  sum = 0;
  for (let i = 0; i < 10; i++) {
    sum += parseInt(digits.charAt(i), 10) * (11 - i);
  }
  rest = 11 - (sum % 11);
  let digit2 = rest >= 10 ? 0 : rest;

  return digit2 === parseInt(digits.charAt(10), 10);
}

export type CpfValidationState = 'empty' | 'incomplete' | 'valid' | 'invalid';

export interface CpfValidationResult {
  state: CpfValidationState;
  isValid: boolean;
  message: string;
}

export function validateCpfStatus(cpf: string): CpfValidationResult {
  const digits = cleanCpf(cpf);

  if (digits.length === 0) {
    return {
      state: 'empty',
      isValid: false,
      message: 'Informe o CPF do titular.',
    };
  }

  if (digits.length < 11) {
    return {
      state: 'incomplete',
      isValid: false,
      message: `Digite os 11 dígitos (${digits.length}/11).`,
    };
  }

  const valid = isValidCpf(digits);
  if (valid) {
    return {
      state: 'valid',
      isValid: true,
      message: 'CPF autêntico e verificado pelo algoritmo da Receita Federal.',
    };
  }

  return {
    state: 'invalid',
    isValid: false,
    message: 'CPF inválido. Verifique os dígitos informados.',
  };
}
