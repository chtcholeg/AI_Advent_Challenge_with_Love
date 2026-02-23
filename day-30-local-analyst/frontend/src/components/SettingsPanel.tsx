import { useEffect, useState } from 'react';
import { getSettings, updateSettings, getOllamaStatus } from '../api/client';
import type { AppSettings } from '../types';

interface Props {
  onClose: () => void;
}

export function SettingsPanel({ onClose }: Props) {
  const [form, setForm] = useState<AppSettings>({
    ollama_model: '',
    ollama_base_url: 'http://localhost:11434',
  });
  const [models, setModels] = useState<string[]>([]);
  const [loadingModels, setLoadingModels] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    getSettings().then(setForm).catch(() => {});
    fetchModels();
  }, []);

  const fetchModels = async () => {
    setLoadingModels(true);
    try {
      const status = await getOllamaStatus();
      if (status.available && status.models.length > 0) {
        setModels(status.models);
      }
    } catch {
      // ignore
    } finally {
      setLoadingModels(false);
    }
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      await updateSettings(form);
      setSaved(true);
      setTimeout(() => setSaved(false), 2000);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div style={styles.overlay} onClick={onClose}>
      <div style={styles.panel} onClick={(e) => e.stopPropagation()}>
        <div style={styles.header}>
          <h3 style={styles.title}>Settings</h3>
          <button style={styles.close} onClick={onClose}>✕</button>
        </div>

        <label style={styles.label}>Ollama Base URL</label>
        <input
          style={styles.input}
          value={form.ollama_base_url}
          onChange={(e) => setForm((f) => ({ ...f, ollama_base_url: e.target.value }))}
        />

        <label style={styles.label}>
          Model
          {loadingModels && <span style={styles.loadingBadge}>loading...</span>}
          {!loadingModels && models.length > 0 && (
            <span style={styles.countBadge}>{models.length} available</span>
          )}
        </label>

        {models.length > 0 ? (
          <>
            <select
              style={styles.select}
              value={form.ollama_model}
              onChange={(e) => setForm((f) => ({ ...f, ollama_model: e.target.value }))}
            >
              {form.ollama_model && !models.includes(form.ollama_model) && (
                <option value={form.ollama_model}>{form.ollama_model} (current)</option>
              )}
              {models.map((m) => (
                <option key={m} value={m}>{m}</option>
              ))}
            </select>
            <p style={styles.hint}>Or type a custom model name:</p>
            <input
              style={styles.input}
              value={form.ollama_model}
              placeholder="e.g. llama3.1:8b"
              onChange={(e) => setForm((f) => ({ ...f, ollama_model: e.target.value }))}
            />
          </>
        ) : (
          <input
            style={styles.input}
            value={form.ollama_model}
            placeholder="e.g. llama3.1:8b, qwen2.5:7b"
            onChange={(e) => setForm((f) => ({ ...f, ollama_model: e.target.value }))}
          />
        )}

        {models.length === 0 && !loadingModels && (
          <p style={styles.warning}>
            Ollama is not running or has no models pulled.<br />
            Start it with: <code style={styles.code}>ollama serve</code>
          </p>
        )}

        <button style={styles.btn} onClick={handleSave} disabled={saving}>
          {saved ? '✓ Saved!' : saving ? 'Saving...' : 'Save'}
        </button>
      </div>
    </div>
  );
}

const styles: Record<string, React.CSSProperties> = {
  overlay: {
    position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)',
    display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 100,
  },
  panel: {
    background: '#1e293b', borderRadius: 12, padding: 24, width: 420,
    border: '1px solid #334155',
  },
  header: {
    display: 'flex', justifyContent: 'space-between',
    alignItems: 'center', marginBottom: 20,
  },
  title: { color: '#e2e8f0', margin: 0, fontSize: 16 },
  close: {
    background: 'none', border: 'none', color: '#64748b',
    cursor: 'pointer', fontSize: 18,
  },
  label: {
    display: 'flex', alignItems: 'center', gap: 8,
    color: '#94a3b8', fontSize: 12, marginBottom: 6, marginTop: 14,
  },
  loadingBadge: {
    fontSize: 10, color: '#64748b', fontStyle: 'italic',
  },
  countBadge: {
    fontSize: 10, background: '#14532d', color: '#86efac',
    padding: '1px 6px', borderRadius: 10,
  },
  select: {
    width: '100%', background: '#0f172a', border: '1px solid #334155',
    borderRadius: 6, color: '#e2e8f0', padding: '8px 12px',
    fontSize: 13, outline: 'none', cursor: 'pointer',
    boxSizing: 'border-box' as const, marginBottom: 2,
  },
  hint: {
    color: '#475569', fontSize: 11, margin: '8px 0 4px',
  },
  input: {
    width: '100%', background: '#0f172a', border: '1px solid #334155',
    borderRadius: 6, color: '#e2e8f0', padding: '8px 12px',
    fontSize: 13, boxSizing: 'border-box' as const, outline: 'none',
  },
  warning: {
    color: '#f59e0b', fontSize: 12, marginTop: 8,
    background: '#1c1500', padding: '8px 12px',
    borderRadius: 6, lineHeight: 1.6,
  },
  code: {
    background: '#0f172a', padding: '1px 5px',
    borderRadius: 3, fontSize: 11, color: '#93c5fd',
  },
  btn: {
    marginTop: 20, width: '100%', padding: '10px',
    background: '#6366f1', color: '#fff', border: 'none',
    borderRadius: 8, cursor: 'pointer', fontWeight: 600, fontSize: 14,
  },
};
