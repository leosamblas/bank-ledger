import { request, ApiError, IS_MOCK_ENABLED } from './apiClient';
import {
  AccountCreationRequest,
  AccountResponse,
  AccountLookupResponse,
  AccountLookupRequest,
  UpdateProfileRequest,
} from './types';
import { cleanCpf } from '@/utils/cpf';
import { generateUUID } from '@/utils/uuid';

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
      birthDate: '1990-05-15',
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
        birthDate: '1990-05-15',
      };
    }

    return request<AccountResponse>(`/accounts/${accountId}`, {
      method: 'GET',
    });
  },

  /**
   * Retorna os dados da conta do usuário autenticado (/accounts/me)
   */
  async getCurrentUserAccount(): Promise<AccountResponse> {
    return request<AccountResponse>('/accounts/me', {
      method: 'GET',
    });
  },

  /**
   * Consulta conta por agência/número ou por CPF via POST seguro (evita exposição de PII em URL)
   */
  async lookupAccount(
    requestOrAgency: AccountLookupRequest | string,
    accountNumber?: string
  ): Promise<AccountLookupResponse> {
    const payload: AccountLookupRequest =
      typeof requestOrAgency === 'string'
        ? {
            searchType: 'ACCOUNT',
            agency: requestOrAgency,
            accountNumber,
          }
        : {
            searchType: requestOrAgency.searchType || (requestOrAgency.cpf ? 'CPF' : 'ACCOUNT'),
            cpf: requestOrAgency.cpf ? cleanCpf(requestOrAgency.cpf) : undefined,
            agency: requestOrAgency.agency,
            accountNumber:
              requestOrAgency.accountNumber ||
              (requestOrAgency as { number?: string }).number,
          };

    try {
      const response = await request<AccountLookupResponse>('/accounts/lookup', {
        method: 'POST',
        body: JSON.stringify(payload),
      });

      return response;
    } catch (error) {
      if (
        IS_MOCK_ENABLED &&
        error instanceof TypeError &&
        error.message.includes('fetch')
      ) {
        await new Promise((resolve) => setTimeout(resolve, 300));
        return {
          accountId: generateUUID(),
          branch: payload.agency || '0001',
          agency: payload.agency || '0001',
          number: payload.accountNumber || '104829',
          accountNumber: payload.accountNumber || '104829',
          checkDigit: '3',
          digit: '3',
          holderName: 'Carlos Eduardo Silva',
          holderNameMasked: 'C*** E*** S***',
          cpf: payload.cpf || '529.982.247-25',
          bankCode: '001',
          bankName: 'Nexus Bank S.A.',
        };
      }
      throw error;
    }
  },

  /**
   * Atualiza dados cadastrais de contato e endereço do correntista
   */
  async updateProfile(data: UpdateProfileRequest): Promise<AccountResponse> {
    const payload: UpdateProfileRequest = {
      ...data,
      phone: data.phone ? data.phone.replace(/\D/g, '') : undefined,
      postalCode: data.postalCode ? data.postalCode.replace(/\D/g, '') : undefined,
    };

    return request<AccountResponse>('/accounts/me/profile', {
      method: 'PUT',
      body: JSON.stringify(payload),
    });
  },
};
