import test from 'node:test';
import assert from 'node:assert/strict';
import { MetricsTracker } from '../src/metrics.js';

test('MetricsTracker correctly aggregates latencies, percentiles, and status codes', () => {
  const tracker = new MetricsTracker();
  tracker.start();

  // Record 100 sample latencies from 1ms to 100ms
  for (let i = 1; i <= 100; i++) {
    tracker.record(i, i % 10 === 0 ? 500 : 201, null);
  }
  tracker.stop();

  const summary = tracker.summary();
  assert.equal(summary.totalRequests, 100);
  assert.equal(summary.successfulRequests, 90);
  assert.equal(summary.failedRequests, 10);
  assert.equal(summary.statusCodes['201'], 90);
  assert.equal(summary.statusCodes['500'], 10);
  assert.equal(summary.min, 1);
  assert.equal(summary.max, 100);
  assert.equal(summary.mean, 50.5);
  assert.equal(summary.p50, 50);
  assert.equal(summary.p90, 90);
  assert.equal(summary.p95, 95);
  assert.equal(summary.p99, 99);
  assert.ok(summary.durationMs >= 0);
  assert.ok(summary.rps >= 0);
});

test('MetricsTracker returns zeros gracefully when no requests are recorded', () => {
  const tracker = new MetricsTracker();
  tracker.start();
  tracker.stop();

  const summary = tracker.summary();
  assert.equal(summary.totalRequests, 0);
  assert.equal(summary.successfulRequests, 0);
  assert.equal(summary.failedRequests, 0);
  assert.deepEqual(summary.statusCodes, {});
  assert.equal(summary.min, 0);
  assert.equal(summary.max, 0);
  assert.equal(summary.mean, 0);
  assert.equal(summary.p50, 0);
  assert.equal(summary.p90, 0);
  assert.equal(summary.p95, 0);
  assert.equal(summary.p99, 0);
  assert.equal(summary.rps, 0);
  assert.ok(summary.durationMs >= 0);
});

test('MetricsTracker handles single request and unsorted latencies', () => {
  const tracker = new MetricsTracker();
  tracker.start();
  tracker.record(42, 200, null);
  tracker.stop();

  const summary = tracker.summary();
  assert.equal(summary.totalRequests, 1);
  assert.equal(summary.successfulRequests, 1);
  assert.equal(summary.failedRequests, 0);
  assert.equal(summary.min, 42);
  assert.equal(summary.max, 42);
  assert.equal(summary.mean, 42);
  assert.equal(summary.p50, 42);
  assert.equal(summary.p90, 42);
  assert.equal(summary.p95, 42);
  assert.equal(summary.p99, 42);
});

test('MetricsTracker handles network errors with ERR status code', () => {
  const tracker = new MetricsTracker();
  tracker.start();
  tracker.record(15, 0, new Error('ECONNREFUSED'));
  tracker.record(25, 200, null);
  tracker.record(30, 404, null);
  tracker.stop();

  const summary = tracker.summary();
  assert.equal(summary.totalRequests, 3);
  assert.equal(summary.successfulRequests, 1);
  assert.equal(summary.failedRequests, 2);
  assert.equal(summary.statusCodes['ERR'], 1);
  assert.equal(summary.statusCodes['200'], 1);
  assert.equal(summary.statusCodes['404'], 1);
});

test('MetricsTracker works without explicit start/stop calls', () => {
  const tracker = new MetricsTracker();
  tracker.record(10, 200, null);
  const summary = tracker.summary();
  assert.equal(summary.totalRequests, 1);
  assert.equal(summary.successfulRequests, 1);
  assert.ok(summary.durationMs >= 0);
  assert.ok(summary.rps >= 0);
});
