import test from 'node:test';
import assert from 'node:assert/strict';

test('formUtils returns correct input type and accessible aria-labels', async () => {
  const { getPasswordInputType, getPasswordToggleAriaLabel } = await import('../../utils/formUtils');

  assert.strictEqual(getPasswordInputType(false), 'password');
  assert.strictEqual(getPasswordInputType(true), 'text');

  assert.strictEqual(getPasswordToggleAriaLabel('Senha de Acesso', false), 'Exibir Senha de Acesso');
  assert.strictEqual(getPasswordToggleAriaLabel('Senha de Acesso', true), 'Ocultar Senha de Acesso');
  assert.strictEqual(getPasswordToggleAriaLabel('Confirmar Senha', false), 'Exibir Confirmar Senha');
});
