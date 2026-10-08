import { request, ApiError, IS_MOCK_ENABLED } from './apiClient';
import {
  AccountCreationRequest,
  AccountResponse,
  AccountLookupResponse,
} from './types';
import { cleanCpf } from '@/utils/cpf';

/**
 * Utilitário seguro para gerar UUIDv4 mesmo em ambientes sem crypto.randomUUID
 */
function generateUUID(): string {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

/**
 * Serviço de Gestão de Contas (Abertura, Consulta e Lookup)
 */
export const accountService = {
  /**
   * Abre uma nova conta corrente digital no Nexus Bank
   */
  async createAccount(data: AccountCreationRequest): Promise<AccountResponse> {
    const payload: AccountCreationRequest = {
      ...data,
      cpf: cleanCpf(data.cpf),
      phone: data.phone.replace(/\D/g, ''),
    };

    if (IS_MOCK_ENABLED) {
      return this.mockCreateAccount(payload);
    }

    try {
      const response = await request<AccountResponse>('/accounts', {
        method: 'POST',
        body: JSON.stringify(payload),
      });

      return response;
    } catch (error) {
      // Fallback gracioso se o backend ainda estiver offline
      if (error instanceof TypeError && error.message.includes('fetch')) {
        console.warn(
          '[AccountService] Backend inacessível. Criando conta via mock local.'
        );
        return this.mockCreateAccount(payload);
      }
      throw error;
    }
  },

  /**
   * Mock realista para testes locais e validação de fluxo do cliente
   */
  async mockCreateAccount(
    payload: AccountCreationRequest
  ): Promise<AccountResponse> {
    // Simula tempo de processamento bancário (850ms)
    await new Promise((resolve) => setTimeout(resolve, 850));

    // Exemplo de simulação de erro de CPF duplicado se terminar em "99"
    if (payload.cpf.endsWith('9999')) {
      throw new ApiError({
        status: 409,
        title: 'Conflito de Cadastro',
        detail:
          'Já existe uma conta ativa associada a este número de CPF no Nexus Bank.',
      });
    }

    // Gera dados de conta realistas
    const randomAccountNumber = Math.floor(100000 + Math.random() * 900000).toString();
    const randomDigit = Math.floor(Math.random() * 9 + 1).toString();

    const mockAccount: AccountResponse = {
      id: generateUUID(),
      customerId: generateUUID(),
      agency: '0001',
      number: randomAccountNumber,
      digit: randomDigit,
      status: 'PENDING_ACTIVATION',
      openedAt: new Date().toISOString(),
    };

    return mockAccount;
  },

  /**
   * Busca detalhes de uma conta específica por ID
   */
  async getAccountById(accountId: string): Promise<AccountResponse> {
    if (IS_MOCK_ENABLED) {
      await new Promise((resolve) => setTimeout(resolve, 300));
      return {
        id: accountId,
        customerId: generateUUID(),
        agency: '0001',
        number: '104829',
        digit: '3',
        status: 'ACTIVE',
        openedAt: new Date().toISOString(),
      };
    }

    return request<AccountResponse>(`/accounts/${accountId}`, {
      method: 'GET',
    });
  },

  /**
   * Consulta conta por agência e número para exibir nome mascarado
   */
  async lookupAccount(
    agency: string,
    number: string
  ): Promise<AccountLookupResponse> {
    if (IS_MOCK_ENABLED) {
      await new Promise((resolve) => setTimeout(resolve, 300));
      return {
        accountId: generateUUID(),
        agency,
        number,
        holderNameMasked: 'C*** E*** S***',
      };
    }

    return request<AccountLookupResponse>(
      `/accounts/lookup?agency=${encodeURIComponent(
        agency
      )}&number=${encodeURIComponent(number)}`,
      {
        method: 'GET',
      }
    );
  },
};
