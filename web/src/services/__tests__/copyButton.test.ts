import test from 'node:test';
import assert from 'node:assert/strict';

test('CopyButton helper returns formatted aria-label', async () => {
  const { getCopyAriaLabel } = await import('../../components/ui/CopyButton');

  assert.strictEqual(getCopyAriaLabel('ID da Transação'), 'Copiar ID da Transação');
  assert.strictEqual(getCopyAriaLabel('Chave PIX'), 'Copiar Chave PIX');
});
