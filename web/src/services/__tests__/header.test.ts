import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

test('Header.tsx uses semantic theme tokens and no hardcoded dark navy hexes', () => {
  const headerPath = path.resolve(process.cwd(), 'src/components/ui/Header.tsx');
  const content = fs.readFileSync(headerPath, 'utf-8');

  // Must not have hardcoded #0e1c2f or #1a2d47
  assert.ok(!content.includes('#0e1c2f'), 'Should not contain hardcoded hex #0e1c2f');
  assert.ok(!content.includes('#1a2d47'), 'Should not contain hardcoded hex #1a2d47');
  assert.ok(content.includes('bg-primary-container'), 'Should use semantic bg-primary-container token');
});
