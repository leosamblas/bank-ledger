import test from 'node:test';
import assert from 'node:assert/strict';

test('maskFinancialValue masks monetary string when private is true', async () => {
  const { maskFinancialValue } = await import('../../components/banking/PrivacyToggle');

  const visible = 'R$ 15.420,50';
  assert.strictEqual(maskFinancialValue(visible, false), 'R$ 15.420,50');
  assert.strictEqual(maskFinancialValue(visible, true), 'R$ ••••••••');
});

test('maskFinancialValue handles strings without currency symbol and edge cases', async () => {
  const { maskFinancialValue } = await import('../../components/banking/PrivacyToggle');

  assert.strictEqual(maskFinancialValue('1.500,00', false), '1.500,00');
  assert.strictEqual(maskFinancialValue('1.500,00', true), '••••••••');
  assert.strictEqual(maskFinancialValue('', false), '');
  assert.strictEqual(maskFinancialValue('', true), '');
});
