import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

test('web/package.json uses secure Next.js version >= 16.4.6', () => {
  const pkgPath = path.resolve(process.cwd(), 'package.json');
  const pkg = JSON.parse(fs.readFileSync(pkgPath, 'utf-8'));

  const nextVersion = pkg.dependencies?.next || '';
  assert.ok(
    nextVersion === '16.4.6' || nextVersion === '^16.4.6',
    `Expected next to be 16.4.6, found: ${nextVersion}`
  );

  const eslintConfigNext = pkg.devDependencies?.['eslint-config-next'] || '';
  assert.ok(
    eslintConfigNext === '16.4.6' || eslintConfigNext === '^16.4.6',
    `Expected eslint-config-next to be 16.4.6, found: ${eslintConfigNext}`
  );
});
