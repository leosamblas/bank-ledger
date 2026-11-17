import test from 'node:test';
import assert from 'node:assert/strict';

test('BottomNav items definitions match core Web Banking routes', async () => {
  const { BOTTOM_NAV_ITEMS } = await import('../../components/banking/BottomNav');

  assert.strictEqual(BOTTOM_NAV_ITEMS.length, 4);
  const hrefs = BOTTOM_NAV_ITEMS.map((i) => i.href);
  assert.ok(hrefs.includes('/dashboard'));
  assert.ok(hrefs.includes('/dashboard/transfer'));
  assert.ok(hrefs.includes('/dashboard/operations'));
  assert.ok(hrefs.includes('/dashboard/statement'));
});
