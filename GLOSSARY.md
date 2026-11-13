# Bank Ledger

Core banking system and double-entry accounting engine for Nexus Bank.

## Language

### Core Banking & Identity

**Customer**:
An individual registered in the bank with a unique CPF, entitled to hold accounts.  
_Avoid_: User, client

**Account**:
A checking account associated with a customer, identified by agency, number, and check digit.  
_Avoid_: Wallet, bank account

### Financial Transactions & Ledger

**Transaction**:
A business operation requested by a customer (such as deposit, withdrawal, or transfer) representing the customer's intent.  
_Avoid_: Journal, entry, transfer request

**Journal**:
An immutable accounting record that groups balanced debit and credit entries for a single financial event.  
_Avoid_: Transaction, ledger row

**Entry**:
An individual debit or credit leg linked to a specific ledger account within a journal.  
_Avoid_: Movement, line item, transaction item

**Ledger Account**:
An internal accounting balance sheet entity (customer account or system account) tracked with normal balance rules.  
_Avoid_: Bank account, checking account

**Cash Shard**:
A system ledger account representing an aggregated pool of physical/vault cash, sharded to mitigate lock contention.  
_Avoid_: Vault, cash account, system balance

**Terminal**:
A physical or logical point of capture (such as an ATM, branch counter, or digital channel) from which cash operations originate.  
_Avoid_: Device, machine, ATM identifier

**Idempotency Key**:
A client-generated UUID that guarantees a financial transaction cannot be processed more than once upon retries.  
_Avoid_: Request ID, deduplication token, nonce
