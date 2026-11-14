#!/usr/bin/env node
import { parseArgs, formatSummary } from './src/cli.js';
import { runLoadTest } from './src/runner.js';

function printHelp() {
  console.log(`
Bank Ledger - Pure JS Load Testing Suite

Usage:
  node load-tests/run.js [options]
  npm start -- [options]

Options:
  -c, --concurrency <number>   Number of concurrent requests (default: 10)
  -n, --requests <number>      Total number of requests to execute (default: 100)
  -u, --url <string>           Target endpoint URL (default: http://localhost:8081/api/v1/accounts)
  -h, --help                   Display this help message

Examples:
  node load-tests/run.js -c 20 -n 200
  node load-tests/run.js --concurrency 50 --requests 1000
`);
}

async function main() {
  const args = parseArgs(process.argv.slice(2));

  if (args.help) {
    printHelp();
    return;
  }

  console.log('='.repeat(80));
  console.log('                 Bank Ledger Account Service Load Test');
  console.log('='.repeat(80));
  console.log(`  Target URL:   ${args.url}`);
  console.log(`  Concurrency:  ${args.concurrency}`);
  console.log(`  Requests:     ${args.requests}`);
  console.log('='.repeat(80));
  console.log('Running load test...\n');

  try {
    const summary = await runLoadTest({
      url: args.url,
      concurrency: args.concurrency,
      totalRequests: args.requests,
    });

    console.log(formatSummary(summary));

    if (summary.failedRequests > 0) {
      process.exitCode = 1;
    }
  } catch (error) {
    console.error('\n[FATAL] Load test encountered an unhandled error:', error);
    process.exitCode = 1;
  }
}

main();
