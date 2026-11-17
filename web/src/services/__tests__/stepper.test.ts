import test from 'node:test';
import assert from 'node:assert/strict';

test('Stepper step computation returns correct active and completed states', async () => {
  const { computeStepState } = await import('../../components/ui/Stepper');

  assert.strictEqual(computeStepState(1, 1), 'active');
  assert.strictEqual(computeStepState(2, 1), 'upcoming');
  assert.strictEqual(computeStepState(1, 2), 'completed');
  assert.strictEqual(computeStepState(2, 3), 'completed');
});
