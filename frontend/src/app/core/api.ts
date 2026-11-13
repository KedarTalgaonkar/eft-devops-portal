import { HttpErrorResponse } from '@angular/common/http';

/** Every backend call uses this relative base; nginx (container) or proxy.conf.json (ng serve) forwards it. */
export const API = '/api';

export type QueryValue = string | number | boolean | null | undefined;

/** Builds `/api/<path>?a=1&b=2`, leaving out empty parameters. */
export function apiUrl(path: string, params: Record<string, QueryValue> = {}): string {
  const query = Object.entries(params)
    .filter(([, v]) => v !== null && v !== undefined && v !== '')
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`)
    .join('&');
  return `${API}/${path}${query ? `?${query}` : ''}`;
}

/** Shape of the backend's ApiError body. */
export interface ApiError {
  status: number;
  error: string;
  message: string;
  path: string;
  details?: string[];
}

/** Human-readable message for an HttpClient or httpResource error. */
export function errorMessage(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    const body = err.error as Partial<ApiError> | null;
    if (body && typeof body === 'object' && body.message) {
      return body.message;
    }
    if (err.status === 0) {
      return 'The backend cannot be reached.';
    }
    return `${err.status} ${err.statusText}`;
  }
  return err instanceof Error ? err.message : 'Unexpected error';
}
