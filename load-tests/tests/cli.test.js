import test from 'node:test';
import assert from 'node:assert/strict';
import { parseArgs, formatSummary } from '../src/cli.js';

test('parseArgs parses short and long flags for concurrency and requests', () => {
  const parsed1 = parseArgs(['-c', '25', '-n', '500']);
  assert.equal(parsed1.concurrency, 25);
  assert.equal(parsed1.requests, 500);

  const parsed2 = parseArgs(['--concurrency', '15', '--requests', '300']);
  assert.equal(parsed2.concurrency, 15);
  assert.equal(parsed2.requests, 300);

  const parsedEqualSyntax = parseArgs(['--concurrency=30', '--requests=600']);
  assert.equal(parsedEqualSyntax.concurrency, 30);
  assert.equal(parsedEqualSyntax.requests, 600);

  const defaults = parseArgs([]);
  assert.equal(defaults.concurrency, 10);
  assert.equal(defaults.requests, 100);
  assert.equal(defaults.url, process.env.TARGET_URL || 'http://localhost:8081/api/v1/accounts');
});

test('parseArgs handles custom url via -u and --url', () => {
  const parsedShort = parseArgs(['-u', 'http://example.com/api/test']);
  assert.equal(parsedShort.url, 'http://example.com/api/test');

  const parsedLong = parseArgs(['--url', 'http://custom-host:9090/accounts']);
  assert.equal(parsedLong.url, 'http://custom-host:9090/accounts');
});

test('parseArgs handles invalid or non-positive numbers gracefully', () => {
  const parsedInvalid = parseArgs(['-c', 'not-a-number', '-n', '-50']);
  assert.equal(parsedInvalid.concurrency, 10);
  assert.equal(parsedInvalid.requests, 100);
});

test('parseArgs detects help flag', () => {
  const parsedHelpShort = parseArgs(['-h']);
  assert.equal(parsedHelpShort.help, true);

  const parsedHelpLong = parseArgs(['--help']);
  assert.equal(parsedHelpLong.help, true);

  const parsedNoHelp = parseArgs(['-c', '5']);
  assert.equal(parsedNoHelp.help, false);
});

test('formatSummary returns an ASCII summary table with percentiles and status breakdown', () => {
  const sampleSummary = {
    totalRequests: 50,
    successfulRequests: 50,
    failedRequests: 0,
    durationMs: 1200,
    rps: 41.67,
    statusCodes: { '201': 50 },
    min: 10,
    max: 50,
    mean: 22.4,
    p50: 20,
    p90: 35,
    p95: 40,
    p99: 48,
  };
  const text = formatSummary(sampleSummary);
  assert.ok(text.includes('Load Test Results'));
  assert.ok(text.includes('p95'));
  assert.ok(text.includes('RPS'));
  assert.ok(text.includes('201'));
  assert.ok(text.includes('100.0%') || text.includes('100%'));
  assert.ok(text.includes('50'));
});

test('formatSummary handles zero requests gracefully', () => {
  const emptySummary = {
    totalRequests: 0,
    successfulRequests: 0,
    failedRequests: 0,
    durationMs: 0,
    rps: 0,
    statusCodes: {},
    min: 0,
    max: 0,
    mean: 0,
    p50: 0,
    p90: 0,
    p95: 0,
    p99: 0,
  };
  const text = formatSummary(emptySummary);
  assert.ok(text.includes('Load Test Results'));
  assert.ok(text.includes('0'));
});
