import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {
  formatPhone,
  formatCep,
  validateEmail,
  fetchAddressByCep,
} from '../../app/dashboard/profile/page';
import { accountService } from '../../services/accountService';
import { UpdateProfileRequest } from '../../services/types';

function createMockResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    statusText: 'OK',
    headers: new Headers({ 'content-type': 'application/json' }),
    json: async () => body,
    text: async () => JSON.stringify(body),
  } as unknown as Response;
}

test('ProfilePage - Input Masks & Formatters', () => {
  // Telefone celular mask: (00) 00000-0000
  assert.strictEqual(formatPhone('11987654321'), '(11) 98765-4321');
  assert.strictEqual(formatPhone('1187654321'), '(11) 8765-4321'); // fixo ou 8 dígitos
  assert.strictEqual(formatPhone('1198'), '(11) 98');
  assert.strictEqual(formatPhone('11'), '(11');
  assert.strictEqual(formatPhone(''), '');

  // CEP mask: 00000-000
  assert.strictEqual(formatCep('01310100'), '01310-100');
  assert.strictEqual(formatCep('01310-100'), '01310-100');
  assert.strictEqual(formatCep('01310'), '01310');
  assert.strictEqual(formatCep(''), '');
});

test('ProfilePage - Email Validation', () => {
  assert.strictEqual(validateEmail('carlos.silva@nexusbank.com.br'), true);
  assert.strictEqual(validateEmail('usuario@empresa.com'), true);
  assert.strictEqual(validateEmail(''), false);
  assert.strictEqual(validateEmail('invalido'), false);
  assert.strictEqual(validateEmail('sem-arroba.com'), false);
  assert.strictEqual(validateEmail('sem-dominio@'), false);
});

test('ProfilePage - ViaCEP auto-fetch integration', async (t) => {
  const originalFetch = globalThis.fetch;

  t.afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  await t.test('fetches and populates address fields when 8-digit CEP is provided', async () => {
    let requestedUrl = '';

    const viaCepResponse = {
      cep: '01310-100',
      logradouro: 'Avenida Paulista',
      complemento: 'de 611 a 1045 - lado ímpar',
      bairro: 'Bela Vista',
      localidade: 'São Paulo',
      uf: 'SP',
      ibge: '3550308',
      gia: '1004',
      ddd: '11',
      siafi: '7107',
    };

    globalThis.fetch = (async (url: string | URL | Request) => {
      requestedUrl = String(url);
      return createMockResponse(viaCepResponse, 200);
    }) as typeof fetch;

    const result = await fetchAddressByCep('01310-100');

    assert.ok(requestedUrl.includes('viacep.com.br/ws/01310100/json/'));
    assert.strictEqual(result?.street, 'Avenida Paulista');
    assert.strictEqual(result?.neighborhood, 'Bela Vista');
    assert.strictEqual(result?.city, 'São Paulo');
    assert.strictEqual(result?.state, 'SP');
  });

  await t.test('returns null when ViaCEP returns erro flag or invalid CEP', async () => {
    globalThis.fetch = (async () => {
      return createMockResponse({ erro: 'true' }, 200);
    }) as typeof fetch;

    const result = await fetchAddressByCep('99999-999');
    assert.strictEqual(result, null);
  });
});

test('ProfilePage - Static Structure, Read-only Identity, Accessibility & Tokens', () => {
  const profilePagePath = path.resolve(
    process.cwd(),
    'src/app/dashboard/profile/page.tsx'
  );
  assert.ok(fs.existsSync(profilePagePath), 'ProfilePage must exist at src/app/dashboard/profile/page.tsx');

  const content = fs.readFileSync(profilePagePath, 'utf-8');

  // Page Title
  assert.ok(
    content.includes('Meu Perfil e Dados Cadastrais'),
    'Page must contain title "Meu Perfil e Dados Cadastrais"'
  );

  // Read-only Identity Section
  assert.ok(content.includes('Nome Completo'), 'Must include Nome Completo label');
  assert.ok(content.includes('CPF'), 'Must include CPF label');
  assert.ok(content.includes('Agência'), 'Must include Agência label');
  assert.ok(content.includes('Conta Corrente'), 'Must include Conta Corrente label');
  assert.ok(content.includes('Data de Nascimento'), 'Must include Data de Nascimento label');

  // Informational Badge
  assert.ok(
    content.includes('Dados pessoais cadastrais protegidos. Para alteração de titularidade, contate seu gerente.'),
    'Must display informational badge regarding protected cadastral identity data'
  );

  // Read-only attributes on identity fields
  assert.ok(content.includes('readOnly'), 'Identity inputs must have readOnly attribute');
  assert.ok(content.includes('aria-readonly'), 'Identity inputs must declare aria-readonly');

  // Editable Contact Section
  assert.ok(content.includes('Telefone Celular'), 'Must include Telefone Celular field');
  assert.ok(content.includes('E-mail'), 'Must include E-mail field');

  // Editable Residential Address Section
  assert.ok(content.includes('CEP'), 'Must include CEP field');
  assert.ok(content.includes('Logradouro') || content.includes('Endereço / Rua'), 'Must include street field');
  assert.ok(content.includes('Número'), 'Must include number field');
  assert.ok(content.includes('Complemento'), 'Must include complement field');
  assert.ok(content.includes('Bairro'), 'Must include neighborhood field');
  assert.ok(content.includes('Cidade'), 'Must include city field');
  assert.ok(content.includes('UF') || content.includes('Estado'), 'Must include state field');

  // Action Buttons
  assert.ok(content.includes('Salvar Alterações'), 'Must include "Salvar Alterações" button');
  assert.ok(content.includes('Cancelar'), 'Must include "Cancelar" button');

  // Accessibility & UX
  assert.ok(!content.includes('alert('), 'Must not use browser alert()');
  assert.ok(content.includes('aria-live'), 'Must use aria-live for notifications/messages');
  assert.ok(content.includes('focus:ring-2'), 'Must have accessible focus rings');
});

test('ProfilePage - Successful submission calls updateProfile', async (t) => {
  const originalFetch = globalThis.fetch;

  t.afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  let capturedUrl = '';
  let capturedOptions: RequestInit | undefined;

  globalThis.fetch = (async (url: string | URL | Request, options?: RequestInit) => {
    capturedUrl = String(url);
    capturedOptions = options;
    return createMockResponse({
      id: 'acc-1',
      customerId: 'cust-1',
      agency: '0001',
      number: '104829',
      digit: '3',
      status: 'ACTIVE',
      openedAt: new Date().toISOString(),
      holderName: 'Carlos Eduardo Silva',
    }, 200);
  }) as typeof fetch;

  const profilePayload: UpdateProfileRequest = {
    phone: '(11) 98765-4321',
    email: 'carlos.silva@nexusbank.com.br',
    postalCode: '01310-100',
    street: 'Avenida Paulista',
    number: '1000',
    complement: 'Apto 101',
    neighborhood: 'Bela Vista',
    city: 'São Paulo',
    state: 'SP',
  };

  const response = await accountService.updateProfile(profilePayload);

  assert.strictEqual(capturedOptions?.method, 'PUT');
  assert.ok(capturedUrl.endsWith('/accounts/me/profile'));
  const body = JSON.parse(String(capturedOptions?.body));
  assert.strictEqual(body.phone, '11987654321');
  assert.strictEqual(body.postalCode, '01310100');
  assert.strictEqual(body.email, 'carlos.silva@nexusbank.com.br');
  assert.strictEqual(response.holderName, 'Carlos Eduardo Silva');
});
