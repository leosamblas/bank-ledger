import { performance } from 'node:perf_hooks';
import { generateCustomerPayload } from './generator.js';
import { MetricsTracker } from './metrics.js';

/**
 * @typedef {import('./metrics.js').MetricsSummary} MetricsSummary
 */

/**
 * @typedef {Object} RunLoadTestOptions
 * @property {string} url - Target URL to send requests to.
 * @property {number} [concurrency=1] - Maximum concurrent in-flight requests.
 * @property {number} [totalRequests=0] - Total number of requests to execute.
 * @property {typeof fetch} [fetchFn=globalThis.fetch] - Custom fetch implementation (optional).
 * @property {Function} [payloadGenerator=generateCustomerPayload] - Custom payload generator function.
 * @property {Record<string, string>} [headers={}] - Custom HTTP headers.
 * @property {string} [method='POST'] - HTTP method.
 */

/**
 * Executes a load test with strict concurrency control and aggregate metrics tracking.
 *
 * @param {RunLoadTestOptions} options
 * @returns {Promise<MetricsSummary>}
 */
export async function runLoadTest(options = {}) {
  const {
    url,
    concurrency = 1,
    totalRequests = 0,
    fetchFn = globalThis.fetch,
    payloadGenerator = generateCustomerPayload,
    headers = {},
    method = 'POST',
  } = options;

  const tracker = new MetricsTracker();
  tracker.start();

  const safeTotalRequests = Math.max(0, Math.floor(Number(totalRequests) || 0));
  const safeConcurrency = Math.max(1, Math.floor(Number(concurrency) || 1));

  if (safeTotalRequests === 0) {
    tracker.stop();
    return tracker.summary();
  }

  const workerCount = Math.min(safeConcurrency, safeTotalRequests);
  let dispatchedCount = 0;

  async function worker() {
    while (true) {
      if (dispatchedCount >= safeTotalRequests) {
        break;
      }
      dispatchedCount++;

      let payload;
      try {
        payload = typeof payloadGenerator === 'function'
          ? payloadGenerator()
          : generateCustomerPayload();
      } catch (err) {
        // If payload generator throws, record immediately as an error
        tracker.record(0, 0, err);
        continue;
      }

      const body = JSON.stringify(payload);
      let response = null;
      let error = null;
      const reqStart = performance.now();

      try {
        response = await fetchFn(url, {
          method,
          headers: {
            'Content-Type': 'application/json',
            ...headers,
          },
          body,
        });

        if (response) {
          try {
            if (typeof response.text === 'function') {
              await response.text();
            } else if (typeof response.json === 'function') {
              await response.json();
            }
          } catch {
            // Ignore response body consumption errors
          }
        }
      } catch (err) {
        error = err;
      }

      const reqDuration = performance.now() - reqStart;
      const statusCode = response && typeof response.status === 'number' ? response.status : 0;
      tracker.record(reqDuration, statusCode, error);
    }
  }

  const workers = Array.from({ length: workerCount }, () => worker());
  await Promise.all(workers);

  tracker.stop();
  return tracker.summary();
}
