/**
 * HTTP 클라이언트 — Spring REST API 호출 래퍼.
 * - 조회 9종: ApiResponse<T> envelope 언랩 (getJson). 200+G4 code 동반 반환.
 * - 임포트/업로드: DTO 직접 반환 (getRaw / postJson).
 * - 업로드: 진행률 필요 → XHR (fetch 는 업로드 progress 미지원).
 * 분기는 HTTP status 가 아니라 code 로.
 */
import type { ApiResponse, ErrorBody } from './types';

export class ApiError extends Error {
  readonly code: string;
  readonly httpStatus: number;
  readonly detail?: string | null;

  constructor(code: string, httpStatus: number, message: string, detail?: string | null) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.httpStatus = httpStatus;
    this.detail = detail;
  }
}

async function parseBody(res: Response): Promise<unknown> {
  const text = await res.text();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

function toApiError(body: unknown, httpStatus: number): ApiError {
  const e = (body ?? {}) as Partial<ErrorBody>;
  return new ApiError(
    e.code ?? 'INTERNAL_ERROR',
    httpStatus,
    e.message ?? 'request failed',
    e.detail,
  );
}

/** 조회: envelope 언랩 + code 동반(200 G4 처리용). */
export async function getJson<T>(path: string): Promise<{ data: T | null; code: string | null }> {
  const res = await fetch(path);
  const body = await parseBody(res);
  if (!res.ok) throw toApiError(body, res.status);
  const env = (body ?? {}) as ApiResponse<T>;
  return { data: env.data ?? null, code: env.code ?? null };
}

/** 임포트/업로드 GET: DTO 직접. */
export async function getRaw<T>(path: string): Promise<T> {
  const res = await fetch(path);
  const body = await parseBody(res);
  if (!res.ok) throw toApiError(body, res.status);
  return body as T;
}

/** 임포트 삭제(DELETE): DTO 직접. */
export async function delJson<T>(path: string): Promise<T> {
  const res = await fetch(path, { method: 'DELETE' });
  const body = await parseBody(res);
  if (!res.ok) throw toApiError(body, res.status);
  return body as T;
}

/** 임포트/업로드 POST(JSON body): DTO 직접. */
export async function postJson<T>(path: string, payload: unknown): Promise<T> {
  const res = await fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  const body = await parseBody(res);
  if (!res.ok) throw toApiError(body, res.status);
  return body as T;
}

/** 멀티파트 업로드 + 진행률(XHR). */
export function uploadFile<T = unknown>(
  path: string,
  file: File,
  onProgress?: (pct: number) => void,
): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const form = new FormData();
    form.append('file', file);
    const xhr = new XMLHttpRequest();
    xhr.open('POST', path);
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable && onProgress) {
        onProgress(Math.round((e.loaded / e.total) * 100));
      }
    };
    xhr.onload = () => {
      let body: unknown;
      try {
        body = xhr.responseText ? JSON.parse(xhr.responseText) : null;
      } catch {
        body = null;
      }
      if (xhr.status >= 200 && xhr.status < 300) resolve(body as T);
      else reject(toApiError(body, xhr.status));
    };
    xhr.onerror = () => reject(new ApiError('INTERNAL_ERROR', 0, 'network error'));
    xhr.send(form);
  });
}
