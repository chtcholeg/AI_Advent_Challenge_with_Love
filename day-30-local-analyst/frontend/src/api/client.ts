import type {
  Session,
  FileOut,
  SchemaOut,
  ColumnInfo,
  MessageOut,
  OllamaStatus,
  AppSettings,
} from '../types';

const BASE = import.meta.env.VITE_API_URL ?? 'http://localhost:8000';

async function req<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${path}`, options);
  if (!res.ok) {
    const err = await res.json().catch(() => ({ detail: res.statusText }));
    throw new Error(err.detail ?? 'Request failed');
  }
  return res.json() as Promise<T>;
}

// Sessions
export const createSession = (name: string) =>
  req<Session>('/api/sessions', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name }),
  });

export const listSessions = () => req<Session[]>('/api/sessions');

export const deleteSession = (id: string) =>
  req<{ ok: boolean }>(`/api/sessions/${id}`, { method: 'DELETE' });

export const getMessages = (sessionId: string) =>
  req<MessageOut[]>(`/api/sessions/${sessionId}/messages`);

// Files
export const uploadFile = (sessionId: string, file: File): Promise<FileOut> => {
  const form = new FormData();
  form.append('session_id', sessionId);
  form.append('file', file);
  return req<FileOut>('/api/upload', { method: 'POST', body: form });
};

export const getSessionFiles = (sessionId: string) =>
  req<FileOut[]>(`/api/sessions/${sessionId}/files`);

export const getSchema = (fileId: string) =>
  req<SchemaOut>(`/api/files/${fileId}/schema`);

export const updateSchema = (fileId: string, columns: ColumnInfo[]) =>
  req<{ ok: boolean }>(`/api/files/${fileId}/schema`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ columns }),
  });

// SQL manual run
export const runSql = (sessionId: string, sql: string, fileId: string) =>
  req<{ rows: Record<string, unknown>[]; count: number }>(
    `/api/sessions/${sessionId}/chat/sql`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sql, file_id: fileId }),
    }
  );

// Settings
export const getOllamaStatus = () => req<OllamaStatus>('/api/ollama/status');
export const getSettings = () => req<AppSettings>('/api/settings');
export const updateSettings = (data: Partial<AppSettings>) =>
  req<{ ok: boolean }>('/api/settings', {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  });

// Export (download links)
export const exportUrl = (sessionId: string, format: 'markdown' | 'json' | 'csv' | 'pdf') =>
  `${BASE}/api/sessions/${sessionId}/export/${format}`;

// SSE chat stream
export function streamChat(
  sessionId: string,
  question: string,
  fileIds: string[],
  signal: AbortSignal,
  onEvent: (type: string, data: Record<string, unknown>) => void
): void {
  fetch(`${BASE}/api/sessions/${sessionId}/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ question, file_ids: fileIds }),
    signal,
  }).then(async (res) => {
    if (!res.body) return;
    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });

      const parts = buffer.split('\n\n');
      buffer = parts.pop() ?? '';

      for (const part of parts) {
        const lines = part.split('\n');
        let eventType = 'message';
        let dataStr = '';
        for (const line of lines) {
          if (line.startsWith('event: ')) eventType = line.slice(7).trim();
          if (line.startsWith('data: ')) dataStr = line.slice(6).trim();
        }
        if (dataStr) {
          try {
            onEvent(eventType, JSON.parse(dataStr));
          } catch {
            // ignore parse errors
          }
        }
      }
    }
  }).catch((err) => {
    if (err.name !== 'AbortError') {
      onEvent('error', { message: String(err) });
    }
  });
}
