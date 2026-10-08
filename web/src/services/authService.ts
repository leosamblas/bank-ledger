import { request, tokenStorage, ApiError, IS_MOCK_ENABLED } from './apiClient';
import { LoginRequest, TokenResponse, AccountResponse } from './types';
import { cleanCpf } from '@/utils/cpf';

/**
 * Serviço de Autenticação integrado ao contrato da API com mocks para desenvolvimento
 */
export const authService = {
  /**
   * Autentica o usuário com CPF e senha
   */
  async login(credentials: LoginRequest): Promise<TokenResponse> {
    const sanitizedCpf = cleanCpf(credentials.cpf);

    // Se estiver em modo mock habilitado, responde localmente
    if (IS_MOCK_ENABLED) {
      return this.mockLogin({ ...credentials, cpf: sanitizedCpf });
    }

    try {
      const response = await request<TokenResponse>('/auth/login', {
        method: 'POST',
        body: JSON.stringify({
          cpf: sanitizedCpf,
          password: credentials.password,
        }),
      });

      if (response.token) {
        tokenStorage.set(response.token);
      }

      return response;
    } catch (error) {
      // Caso a API real ainda não esteja rodando (ex: conexão recusada), faz fallback para mock
      if (error instanceof TypeError && error.message.includes('fetch')) {
        console.warn(
          '[AuthService] Backend inacessível. Utilizando fallback com resposta mock.'
        );
        return this.mockLogin({ ...credentials, cpf: sanitizedCpf });
      }
      throw error;
    }
  },

  /**
   * Mock realista para testes locais e desenvolvimento
   */
  async mockLogin(credentials: LoginRequest): Promise<TokenResponse> {
    // Simula latência de rede realista (700ms)
    await new Promise((resolve) => setTimeout(resolve, 700));

    // Exemplo de teste para credenciais rejeitadas:
    if (credentials.password === 'senha-incorreta') {
      throw new ApiError({
        status: 401,
        title: 'Não Autorizado',
        detail: 'Credenciais de acesso incorretas. Verifique seu CPF e senha eletrônica.',
      });
    }

    const mockToken: TokenResponse = {
      token: `nexus_jwt_eyJhGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.${btoa(
        JSON.stringify({
          sub: credentials.cpf,
          role: 'CLIENT',
          iat: Math.floor(Date.now() / 1000),
          exp: Math.floor(Date.now() / 1000) + 3600,
        })
      )}.mock_signature_nexus_ledger`,
      expiresIn: 3600,
      tokenType: 'Bearer',
      user: {
        id: '9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d',
        fullName: 'Carlos Eduardo Silva',
        cpf: credentials.cpf,
        email: 'carlos.silva@nexusbank.com.br',
      },
    };

    tokenStorage.set(mockToken.token);
    return mockToken;
  },

  /**
   * Encerra a sessão do cliente
   */
  logout(): void {
    tokenStorage.clear();
  },

  /**
   * Verifica se há token ativo na sessão
   */
  isAuthenticated(): boolean {
    return Boolean(tokenStorage.get());
  },

  /**
   * Retorna os dados da conta do usuário autenticado
   */
  async getCurrentUserAccount(): Promise<AccountResponse> {
    if (IS_MOCK_ENABLED) {
      await new Promise((resolve) => setTimeout(resolve, 400));
      return {
        id: 'f47ac10b-58cc-4372-a567-0e02b2c3d479',
        customerId: '9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d',
        agency: '0001',
        number: '104829',
        digit: '3',
        status: 'ACTIVE',
        openedAt: new Date().toISOString(),
      };
    }

    return request<AccountResponse>('/accounts/me', {
      method: 'GET',
    });
  },
};
