import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

test('layout.tsx contains deduplicated font stylesheets and viewport configuration', () => {
  const layoutPath = path.resolve(process.cwd(), 'src/app/layout.tsx');
  const content = fs.readFileSync(layoutPath, 'utf-8');

  // Must not have duplicate Material Symbols stylesheets
  const matches = content.match(/fonts\.googleapis\.com\/css2\?family=Material\+Symbols\+Outlined/g) || [];
  assert.strictEqual(matches.length, 1, 'Should have exactly one Material Symbols font stylesheet link');
});
