import test from 'node:test';
import assert from 'node:assert/strict';

test('Skeleton renders with aria-busy and accessible loading attributes', async () => {
  const { getSkeletonAriaAttributes } = await import('../../components/ui/Skeleton');

  const attrs = getSkeletonAriaAttributes('Carregando informações financeiras');
  assert.strictEqual(attrs['aria-busy'], true);
  assert.strictEqual(attrs['aria-label'], 'Carregando informações financeiras');
});
