import 'server-only';

/**
 * Cliente HTTP do lado servidor.
 *
 * O browser nunca fala com o backend: quem chama a API e o Route Handler ou a Server
 * Action, e o JWT vive em cookie httpOnly. O `server-only` acima faz o build quebrar
 * se alguem importar isto de um Client Component.
 */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly body: unknown,
  ) {
    super(`A API respondeu ${status}`);
    this.name = 'ApiError';
  }
}

function baseUrl(): string {
  const url = process.env.API_BASE_URL;
  if (!url) {
    throw new Error('API_BASE_URL nao configurada. Veja frontend/.env.example.');
  }
  return url.replace(/\/$/, '');
}

export interface ApiRequest extends Omit<RequestInit, 'body'> {
  /** Token do cookie httpOnly. Quem le a sessao passa aqui; o cliente nao busca sozinho. */
  token?: string;
  body?: unknown;
}

export async function apiFetch<T>(path: string, { token, body, ...init }: ApiRequest = {}): Promise<T> {
  const response = await fetch(`${baseUrl()}${path}`, {
    ...init,
    headers: {
      Accept: 'application/json',
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init.headers,
    },
    ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
  });

  if (!response.ok) {
    throw new ApiError(response.status, await safeJson(response));
  }

  return response.status === 204 ? (undefined as T) : ((await response.json()) as T);
}

async function safeJson(response: Response): Promise<unknown> {
  try {
    return await response.json();
  } catch {
    return null;
  }
}
