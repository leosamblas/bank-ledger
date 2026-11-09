import { StatementItem, BankingAccountInfo } from '@/services/types';
import { formatCentsToBrl } from './currency';
import { formatDateTimeBr } from './date';

/**
 * Sanitiza valores de células contra CSV Formula Injection (CWE-1236).
 * Previne que caracteres como '=', '+', '-', '@' acionem execução arbitrária no Excel.
 */
function sanitizeCsvCell(value: string | number): string {
  if (value === null || value === undefined) return '';
  const str = String(value).trim();
  if (/^[=+@\-\t\r]/.test(str)) {
    return `"'${str.replace(/"/g, '""')}"`;
  }
  return `"${str.replace(/"/g, '""')}"`;
}

/**
 * Dispara o download de um arquivo no navegador cliente
 */
function triggerBrowserDownload(content: string, filename: string, mimeType: string) {
  if (typeof window === 'undefined') return;
  const blob = new Blob([content], { type: mimeType });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

/**
 * Exporta os lançamentos em formato CSV estruturado com BOM UTF-8
 */
export function exportStatementToCsv(
  statement: StatementItem[],
  account: BankingAccountInfo,
  filenamePrefix = 'extrato_nexus'
): void {
  const headers = [
    'Data',
    'Hora',
    'Protocolo',
    'Descricao',
    'Contraparte',
    'Agencia/Conta Contraparte',
    'Tipo',
    'Natureza',
    'Valor (BRL)',
    'Saldo Resultante (BRL)',
  ];

  const rows = statement.map((tx) => {
    const dt = formatDateTimeBr(tx.timestamp);
    const valorFormatted = `${tx.nature === 'CREDIT' ? '+' : '-'}${formatCentsToBrl(tx.amountCents, false)}`;
    const saldoFormatted = formatCentsToBrl(tx.balanceAfterCents, false);

    return [
      sanitizeCsvCell(dt.date),
      sanitizeCsvCell(dt.time),
      sanitizeCsvCell(tx.sequence),
      sanitizeCsvCell(tx.description),
      sanitizeCsvCell(tx.counterpartyName),
      sanitizeCsvCell(tx.counterpartyAccount),
      sanitizeCsvCell(tx.type),
      sanitizeCsvCell(tx.nature === 'CREDIT' ? 'Credito' : 'Debito'),
      sanitizeCsvCell(valorFormatted),
      sanitizeCsvCell(saldoFormatted),
    ].join(';');
  });

  // \uFEFF adiciona o Byte Order Mark (BOM) UTF-8 para compatibilidade perfeita com Excel no Windows
  const csvContent = '\uFEFF' + [headers.join(';'), ...rows].join('\r\n');
  const nowStr = new Date().toISOString().slice(0, 10).replace(/-/g, '');
  const filename = `${filenamePrefix}_ag${account.agency}_cc${account.accountNumber}_${nowStr}.csv`;

  triggerBrowserDownload(csvContent, filename, 'text/csv;charset=utf-8;');
}

/**
 * Exporta os lançamentos em formato OFX (Open Financial Exchange 1.0.2)
 * Padrão aceito por softwares de contabilidade, ERPs e conciliadores
 */
export function exportStatementToOfx(
  statement: StatementItem[],
  account: BankingAccountInfo,
  filenamePrefix = 'extrato_nexus'
): void {
  const now = new Date();
  const nowOfx = now.toISOString().replace(/[-:T]/g, '').slice(0, 14);

  const transactionsOfx = statement
    .map((tx) => {
      const txDate = new Date(tx.timestamp);
      const dtPosted = txDate.toISOString().replace(/[-:T]/g, '').slice(0, 14);
      const isCredit = tx.nature === 'CREDIT';
      const trnType = isCredit ? 'CREDIT' : 'DEBIT';
      const trnAmt = `${isCredit ? '' : '-'}${(tx.amountCents / 100).toFixed(2)}`;

      return `
<STMTTRN>
<TRNTYPE>${trnType}</TRNTYPE>
<DTPOSTED>${dtPosted}[-3:BRT]</DTPOSTED>
<TRNAMT>${trnAmt}</TRNAMT>
<FITID>${tx.transactionId}</FITID>
<CHECKNUM>${tx.sequence}</CHECKNUM>
<MEMO>${tx.description.replace(/[<>]/g, '')}</MEMO>
</STMTTRN>`;
    })
    .join('');

  const latestBalanceCents = statement[0]?.balanceAfterCents ?? 0;
  const latestBalanceAmt = (latestBalanceCents / 100).toFixed(2);

  const ofxContent = `OFXHEADER:100
DATA:OFXSGML
VERSION:102
SECURITY:NONE
ENCODING:USASCII
CHARSET:1252
COMPRESSION:NONE
OLDFILEUID:NONE
NEWFILEUID:NONE

<OFX>
<SIGNONMSGSRSV1>
<SONRS>
<STATUS>
<CODE>0</CODE>
<SEVERITY>INFO</SEVERITY>
</STATUS>
<DTSERVER>${nowOfx}[-3:BRT]</DTSERVER>
<LANGUAGE>POR</LANGUAGE>
<FI>
<ORG>Nexus Bank S.A.</ORG>
<FID>0418</FID>
</FI>
</SONRS>
</SIGNONMSGSRSV1>
<BANKMSGSRSV1>
<STMTTRNRS>
<TRNUID>1001</TRNUID>
<STATUS>
<CODE>0</CODE>
<SEVERITY>INFO</SEVERITY>
</STATUS>
<STMTRS>
<CURDEF>BRL</CURDEF>
<BANKACCTFROM>
<BANKID>0418</BANKID>
<BRANCHID>${account.agency}</BRANCHID>
<ACCTID>${account.accountNumber}${account.digit}</ACCTID>
<ACCTTYPE>CHECKING</ACCTTYPE>
</BANKACCTFROM>
<BANKTRANLIST>
<DTSTART>${nowOfx}</DTSTART>
<DTEND>${nowOfx}</DTEND>${transactionsOfx}
</BANKTRANLIST>
<LEDGERBAL>
<BALAMT>${latestBalanceAmt}</BALAMT>
<DTASOF>${nowOfx}[-3:BRT]</DTASOF>
</LEDGERBAL>
</STMTRS>
</STMTTRNRS>
</BANKMSGSRSV1>
</OFX>`;

  const nowStr = now.toISOString().slice(0, 10).replace(/-/g, '');
  const filename = `${filenamePrefix}_ag${account.agency}_cc${account.accountNumber}_${nowStr}.ofx`;

  triggerBrowserDownload(ofxContent, filename, 'application/x-ofx;charset=utf-8;');
}
