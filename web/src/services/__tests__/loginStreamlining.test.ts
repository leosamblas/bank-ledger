import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

test('login/page.tsx removes redundant breadcrumb and duplicate footer exit links', () => {
  const loginPath = path.resolve(process.cwd(), 'src/app/login/page.tsx');
  const content = fs.readFileSync(loginPath, 'utf-8');

  // Breadcrumbs should be removed
  assert.ok(!content.includes('Navegação estrutural'), 'Should not contain breadcrumbs nav');
  // Redundant "Voltar para a página inicial" below card should be removed
  assert.ok(!content.includes('Voltar para a página inicial'), 'Should not contain duplicate home link at bottom');
});
