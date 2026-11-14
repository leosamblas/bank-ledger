import test from 'node:test';
import assert from 'node:assert/strict';
import { runLoadTest } from '../src/runner.js';

test('runLoadTest respects concurrency limit and executes exact request count', async () => {
  let activeConcurrency = 0;
  let maxObservedConcurrency = 0;
  let totalCalls = 0;

  const mockFetch = async () => {
    activeConcurrency++;
    if (activeConcurrency > maxObservedConcurrency) {
      maxObservedConcurrency = activeConcurrency;
    }
    totalCalls++;
    await new Promise((r) => setTimeout(r, 20));
    activeConcurrency--;
    return {
      status: 201,
      ok: true,
      headers: new Headers({ Location: '/api/v1/accounts/123' }),
      json: async () => ({ id: '123' }),
      text: async () => JSON.stringify({ id: '123' }),
    };
  };

  const concurrency = 4;
  const totalRequests = 20;
  const summary = await runLoadTest({
    url: 'http://localhost:8081/api/v1/accounts',
    concurrency,
    totalRequests,
    fetchFn: mockFetch,
  });

  assert.equal(totalCalls, totalRequests);
  assert.equal(summary.totalRequests, totalRequests);
  assert.equal(summary.successfulRequests, totalRequests);
  assert.equal(summary.failedRequests, 0);
  assert.ok(
    maxObservedConcurrency <= concurrency,
    `Max concurrency ${maxObservedConcurrency} exceeded ${concurrency}`
  );
  assert.ok(maxObservedConcurrency > 1, `Max concurrency should be > 1, observed ${maxObservedConcurrency}`);
});

test('runLoadTest handles network errors gracefully without crashing', async () => {
  let callCount = 0;
  const mockFetch = async () => {
    callCount++;
    if (callCount % 2 === 0) {
      throw new Error('ECONNREFUSED: Connection refused');
    }
    return {
      status: 201,
      ok: true,
      text: async () => '{"status":"ok"}',
    };
  };

  const summary = await runLoadTest({
    url: 'http://localhost:8081/api/v1/accounts',
    concurrency: 3,
    totalRequests: 10,
    fetchFn: mockFetch,
  });

  assert.equal(summary.totalRequests, 10);
  assert.equal(summary.successfulRequests, 5);
  assert.equal(summary.failedRequests, 5);
  assert.equal(summary.statusCodes['201'], 5);
  assert.equal(summary.statusCodes['ERR'], 5);
});

test('runLoadTest tracks HTTP error response statuses', async () => {
  let callCount = 0;
  const mockFetch = async () => {
    callCount++;
    const status = callCount % 2 === 0 ? 500 : 201;
    return {
      status,
      ok: status < 400,
      text: async () => status === 500 ? 'Internal Server Error' : '{"id":"1"}',
    };
  };

  const summary = await runLoadTest({
    url: 'http://localhost:8081/api/v1/accounts',
    concurrency: 2,
    totalRequests: 6,
    fetchFn: mockFetch,
  });

  assert.equal(summary.totalRequests, 6);
  assert.equal(summary.successfulRequests, 3);
  assert.equal(summary.failedRequests, 3);
  assert.equal(summary.statusCodes['201'], 3);
  assert.equal(summary.statusCodes['500'], 3);
});

test('runLoadTest sends POST request with Content-Type application/json and generated payload', async () => {
  const capturedRequests = [];
  const customPayload = { test: 'custom-data', cpf: '12345678901' };

  const mockFetch = async (url, options) => {
    capturedRequests.push({ url, options });
    return {
      status: 201,
      ok: true,
      text: async () => '{}',
    };
  };

  await runLoadTest({
    url: 'http://localhost:8081/api/v1/accounts',
    concurrency: 2,
    totalRequests: 4,
    fetchFn: mockFetch,
    payloadGenerator: () => customPayload,
  });

  assert.equal(capturedRequests.length, 4);
  for (const req of capturedRequests) {
    assert.equal(req.url, 'http://localhost:8081/api/v1/accounts');
    assert.equal(req.options.method, 'POST');
    assert.equal(req.options.headers['Content-Type'], 'application/json');
    assert.deepEqual(JSON.parse(req.options.body), customPayload);
  }
});

test('runLoadTest uses default generateCustomerPayload when payloadGenerator is omitted', async () => {
  const capturedBodies = [];
  const mockFetch = async (_url, options) => {
    capturedBodies.push(JSON.parse(options.body));
    return {
      status: 201,
      ok: true,
      text: async () => '{}',
    };
  };

  await runLoadTest({
    url: 'http://localhost:8081/api/v1/accounts',
    concurrency: 2,
    totalRequests: 3,
    fetchFn: mockFetch,
  });

  assert.equal(capturedBodies.length, 3);
  for (const body of capturedBodies) {
    assert.ok(body.fullName);
    assert.ok(body.cpf);
    assert.ok(body.email);
    assert.ok(body.phone);
    assert.ok(body.birthDate);
    assert.ok(body.password);
  }
});

test('runLoadTest handles totalRequests = 0 gracefully', async () => {
  let called = false;
  const mockFetch = async () => {
    called = true;
    return { status: 200, ok: true, text: async () => '' };
  };

  const summary = await runLoadTest({
    url: 'http://localhost:8081/api/v1/accounts',
    concurrency: 5,
    totalRequests: 0,
    fetchFn: mockFetch,
  });

  assert.equal(called, false);
  assert.equal(summary.totalRequests, 0);
  assert.equal(summary.successfulRequests, 0);
  assert.equal(summary.failedRequests, 0);
});

test('runLoadTest handles concurrency > totalRequests cleanly', async () => {
  let callCount = 0;
  const mockFetch = async () => {
    callCount++;
    return { status: 201, ok: true, text: async () => '' };
  };

  const summary = await runLoadTest({
    url: 'http://localhost:8081/api/v1/accounts',
    concurrency: 10,
    totalRequests: 3,
    fetchFn: mockFetch,
  });

  assert.equal(callCount, 3);
  assert.equal(summary.totalRequests, 3);
});

test('runLoadTest handles throwing payloadGenerator gracefully', async () => {
  const throwingGenerator = () => {
    throw new Error('Generator failure');
  };

  const summary = await runLoadTest({
    url: 'http://localhost:8081/api/v1/accounts',
    concurrency: 2,
    totalRequests: 4,
    payloadGenerator: throwingGenerator,
  });

  assert.equal(summary.totalRequests, 4);
  assert.equal(summary.failedRequests, 4);
  assert.equal(summary.successfulRequests, 0);
  assert.equal(summary.statusCodes['ERR'], 4);
});

test('runLoadTest supports custom HTTP headers and method', async () => {
  let capturedHeaders = null;
  let capturedMethod = null;

  const mockFetch = async (_url, options) => {
    capturedHeaders = options.headers;
    capturedMethod = options.method;
    return { status: 200, ok: true, text: async () => '' };
  };

  await runLoadTest({
    url: 'http://localhost:8081/api/v1/accounts',
    concurrency: 1,
    totalRequests: 1,
    fetchFn: mockFetch,
    method: 'PUT',
    headers: {
      'X-Custom-Header': 'test-val',
    },
  });

  assert.equal(capturedMethod, 'PUT');
  assert.equal(capturedHeaders['Content-Type'], 'application/json');
  assert.equal(capturedHeaders['X-Custom-Header'], 'test-val');
});

