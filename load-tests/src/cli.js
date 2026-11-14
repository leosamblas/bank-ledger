import { parseArgs as nodeParseArgs } from 'node:util';

/**
 * @typedef {import('./metrics.js').MetricsSummary} MetricsSummary
 */

/**
 * @typedef {Object} ParsedCliArgs
 * @property {number} concurrency - Number of concurrent requests.
 * @property {number} requests - Total number of requests to execute.
 * @property {string} url - Target URL endpoint.
 * @property {boolean} help - Whether help was requested.
 */

const DEFAULT_CONCURRENCY = 10;
const DEFAULT_REQUESTS = 100;
const DEFAULT_URL = process.env.TARGET_URL || 'http://localhost:8081/api/v1/accounts';

/**
 * Parses CLI arguments for load test execution.
 *
 * @param {string[]} [argv=process.argv.slice(2)]
 * @returns {ParsedCliArgs}
 */
export function parseArgs(argv = process.argv.slice(2)) {
  try {
    const { values } = nodeParseArgs({
      args: argv,
      options: {
        concurrency: { type: 'string', short: 'c' },
        requests: { type: 'string', short: 'n' },
        url: { type: 'string', short: 'u' },
        help: { type: 'boolean', short: 'h', default: false },
      },
      strict: false,
      allowPositionals: true,
    });

    const parsedConcurrency = Number.parseInt(values.concurrency, 10);
    const concurrency = Number.isInteger(parsedConcurrency) && parsedConcurrency > 0
      ? parsedConcurrency
      : DEFAULT_CONCURRENCY;

    const parsedRequests = Number.parseInt(values.requests, 10);
    const requests = Number.isInteger(parsedRequests) && parsedRequests > 0
      ? parsedRequests
      : DEFAULT_REQUESTS;

    const url = typeof values.url === 'string' && values.url.trim().length > 0
      ? values.url.trim()
      : (process.env.TARGET_URL || DEFAULT_URL);

    return {
      concurrency,
      requests,
      url,
      help: Boolean(values.help),
    };
  } catch {
    return {
      concurrency: DEFAULT_CONCURRENCY,
      requests: DEFAULT_REQUESTS,
      url: process.env.TARGET_URL || DEFAULT_URL,
      help: false,
    };
  }
}

/**
 * Formats a MetricsSummary into a clean ASCII terminal report.
 *
 * @param {MetricsSummary} summary
 * @returns {string}
 */
export function formatSummary(summary = {}) {
  const {
    totalRequests = 0,
    successfulRequests = 0,
    failedRequests = 0,
    durationMs = 0,
    rps = 0,
    statusCodes = {},
    min = 0,
    max = 0,
    mean = 0,
    p50 = 0,
    p90 = 0,
    p95 = 0,
    p99 = 0,
  } = summary;

  const durationSec = (durationMs / 1000).toFixed(2);
  const durationMsStr = durationMs.toFixed(0);

  const statusLines = Object.entries(statusCodes);
  let statusOutput = '';

  if (statusLines.length === 0) {
    statusOutput = '  (No responses received)\n';
  } else {
    for (const [code, count] of statusLines) {
      const pct = totalRequests > 0 ? ((count / totalRequests) * 100).toFixed(1) : '0.0';
      statusOutput += `  ${code.padEnd(24)}: ${count} (${pct}%)\n`;
    }
  }

  const border = '='.repeat(80);
  const subBorder = '-'.repeat(76);

  return [
    border,
    '                              Load Test Results                                 ',
    border,
    '',
    '  Execution Overview:',
    `  ${subBorder}`,
    `  Total Requests:         ${totalRequests}`,
    `  Successful Requests:    ${successfulRequests}`,
    `  Failed Requests:        ${failedRequests}`,
    `  Total Duration:         ${durationSec}s (${durationMsStr} ms)`,
    `  Throughput (RPS):       ${rps.toFixed(2)} req/s`,
    '',
    '  Latency Distribution:',
    `  ${subBorder}`,
    `  Min:                    ${min.toFixed(2)} ms`,
    `  Mean:                   ${mean.toFixed(2)} ms`,
    `  50th Percentile (p50):  ${p50.toFixed(2)} ms`,
    `  90th Percentile (p90):  ${p90.toFixed(2)} ms`,
    `  95th Percentile (p95):  ${p95.toFixed(2)} ms`,
    `  99th Percentile (p99):  ${p99.toFixed(2)} ms`,
    `  Max:                    ${max.toFixed(2)} ms`,
    '',
    '  HTTP Status Codes:',
    `  ${subBorder}`,
    statusOutput.trimEnd(),
    border,
  ].join('\n');
}
