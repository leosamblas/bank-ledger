import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

test('signup page and OpenAccountForm remove redundant breadcrumbs and duplicate bottom links', () => {
  const signupPath = path.resolve(process.cwd(), 'src/app/signup/page.tsx');
  const formPath = path.resolve(process.cwd(), 'src/components/forms/OpenAccountForm.tsx');

  const signupContent = fs.readFileSync(signupPath, 'utf-8');
  const formContent = fs.readFileSync(formPath, 'utf-8');

  // Breadcrumbs should be removed
  assert.ok(!signupContent.includes('Navegação estrutural'), 'Should not contain breadcrumbs nav');
  // Redundant bottom links in OpenAccountForm should be removed
  assert.ok(!formContent.includes('Já possui uma conta?'), 'Should not contain duplicate login prompt at bottom');
  assert.ok(!formContent.includes('Voltar para a página inicial'), 'Should not contain duplicate home link at bottom');
});
