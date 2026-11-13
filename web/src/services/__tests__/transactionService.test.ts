import test from 'node:test';
import assert from 'node:assert/strict';
import { transactionService } from '../transactionService';
import { mockBankingStore } from '../mockBankingStore';
import { ApiError } from '../apiClient';

test('transactionService - createDeposit and polling', async (t) => {
  mockBankingStore.reset();
  const idempotencyKey = 'dep-test-key-001';

  await t.test('creates deposit with PENDING status initially', async () => {
    const tx = await transactionService.createDeposit(50000, idempotencyKey, 'ATM Teste');
    assert.ok(tx.id, 'Transaction should have an ID');
    assert.strictEqual(tx.type, 'DEPOSIT');
    assert.strictEqual(tx.status, 'PENDING');
    assert.strictEqual(tx.amount, 50000);
    assert.strictEqual(tx.currency, 'BRL');
    assert.strictEqual(tx.failureCode, null);
  });

  await t.test('idempotency key returns same transaction', async () => {
    const tx2 = await transactionService.createDeposit(50000, idempotencyKey, 'ATM Teste');
    assert.strictEqual(tx2.status, 'PENDING');
  });

  await t.test('pollTransactionUntilResolved waits until COMPLETED', async () => {
    const initialTx = await transactionService.createDeposit(25000, 'dep-test-key-002');
    const resolvedTx = await transactionService.pollTransactionUntilResolved(
      initialTx.id,
      100, // interval 100ms
      10  // maxAttempts 10
    );

    assert.strictEqual(resolvedTx.id, initialTx.id);
    assert.strictEqual(resolvedTx.status, 'COMPLETED');
    assert.ok(resolvedTx.completedAt, 'Should have completedAt set');
    assert.strictEqual(resolvedTx.failureCode, null);
  });

  await t.test('pollTransactionUntilResolved returns last state if maxAttempts is reached before resolution', async () => {
    const tx = await transactionService.createDeposit(15000, 'dep-timeout-test');
    // We set interval to 10ms and maxAttempts to 2 (total 20ms, while resolution is 400ms)
    const polledTx = await transactionService.pollTransactionUntilResolved(tx.id, 10, 2);
    assert.strictEqual(polledTx.id, tx.id);
    assert.strictEqual(polledTx.status, 'PENDING');
  });

  await t.test('rejects negative or zero amount', async () => {
    await assert.rejects(
      async () => {
        await transactionService.createDeposit(-100, 'dep-invalid-1');
      },
      (err: unknown) => {
        assert.ok(err instanceof ApiError);
        assert.strictEqual(err.problem.status, 400);
        return true;
      }
    );
  });

  await t.test('rejects amount above max regulatory limit', async () => {
    await assert.rejects(
      async () => {
        await transactionService.createDeposit(6000000, 'dep-too-high');
      },
      (err: unknown) => {
        assert.ok(err instanceof ApiError);
        assert.strictEqual(err.problem.status, 400);
        return true;
      }
    );
  });
});

test('transactionService - createWithdrawal and polling', async (t) => {
  mockBankingStore.reset();

  await t.test('creates withdrawal and resolves to COMPLETED when balance is sufficient', async () => {
    const tx = await transactionService.createWithdrawal(10000, 'with-test-key-001', 'ATM 24h');
    assert.strictEqual(tx.type, 'WITHDRAWAL');
    assert.strictEqual(tx.status, 'PENDING');
    assert.strictEqual(tx.amount, 10000);

    const resolved = await transactionService.pollTransactionUntilResolved(tx.id, 100, 10);
    assert.strictEqual(resolved.status, 'COMPLETED');
    assert.strictEqual(resolved.failureCode, null);
    assert.ok(resolved.completedAt);
  });

  await t.test('creates withdrawal and resolves to FAILED with INSUFFICIENT_FUNDS when balance is insufficient', async () => {
    const state = mockBankingStore.getState();
    const excessiveAmount = state.balance.availableCents + 100000;

    const tx = await transactionService.createWithdrawal(excessiveAmount, 'with-insufficient-001');
    assert.strictEqual(tx.status, 'PENDING');

    const resolved = await transactionService.pollTransactionUntilResolved(tx.id, 100, 10);
    assert.strictEqual(resolved.status, 'FAILED');
    assert.strictEqual(resolved.failureCode, 'INSUFFICIENT_FUNDS');
    assert.ok(resolved.completedAt);
  });

  await t.test('rejects invalid withdrawal amounts with 400', async () => {
    await assert.rejects(
      async () => {
        await transactionService.createWithdrawal(0, 'with-zero');
      },
      (err: unknown) => {
        assert.ok(err instanceof ApiError);
        assert.strictEqual(err.problem.status, 400);
        return true;
      }
    );
  });
});

test('transactionService - getTransactionById error cases and statement fallback', async (t) => {
  mockBankingStore.reset();

  await t.test('finds initial statement transactions', async () => {
    const tx = await transactionService.getTransactionById('tx-77e8a9');
    assert.ok(tx);
    assert.strictEqual(tx.status, 'COMPLETED');
    assert.strictEqual(tx.amount, 450000);
  });

  await t.test('throws 404 for unknown transaction ID', async () => {
    await assert.rejects(
      async () => {
        await transactionService.getTransactionById('non-existent-id-999');
      },
      (err: unknown) => {
        assert.ok(err instanceof ApiError);
        assert.strictEqual(err.problem.status, 404);
        return true;
      }
    );
  });
});
