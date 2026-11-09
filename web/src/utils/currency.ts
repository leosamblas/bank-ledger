/**
 * Utilitários para tratamento monetário em centavos inteiros (long)
 * Premissa fundamental do Ledger: sem ponto flutuante no domínio.
 */

/**
 * Converte centavos inteiros para string BRL formatada.
 * Ex: 1542050 -> "R$ 15.420,50"
 */
export function formatCentsToBrl(cents: number, includeSymbol = true): string {
  const value = cents / 100;
  const formatted = value.toLocaleString('pt-BR', {
    style: includeSymbol ? 'currency' : 'decimal',
    currency: 'BRL',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
  return formatted;
}

/**
 * Converte centavos para formato compacto para badges ou gráficos.
 * Ex: 1542000 -> "R$ 15,4k"
 */
export function formatCentsCompact(cents: number): string {
  const value = cents / 100;
  if (value >= 1000000) {
    return `R$ ${(value / 1000000).toFixed(1).replace('.', ',')}M`;
  }
  if (value >= 1000) {
    return `R$ ${(value / 1000).toFixed(1).replace('.', ',')}k`;
  }
  return formatCentsToBrl(cents);
}

/**
 * Converte uma string formatada em BRL ou dígitos em centavos inteiros.
 * Ex: "1.540,50" -> 154050; "100" -> 10000
 */
export function parseBrlToCents(brlString: string): number {
  if (!brlString || typeof brlString !== 'string') return 0;
  // Remove tudo que não seja número ou vírgula/ponto
  const clean = brlString.replace(/[^\d,.]/g, '');
  if (!clean) return 0;

  let calculatedCents = 0;

  // Se tiver vírgula como separador decimal
  if (clean.includes(',')) {
    const [intPart, decPart = ''] = clean.split(',');
    const sanitizedInt = intPart.replace(/\./g, '');
    const sanitizedDec = (decPart + '00').substring(0, 2);
    calculatedCents = parseInt(sanitizedInt || '0', 10) * 100 + parseInt(sanitizedDec, 10);
  } else if (clean.includes('.')) {
    // Se tiver ponto como decimal
    const parts = clean.split('.');
    if (parts.length === 2 && parts[1].length <= 2) {
      const sanitizedInt = parts[0];
      const sanitizedDec = (parts[1] + '00').substring(0, 2);
      calculatedCents = parseInt(sanitizedInt || '0', 10) * 100 + parseInt(sanitizedDec, 10);
    } else {
      const onlyDigits = clean.replace(/\D/g, '');
      calculatedCents = parseInt(onlyDigits || '0', 10) * 100;
    }
  } else {
    // Se for apenas dígitos inteiros
    const onlyDigits = clean.replace(/\D/g, '');
    calculatedCents = parseInt(onlyDigits || '0', 10) * 100;
  }

  // Prevenção estrita de NaN, overflow e valores negativos
  if (!Number.isFinite(calculatedCents) || isNaN(calculatedCents) || calculatedCents < 0) {
    return 0;
  }

  return Math.min(Math.floor(calculatedCents), Number.MAX_SAFE_INTEGER);
}

