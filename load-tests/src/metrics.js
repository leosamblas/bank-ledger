import { performance } from 'node:perf_hooks';

/**
 * @typedef {Object} MetricsSummary
 * @property {number} totalRequests
 * @property {number} successfulRequests
 * @property {number} failedRequests
 * @property {number} durationMs
 * @property {number} rps
 * @property {Record<string, number>} statusCodes
 * @property {number} min
 * @property {number} max
 * @property {number} mean
 * @property {number} p50
 * @property {number} p90
 * @property {number} p95
 * @property {number} p99
 */

/**
 * Tracks and aggregates latency and throughput metrics for load tests.
 */
export class MetricsTracker {
  constructor() {
    this.reset();
  }

  /**
   * Resets all accumulated metrics.
   */
  reset() {
    this.startTime = null;
    this.endTime = null;
    this.totalRequests = 0;
    this.successfulRequests = 0;
    this.failedRequests = 0;
    this.totalLatency = 0;
    this.latencies = [];
    this.statusCodes = {};
  }

  /**
   * Starts or restarts the elapsed time clock.
   */
  start() {
    this.startTime = performance.now();
    this.endTime = null;
  }

  /**
   * Stops the elapsed time clock.
   */
  stop() {
    this.endTime = performance.now();
  }

  /**
   * Records a single request execution.
   *
   * @param {number} durationMs - Request round-trip latency in milliseconds.
   * @param {number} statusCode - HTTP status code (or 0 if connection failed).
   * @param {Error | null} [error=null] - Error instance if request threw or failed.
   */
  record(durationMs, statusCode, error = null) {
    this.totalRequests++;

    const lat = typeof durationMs === 'number' && !isNaN(durationMs) ? durationMs : 0;
    this.latencies.push(lat);
    this.totalLatency += lat;

    const numStatus = Number(statusCode);
    const hasValidStatus = !isNaN(numStatus) && numStatus > 0;
    const isSuccess = !error && hasValidStatus && numStatus >= 200 && numStatus < 400;

    if (isSuccess) {
      this.successfulRequests++;
    } else {
      this.failedRequests++;
    }

    const codeKey = hasValidStatus ? String(numStatus) : 'ERR';
    this.statusCodes[codeKey] = (this.statusCodes[codeKey] || 0) + 1;
  }

  /**
   * Computes and returns aggregated metrics summary.
   *
   * @returns {MetricsSummary}
   */
  summary() {
    const endTime = this.endTime !== null ? this.endTime : performance.now();
    const durationMs = this.startTime !== null ? Math.max(0, endTime - this.startTime) : 0;
    const totalRequests = this.totalRequests;
    const rps = durationMs > 0 ? (totalRequests / (durationMs / 1000)) : 0;

    const len = this.latencies.length;
    if (len === 0) {
      return {
        totalRequests: 0,
        successfulRequests: 0,
        failedRequests: 0,
        durationMs,
        rps: 0,
        statusCodes: { ...this.statusCodes },
        min: 0,
        max: 0,
        mean: 0,
        p50: 0,
        p90: 0,
        p95: 0,
        p99: 0,
      };
    }

    const sorted = [...this.latencies].sort((a, b) => a - b);
    const min = sorted[0];
    const max = sorted[len - 1];
    const mean = this.totalLatency / len;

    const getPercentile = (p) => sorted[Math.floor(p * (len - 1))];

    return {
      totalRequests,
      successfulRequests: this.successfulRequests,
      failedRequests: this.failedRequests,
      durationMs,
      rps,
      statusCodes: { ...this.statusCodes },
      min,
      max,
      mean,
      p50: getPercentile(0.50),
      p90: getPercentile(0.90),
      p95: getPercentile(0.95),
      p99: getPercentile(0.99),
    };
  }
}
