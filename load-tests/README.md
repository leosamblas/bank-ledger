# Bank Ledger — Pure JavaScript Load Testing Suite

A lightweight, zero-overhead load testing tool designed for the **Bank Ledger** platform (`account-service` and related REST endpoints). Built in pure modern JavaScript (Node.js 22+, ES Modules) without requiring external frameworks such as k6, JMeter, or Gatling.

The suite utilizes native Node.js features (`fetch`, `node:perf_hooks`, `node:util.parseArgs`, and the built-in Node.js test runner) combined with `@faker-js/faker` (`fakerPT_BR`) for highly realistic, domain-compliant Brazilian customer data generation.

---

## 🚀 Key Features

* **Pure JavaScript & Zero-Overhead:** Powered by Node.js native `fetch` and async workers with strict concurrency control.
* **Dedicated Brazilian CPF Engine (`cpf.js`):** Independent, native implementation of Receita Federal's official Modulo-11 algorithm:
  * Strict parity with `com.bankledger.account.domain.vo.Cpf` (rejects identical digits, validates both check digits).
  * Supports Brazilian fiscal regions (0-9) via the 9th digit.
  * Exports `generateValidCpf()`, `validateCpf()`, `formatCpf()`, and `unformatCpf()`.
  * Guarantees process-level uniqueness to prevent synthetic HTTP 409 Conflict errors.
* **Realistic Demographics via `@faker-js/faker`:**
  * **Age & Calendar Validation:** Ensures date of birth results in age $\ge 18$ years relative to current date, avoiding `CustomerUnderageException`.
  * **E.164 Brazilian Phone Numbers:** Generates valid mobile numbers with official ANATEL DDD area codes (`+55<DDD>9<8-digits>`).
  * **Unique Emails:** Salted and deduplicated emails to prevent HTTP 409 Conflict errors during high-throughput runs.
* **Comprehensive Metrics Tracking:** Sub-millisecond latency histograms, percentiles (p50, p90, p95, p99, min, max, mean), throughput (requests per second / RPS), and HTTP response status breakdown.
* **Native Test Suite:** Complete unit test suite running on `node --test` with zero third-party testing dependencies.

---

## 📋 Prerequisites

* **Node.js**: `v20.0.0` or higher (tested on Node.js `v22+`).
* **Bank Ledger Account Service**: Running locally (default: `http://localhost:8081`) or accessible via Docker/Gateway (`http://localhost:8080`).

---

## 📦 Installation

From the project root:

```bash
cd load-tests
npm install
```

Or from the root directory directly:

```bash
npm --prefix load-tests install
```

---

## ⚡ Quick Start & Usage Examples

### 1. Default Run (10 concurrent workers, 100 total requests)

```bash
node load-tests/run.js
```

### 2. Custom Concurrency and Request Count

You can configure the execution using short flags (`-c`, `-n`) or long flags (`--concurrency`, `--requests`):

```bash
# 20 concurrent workers executing 500 total requests
node load-tests/run.js -c 20 -n 500

# Using long options
node load-tests/run.js --concurrency 50 --requests 1000
```

### 3. Via npm script

```bash
# Run with custom arguments via npm
npm --prefix load-tests start -- -c 5 -n 50
```

### 4. Custom Target Endpoint URL

The target URL defaults to `http://localhost:8081/api/v1/accounts`. You can customize it via the `-u` / `--url` CLI flag or via the `TARGET_URL` environment variable:

```bash
# Using CLI option
node load-tests/run.js -u http://localhost:8080/api/v1/accounts -c 10 -n 100

# Using environment variable (e.g. targeting API gateway or remote environment)
TARGET_URL=http://localhost:8081/api/v1/accounts node load-tests/run.js -c 10 -n 100
```

On Windows PowerShell:
```powershell
$env:TARGET_URL="http://localhost:8081/api/v1/accounts"; node load-tests/run.js -c 10 -n 100
```

### 5. CLI Help

```bash
node load-tests/run.js --help
```

---

## ⚙️ CLI Parameters & Options

| Option | Short Flag | Type | Default | Description |
| :--- | :--- | :--- | :--- | :--- |
| `--concurrency` | `-c` | `number` | `10` | Maximum number of concurrent in-flight requests. |
| `--requests` | `-n` | `number` | `100` | Total number of customer creation requests to execute. |
| `--url` | `-u` | `string` | `http://localhost:8081/api/v1/accounts` | Target endpoint URL. Falls back to `process.env.TARGET_URL`. |
| `--help` | `-h` | `boolean` | `false` | Displays CLI usage guidelines and exits. |

---

## 🇧🇷 Realistic Brazilian Mock Data Generation

The load testing generator (`load-tests/src/generator.js`) uses `@faker-js/faker` configured with the Brazilian Portuguese locale (`fakerPT_BR`) to produce production-grade customer mock payloads (`AccountCreationRequest`):

```json
{
  "fullName": "Mariana Souza Pereira",
  "cpf": "28475930184",
  "birthDate": "1994-06-12",
  "email": "mariana_1_1763155200000@loadtest.bankledger.com",
  "phone": "+5511987654321",
  "password": "Password#2026!"
}
```

### Domain Rules Implemented:

1. **CPF Checksum (Modulo-11):**
   * Computes the 1st check digit using descending weights from 10 to 2 (`remainder < 2 ? 0 : 11 - remainder`).
   * Computes the 2nd check digit using descending weights from 11 to 2.
   * Explicitly rejects known invalid CPFs with all identical digits (e.g., `00000000000`, `11111111111`).
   * Deduplicates generated CPFs in memory to prevent duplicate key conflicts in the database.
2. **Age Eligibility ($\ge 18$ years old):**
   * Uses `fakerPT_BR.date.birthdate({ min: 18, max: 65, mode: 'age' })`.
   * Evaluates calendar date differences against the current UTC date to strictly guarantee the customer has already reached their 18th birthday, preventing Spring Boot domain validation exceptions (`CustomerUnderageException`).
3. **Brazilian Phone Numbers:**
   * Selects an official Brazilian DDD (e.g., 11 for São Paulo, 21 for Rio de Janeiro, 31 for Belo Horizonte, 41 for Curitiba, etc.) from an authoritative list of 67 valid DDDs.
   * Generates a 9-digit mobile number starting with `9` and formats as an international E.164 string: `+55<DDD>9XXXXXXXX`.
4. **Unique Email Address:**
   * Combines first and last name with a monotonically increasing sequence counter and epoch timestamp salt to guarantee complete uniqueness under high-concurrency loops.

---

## 📊 Output Metrics & Reporting

Upon completing all requests, the CLI formats an ASCII metrics table summarizing execution performance:

```text
================================================================================
                              Load Test Results                                 
================================================================================

  Execution Overview:
  ----------------------------------------------------------------------------
  Total Requests:         100
  Successful Requests:    100
  Failed Requests:        0
  Total Duration:         1.85s (1852 ms)
  Throughput (RPS):       53.99 req/s

  Latency Distribution:
  ----------------------------------------------------------------------------
  Min:                    12.45 ms
  Mean:                   18.23 ms
  50th Percentile (p50):  17.10 ms
  90th Percentile (p90):  24.80 ms
  95th Percentile (p95):  28.15 ms
  99th Percentile (p99):  34.50 ms
  Max:                    38.90 ms

  HTTP Status Codes:
  ----------------------------------------------------------------------------
  201                     : 100 (100.0%)
================================================================================
```

### Metrics Explained:

* **Throughput (RPS):** Total executed requests divided by total wall-clock duration in seconds.
* **Latency Percentiles:**
  * **Min / Max**: Fastest and slowest recorded round-trip latencies.
  * **Mean**: Arithmetic average of all latencies.
  * **p50 (Median)**: 50% of requests completed faster than this time.
  * **p90 / p95 / p99**: Tail latency thresholds indicating performance under stress.
* **HTTP Status Codes:** Tally of all response codes (e.g., `201 Created`, `400 Bad Request`, `409 Conflict`, or `ERR` for connection drops).

---

## 🧪 Running Unit Tests

The test suite runs with Node.js built-in test runner (`node --test`), verifying CLI argument parsing, CPF modulo-11 math, birthdate calculations, concurrency worker limits, and metrics tracking:

```bash
# Run tests from project root
node --test load-tests/tests/*.test.js

# Or using npm prefix
npm --prefix load-tests test
```

All 28+ unit tests run in less than 500ms without requiring network access or external services.

---

## 🏛️ Architecture & Code Organization

```text
load-tests/
├── package.json         # ES module configuration & @faker-js/faker dependency
├── run.js               # CLI entrypoint executable
├── README.md            # Comprehensive documentation
├── src/
│   ├── cli.js           # Argument parsing (parseArgs) & ASCII report formatter
│   ├── generator.js     # Brazilian mock customer data generator (fakerPT_BR)
│   ├── metrics.js       # High-resolution performance tracker & percentile calculator
│   └── runner.js        # Concurrent worker pool and HTTP request dispatcher
└── tests/
    ├── cli.test.js       # Tests for CLI flags and summary formatting
    ├── generator.test.js # Tests for modulo-11, age validation, phone and email uniqueness
    ├── metrics.test.js   # Tests for stats calculations, latencies, percentiles
    └── runner.test.js    # Tests for worker concurrency, HTTP handling, and edge cases
```
