import test from 'node:test';
import assert from 'node:assert/strict';
import { accountService } from '../../services/accountService';
import {
  AccountLookupRequest,
  AccountLookupResponse,
  CustomerAddress,
  UpdateProfileRequest,
  StatementEntry,
  AccountResponse,
} from '../../services/types';

function createMockResponse(body: unknown, status = 200, statusText = 'OK'): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    statusText,
    headers: new Headers({ 'content-type': 'application/json' }),
    json: async () => body,
    text: async () => JSON.stringify(body),
  } as unknown as Response;
}

test('bankingServices - accountService.lookupAccount with secure POST', async (t) => {
  const originalFetch = globalThis.fetch;

  t.afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  await t.test('lookupAccount sends POST /accounts/lookup with CPF in body and never in URL query', async () => {
    let capturedUrl = '';
    let capturedOptions: RequestInit | undefined;

    const mockResponse: AccountLookupResponse = {
      accountId: 'acc-uuid-123',
      branch: '0001',
      agency: '0001',
      number: '100000',
      accountNumber: '100000',
      checkDigit: '4',
      digit: '4',
      holderName: 'Carlos Eduardo Silva',
      holderNameMasked: 'C*** E*** S***',
      cpf: '529.982.247-25',
      bankCode: '001',
      bankName: 'Nexus Bank S.A.',
    };

    globalThis.fetch = (async (url: string | URL | Request, options?: RequestInit) => {
      capturedUrl = String(url);
      capturedOptions = options;
      return createMockResponse(mockResponse, 200);
    }) as typeof fetch;

    const lookupReq: AccountLookupRequest = {
      searchType: 'CPF',
      cpf: '529.982.247-25',
    };

    const result = await accountService.lookupAccount(lookupReq);

    assert.strictEqual(capturedOptions?.method, 'POST', 'HTTP method must be POST');
    assert.ok(capturedUrl.endsWith('/accounts/lookup'), 'URL must end with /accounts/lookup');
    assert.ok(!capturedUrl.includes('?'), 'URL must not contain query parameters');
    assert.ok(!capturedUrl.includes('529'), 'URL must not contain sensitive CPF data');

    const body = JSON.parse(String(capturedOptions?.body));
    assert.strictEqual(body.searchType, 'CPF');
    assert.strictEqual(body.cpf, '52998224725', 'CPF should be cleaned of non-numeric characters');
    assert.strictEqual(result.holderName, 'Carlos Eduardo Silva');
    assert.strictEqual(result.cpf, '529.982.247-25');
  });

  await t.test('lookupAccount sends POST /accounts/lookup with ACCOUNT searchType and body', async () => {
    let capturedUrl = '';
    let capturedOptions: RequestInit | undefined;

    const mockResponse: AccountLookupResponse = {
      accountId: 'acc-uuid-456',
      agency: '0001',
      accountNumber: '200001',
      holderName: 'Ana Maria Santos',
      bankName: 'Nexus Bank S.A.',
    };

    globalThis.fetch = (async (url: string | URL | Request, options?: RequestInit) => {
      capturedUrl = String(url);
      capturedOptions = options;
      return createMockResponse(mockResponse, 200);
    }) as typeof fetch;

    const result = await accountService.lookupAccount('0001', '200001');

    assert.strictEqual(capturedOptions?.method, 'POST');
    assert.ok(capturedUrl.endsWith('/accounts/lookup'));
    assert.ok(!capturedUrl.includes('?'));

    const body = JSON.parse(String(capturedOptions?.body));
    assert.strictEqual(body.searchType, 'ACCOUNT');
    assert.strictEqual(body.agency, '0001');
    assert.strictEqual(body.accountNumber, '200001');
    assert.strictEqual(result.holderName, 'Ana Maria Santos');
  });
});

test('bankingServices - accountService.updateProfile with PUT /accounts/me/profile', async (t) => {
  const originalFetch = globalThis.fetch;

  t.afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  await t.test('updateProfile sends PUT to /accounts/me/profile with profile and address data', async () => {
    let capturedUrl = '';
    let capturedOptions: RequestInit | undefined;

    const mockAccount: AccountResponse = {
      id: 'acc-1',
      customerId: 'cust-1',
      agency: '0001',
      number: '123456',
      digit: '7',
      status: 'ACTIVE',
      openedAt: '2026-11-17T00:00:00Z',
      holderName: 'Carlos Eduardo Silva',
      phone: '11999998888',
      email: 'carlos@nexusbank.com',
      dailyTransferLimitCents: 5000000,
      address: {
        street: 'Avenida Paulista',
        number: '1000',
        complement: 'Apto 42',
        neighborhood: 'Bela Vista',
        city: 'São Paulo',
        state: 'SP',
        postalCode: '01310100',
      },
    };

    globalThis.fetch = (async (url: string | URL | Request, options?: RequestInit) => {
      capturedUrl = String(url);
      capturedOptions = options;
      return createMockResponse(mockAccount, 200);
    }) as typeof fetch;

    const profileData: UpdateProfileRequest = {
      phone: '(11) 99999-8888',
      email: 'carlos@nexusbank.com',
      street: 'Avenida Paulista',
      number: '1000',
      complement: 'Apto 42',
      neighborhood: 'Bela Vista',
      city: 'São Paulo',
      state: 'SP',
      postalCode: '01310-100',
    };

    const res = await accountService.updateProfile(profileData);

    assert.strictEqual(capturedOptions?.method, 'PUT');
    assert.ok(capturedUrl.endsWith('/accounts/me/profile'));
    const body = JSON.parse(String(capturedOptions?.body));
    assert.strictEqual(body.email, 'carlos@nexusbank.com');
    assert.strictEqual(body.phone, '11999998888');
    assert.strictEqual(body.postalCode, '01310100');
    assert.strictEqual(res.holderName, 'Carlos Eduardo Silva');
    assert.strictEqual(res.address?.city, 'São Paulo');
  });
});

test('bankingServices - Types and Interfaces Verification', () => {
  const req: AccountLookupRequest = {
    searchType: 'CPF',
    cpf: '12345678901',
    agency: '0001',
    accountNumber: '100000',
  };
  assert.strictEqual(req.searchType, 'CPF');

  const addr: CustomerAddress = {
    postalCode: '01310100',
    street: 'Av Paulista',
    number: '1000',
    complement: 'Sl 10',
    neighborhood: 'Bela Vista',
    city: 'São Paulo',
    state: 'SP',
  };
  assert.strictEqual(addr.state, 'SP');

  const profileReq: UpdateProfileRequest = {
    phone: '11999998888',
    email: 'user@example.com',
    postalCode: addr.postalCode,
    street: addr.street,
    number: addr.number,
  };
  assert.strictEqual(profileReq.email, 'user@example.com');

  const stmt: StatementEntry = {
    protocolNumber: 'PRT-20261117-000123',
    counterpartyDocument: '123.456.789-00',
  };
  assert.strictEqual(stmt.protocolNumber, 'PRT-20261117-000123');
  assert.strictEqual(stmt.counterpartyDocument, '123.456.789-00');

  const acc: AccountResponse = {
    id: 'acc-1',
    customerId: 'cust-1',
    agency: '0001',
    number: '123456',
    digit: '7',
    status: 'ACTIVE',
    openedAt: '2026-11-17T00:00:00Z',
    address: addr,
    dailyTransferLimitCents: 5000000,
    email: 'user@example.com',
    phone: '11999998888',
    cpf: '12345678901',
  };
  assert.strictEqual(acc.dailyTransferLimitCents, 5000000);
});
