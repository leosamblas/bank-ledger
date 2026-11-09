-- PostgreSQL Databases Initialization for Bank Ledger Microservices
-- Pattern: Database-per-Service

CREATE DATABASE account_db OWNER bank_ledger;
GRANT ALL PRIVILEGES ON DATABASE account_db TO bank_ledger;

CREATE DATABASE ledger_db OWNER bank_ledger;
GRANT ALL PRIVILEGES ON DATABASE ledger_db TO bank_ledger;

CREATE DATABASE transaction_db OWNER bank_ledger;
GRANT ALL PRIVILEGES ON DATABASE transaction_db TO bank_ledger;

CREATE DATABASE query_db OWNER bank_ledger;
GRANT ALL PRIVILEGES ON DATABASE query_db TO bank_ledger;
