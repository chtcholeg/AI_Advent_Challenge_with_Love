import { useEffect, useState } from 'react';
import { getOllamaStatus, getSettings } from '../api/client';
import type { OllamaStatus } from '../types';

export function OllamaStatusBadge() {
  const [status, setStatus] = useState<OllamaStatus | null>(null);
  const [model, setModel] = useState<string>('');

  const refresh = () => {
    Promise.allSettled([getOllamaStatus(), getSettings()]).then(([s, m]) => {
      setStatus(
        s.status === 'fulfilled'
          ? s.value
          : { available: false, models: [], error: 'Cannot reach backend' }
      );
      if (m.status === 'fulfilled') setModel(m.value.ollama_model);
    });
  };

  useEffect(() => {
    refresh();
    const iv = setInterval(refresh, 30_000);
    return () => clearInterval(iv);
  }, []);

  if (!status) return <span style={styles.checking}>● Checking...</span>;

  return (
    <span style={status.available ? styles.ok : styles.err} title={status.error ?? ''}>
      {status.available ? `● ${model || 'Ollama OK'}` : '● Ollama offline'}
    </span>
  );
}

const styles: Record<string, React.CSSProperties> = {
  checking: { fontSize: 12, color: '#888' },
  ok: { fontSize: 12, color: '#22c55e', fontWeight: 600 },
  err: { fontSize: 12, color: '#ef4444', fontWeight: 600, cursor: 'help' },
};
