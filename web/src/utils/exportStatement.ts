import { StatementItem, BankingAccountInfo } from '@/services/types';
import { formatCentsToBrl } from './currency';
import { formatDateTimeBr } from './date';

/**
 * Mapeamento humanizado dos tipos de transação em língua portuguesa.
 */
export function getTransactionTypeLabel(type: string): string {
  switch (type) {
    case 'TRANSFER_OUT':
      return 'Transferência enviada';
    case 'TRANSFER_IN':
      return 'Transferência recebida';
    case 'DEPOSIT':
      return 'Depósito em conta corrente';
    case 'WITHDRAWAL':
      return 'Saque em terminal';
    default:
      return type;
  }
}

/**
 * Obtém ou gera o número de protocolo canônico auditável no padrão PRT-YYYYMMDD-XXXXXX.
 */
export function getCanonicalProtocol(tx: Partial<StatementItem>): string {
  if (tx.protocolNumber) {
    return tx.protocolNumber;
  }
  const dt = formatDateTimeBr(tx.timestamp || new Date().toISOString());
  const dateCompact = dt.date.replace(/\//g, '');
  const seq = String(tx.sequence ?? 0).padStart(6, '0');
  return `PRT-${dateCompact}-${seq}`;
}

/**
 * Sanitiza valores de células contra CSV Formula Injection (CWE-1236).
 * Previne que caracteres como '=', '+', '-', '@' acionem execução arbitrária em planilhas eletrônicas.
 */
function sanitizeCsvCell(value: string | number | undefined | null): string {
  if (value === null || value === undefined) return '""';
  const str = String(value).trim();
  if (/^[=+@\-\t\r]/.test(str)) {
    return `"'${str.replace(/"/g, '""')}"`;
  }
  return `"${str.replace(/"/g, '""')}"`;
}

/**
 * Gera o nome padronizado de arquivo para download: extrato-nexus-YYYYMMDD.ext
 */
export function getStatementExportFilename(
  type: 'csv' | 'ofx',
  date: Date = new Date()
): string {
  const yyyy = date.getFullYear();
  const mm = String(date.getMonth() + 1).padStart(2, '0');
  const dd = String(date.getDate()).padStart(2, '0');
  return `extrato-nexus-${yyyy}${mm}${dd}.${type}`;
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
 * Gera a string do Extrato em CSV com cabeçalho obrigatório, delimitador ';' e UTF-8 com BOM.
 * Header: Data;Protocolo;Tipo;Descrição;Contraparte;Documento;Agência/Conta;Natureza;Valor (R$);Saldo Após (R$)
 */
export function generateStatementCsv(
  statement: StatementItem[],
  account?: BankingAccountInfo
): string {
  const headers = [
    'Data',
    'Protocolo',
    'Tipo',
    'Descrição',
    'Contraparte',
    'Documento',
    'Agência/Conta',
    'Natureza',
    'Valor (R$)',
    'Saldo Após (R$)',
  ];

  const rows = statement.map((tx) => {
    const dt = formatDateTimeBr(tx.timestamp);
    const dataHora = `${dt.date} ${dt.time}`;
    const protocolo = getCanonicalProtocol(tx);
    const tipo = getTransactionTypeLabel(tx.type);
    const contraparteDoc = tx.counterpartyDocument || tx.counterpartyDocumentMasked || '';
    const natureza = tx.nature === 'CREDIT' ? 'Crédito' : 'Débito';
    const valorFormatted = `${tx.nature === 'CREDIT' ? '+' : '-'}${formatCentsToBrl(tx.amountCents, false)}`;
    const saldoFormatted = formatCentsToBrl(tx.balanceAfterCents, false);

    return [
      sanitizeCsvCell(dataHora),
      sanitizeCsvCell(protocolo),
      sanitizeCsvCell(tipo),
      sanitizeCsvCell(tx.description),
      sanitizeCsvCell(tx.counterpartyName),
      sanitizeCsvCell(contraparteDoc),
      sanitizeCsvCell(tx.counterpartyAccount),
      sanitizeCsvCell(natureza),
      sanitizeCsvCell(valorFormatted),
      sanitizeCsvCell(saldoFormatted),
    ].join(';');
  });

  // \uFEFF adiciona o Byte Order Mark (BOM) UTF-8 para compatibilidade nativa com Excel no Windows
  return '\uFEFF' + [headers.join(';'), ...rows].join('\r\n');
}

/**
 * Gera a representação OFX (Open Financial Exchange 1.0.2) estruturada
 */
export function generateStatementOfx(
  statement: StatementItem[],
  account: BankingAccountInfo
): string {
  const now = new Date();
  const nowOfx = now.toISOString().replace(/[-:T]/g, '').slice(0, 14);

  const transactionsOfx = statement
    .map((tx) => {
      const txDate = new Date(tx.timestamp);
      const dtPosted = isNaN(txDate.getTime())
        ? nowOfx
        : txDate.toISOString().replace(/[-:T]/g, '').slice(0, 14);
      const isCredit = tx.nature === 'CREDIT';
      const trnType = isCredit ? 'CREDIT' : 'DEBIT';
      const trnAmt = `${isCredit ? '' : '-'}${(tx.amountCents / 100).toFixed(2)}`;
      const safeName = (tx.counterpartyName || 'Nexus Bank').replace(/[<>&]/g, '');
      const safeMemo = (tx.description || '').replace(/[<>&]/g, '');

      return `
<STMTTRN>
<TRNTYPE>${trnType}</TRNTYPE>
<DTPOSTED>${dtPosted}[-3:BRT]</DTPOSTED>
<TRNAMT>${trnAmt}</TRNAMT>
<FITID>${tx.transactionId || tx.id}</FITID>
<CHECKNUM>${tx.sequence}</CHECKNUM>
<NAME>${safeName}</NAME>
<MEMO>${safeMemo}</MEMO>
</STMTTRN>`;
    })
    .join('');

  const latestBalanceCents = statement[0]?.balanceAfterCents ?? 0;
  const latestBalanceAmt = (latestBalanceCents / 100).toFixed(2);

  return `OFXHEADER:100
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
<FID>001</FID>
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
<BANKID>001</BANKID>
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
}

/**
 * Exporta os lançamentos em formato CSV estruturado com BOM UTF-8 e salva o arquivo
 */
export function exportStatementToCsv(
  statement: StatementItem[],
  account?: BankingAccountInfo
): void {
  const csvContent = generateStatementCsv(statement, account);
  const filename = getStatementExportFilename('csv');
  triggerBrowserDownload(csvContent, filename, 'text/csv;charset=utf-8;');
}

/**
 * Exporta os lançamentos em formato OFX e salva o arquivo
 */
export function exportStatementToOfx(
  statement: StatementItem[],
  account: BankingAccountInfo
): void {
  const ofxContent = generateStatementOfx(statement, account);
  const filename = getStatementExportFilename('ofx');
  triggerBrowserDownload(ofxContent, filename, 'application/x-ofx;charset=utf-8;');
}
