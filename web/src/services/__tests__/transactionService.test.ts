import test from 'node:test';
import assert from 'node:assert/strict';
import { transactionService } from '../transactionService';
import { ApiError } from '../apiClient';
import { TransactionResponse } from '../types';

function createMockResponse(body: unknown, status = 200, statusText = 'OK'): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    statusText,
    headers: new Headers({ 'content-type': 'application/json' }),
    json: async () => body,
    text: async () => JSON.stringify(body),
  } as unknown as Response;
}

test('transactionService - createDeposit and polling', async (t) => {
  const originalFetch = globalThis.fetch;

  t.afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  await t.test('creates deposit with correct POST payload and headers', async () => {
    let capturedUrl = '';
    let capturedOptions: RequestInit | undefined;

    const mockTx: TransactionResponse = {
      id: 'tx-dep-100',
      type: 'DEPOSIT',
      status: 'PENDING',
      sourceAccountId: null,
      destinationAccountId: 'acc-dest-1',
      amount: 50000,
      currency: 'BRL',
      failureCode: null,
      createdAt: '2026-11-14T20:00:00.000Z',
    };

    globalThis.fetch = (async (url: string | URL | Request, options?: RequestInit) => {
      capturedUrl = String(url);
      capturedOptions = options;
      return createMockResponse(mockTx, 202);
    }) as typeof fetch;

    const tx = await transactionService.createDeposit(50000, 'dep-test-key-001', 'ATM Teste');

    assert.ok(capturedUrl.includes('/transactions/deposits'));
    assert.strictEqual(capturedOptions?.method, 'POST');
    const headers = capturedOptions?.headers as Record<string, string>;
    assert.strictEqual(headers['Idempotency-Key'], 'dep-test-key-001');
    assert.deepStrictEqual(JSON.parse(capturedOptions?.body as string), {
      amount: 50000,
      description: 'ATM Teste',
    });

    assert.strictEqual(tx.id, 'tx-dep-100');
    assert.strictEqual(tx.type, 'DEPOSIT');
    assert.strictEqual(tx.status, 'PENDING');
    assert.strictEqual(tx.amount, 50000);
  });

  await t.test('pollTransactionUntilResolved polls until status is COMPLETED', async () => {
    let callCount = 0;

    globalThis.fetch = (async (url: string | URL | Request) => {
      const urlStr = String(url);
      if (urlStr.includes('/transactions/tx-poll-1')) {
        callCount++;
        const status = callCount >= 2 ? 'COMPLETED' : 'PENDING';
        return createMockResponse({
          id: 'tx-poll-1',
          type: 'DEPOSIT',
          status,
          amount: 25000,
          currency: 'BRL',
          createdAt: '2026-11-14T20:00:00.000Z',
          completedAt: status === 'COMPLETED' ? '2026-11-14T20:00:01.000Z' : null,
        });
      }
      return createMockResponse({}, 404);
    }) as typeof fetch;

    const resolved = await transactionService.pollTransactionUntilResolved('tx-poll-1', 10, 5);
    assert.strictEqual(resolved.id, 'tx-poll-1');
    assert.strictEqual(resolved.status, 'COMPLETED');
    assert.ok(callCount >= 2, 'Should have polled at least twice');
  });

  await t.test('pollTransactionUntilResolved returns last state if maxAttempts is reached', async () => {
    globalThis.fetch = (async () => {
      return createMockResponse({
        id: 'tx-timeout',
        type: 'DEPOSIT',
        status: 'PENDING',
        amount: 15000,
        currency: 'BRL',
        createdAt: '2026-11-14T20:00:00.000Z',
      });
    }) as typeof fetch;

    const polledTx = await transactionService.pollTransactionUntilResolved('tx-timeout', 10, 2);
    assert.strictEqual(polledTx.id, 'tx-timeout');
    assert.strictEqual(polledTx.status, 'PENDING');
  });
});

test('transactionService - createWithdrawal and createTransfer', async (t) => {
  const originalFetch = globalThis.fetch;

  t.afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  await t.test('creates withdrawal with correct POST payload', async () => {
    let capturedUrl = '';
    let capturedOptions: RequestInit | undefined;

    globalThis.fetch = (async (url: string | URL | Request, options?: RequestInit) => {
      capturedUrl = String(url);
      capturedOptions = options;
      return createMockResponse({
        id: 'tx-with-01',
        type: 'WITHDRAWAL',
        status: 'PENDING',
        amount: 10000,
        currency: 'BRL',
        createdAt: '2026-11-14T20:00:00.000Z',
      }, 202);
    }) as typeof fetch;

    const tx = await transactionService.createWithdrawal(10000, 'with-key-001', 'ATM 24h');
    assert.ok(capturedUrl.includes('/transactions/withdrawals'));
    assert.strictEqual(capturedOptions?.method, 'POST');
    assert.strictEqual(tx.type, 'WITHDRAWAL');
    assert.strictEqual(tx.amount, 10000);
  });

  await t.test('creates transfer with destinationAccountId and Idempotency-Key', async () => {
    let capturedUrl = '';
    let capturedOptions: RequestInit | undefined;

    globalThis.fetch = (async (url: string | URL | Request, options?: RequestInit) => {
      capturedUrl = String(url);
      capturedOptions = options;
      return createMockResponse({
        id: 'tx-transfer-01',
        type: 'TRANSFER',
        status: 'PENDING',
        destinationAccountId: 'dest-account-uuid',
        amount: 30000,
        currency: 'BRL',
        createdAt: '2026-11-14T20:00:00.000Z',
      }, 202);
    }) as typeof fetch;

    const tx = await transactionService.createTransfer(
      'dest-account-uuid',
      30000,
      'transfer-key-001',
      'Pix fornecedor'
    );

    assert.ok(capturedUrl.includes('/transactions/transfers'));
    assert.strictEqual(capturedOptions?.method, 'POST');
    const headers = capturedOptions?.headers as Record<string, string>;
    assert.strictEqual(headers['Idempotency-Key'], 'transfer-key-001');
    assert.deepStrictEqual(JSON.parse(capturedOptions?.body as string), {
      destinationAccountId: 'dest-account-uuid',
      amount: 30000,
      description: 'Pix fornecedor',
    });
    assert.strictEqual(tx.type, 'TRANSFER');
    assert.strictEqual(tx.amount, 30000);
  });
});

test('transactionService - getTransactionById error cases', async (t) => {
  const originalFetch = globalThis.fetch;

  t.afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  await t.test('throws ApiError 404 for unknown transaction ID', async () => {
    globalThis.fetch = (async () => {
      return createMockResponse(
        {
          status: 404,
          title: 'Not Found',
          detail: 'Transaction not found',
        },
        404,
        'Not Found'
      );
    }) as typeof fetch;

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
