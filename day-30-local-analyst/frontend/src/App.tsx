import { useEffect, useState } from 'react';
import { createSession, listSessions, deleteSession } from './api/client';
import type { Session } from './types';
import { AnalysisPage } from './pages/AnalysisPage';
import { OllamaStatusBadge } from './components/OllamaStatusBadge';
import { SettingsPanel } from './components/SettingsPanel';
import './App.css';

export default function App() {
  const [sessions, setSessions] = useState<Session[]>([]);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [showSettings, setShowSettings] = useState(false);
  const [creating, setCreating] = useState(false);

  useEffect(() => {
    listSessions()
      .then((list) => {
        setSessions(list);
        if (list.length > 0) setActiveId(list[0].id);
      })
      .catch(() => {});
  }, []);

  const handleNew = async () => {
    setCreating(true);
    try {
      const now = new Date();
      const name = `Session ${now.toLocaleDateString()} ${now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`;
      const s = await createSession(name);
      setSessions((prev) => [s, ...prev]);
      setActiveId(s.id);
    } finally {
      setCreating(false);
    }
  };

  const handleDelete = async (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    await deleteSession(id);
    setSessions((prev) => prev.filter((s) => s.id !== id));
    if (activeId === id) setActiveId(sessions.find((s) => s.id !== id)?.id ?? null);
  };

  const active = sessions.find((s) => s.id === activeId);

  return (
    <div style={styles.root}>
      <aside style={styles.sidebar}>
        <div style={styles.sidebarTop}>
          <h1 style={styles.logo}>Log Analyst</h1>
          <OllamaStatusBadge />
        </div>
        <button style={styles.newBtn} onClick={handleNew} disabled={creating}>
          + New Session
        </button>
        <div style={styles.sessionList}>
          {sessions.map((s) => (
            <div
              key={s.id}
              style={{
                ...styles.sessionItem,
                background: s.id === activeId ? '#334155' : 'transparent',
              }}
              onClick={() => setActiveId(s.id)}
            >
              <span style={styles.sessionItemName}>{s.name}</span>
              <button
                style={styles.deleteBtn}
                onClick={(e) => handleDelete(s.id, e)}
                title="Delete session"
              >
                ✕
              </button>
            </div>
          ))}
          {sessions.length === 0 && (
            <p style={styles.emptyHint}>No sessions yet. Create one!</p>
          )}
        </div>
        <button style={styles.settingsBtn} onClick={() => setShowSettings(true)}>
          ⚙ Settings
        </button>
      </aside>

      <main style={styles.main}>
        {active ? (
          <AnalysisPage sessionId={active.id} sessionName={active.name} />
        ) : (
          <div style={styles.empty}>
            <p style={styles.emptyText}>Select or create a session to start analyzing logs</p>
            <button style={styles.newBtnLarge} onClick={handleNew}>
              + New Session
            </button>
          </div>
        )}
      </main>

      {showSettings && <SettingsPanel onClose={() => setShowSettings(false)} />}
    </div>
  );
}

const styles: Record<string, React.CSSProperties> = {
  root: { display: 'flex', height: '100vh', width: '100%', background: '#0a0f1e', fontFamily: 'system-ui, sans-serif' },
  sidebar: {
    width: 240, background: '#0f172a', borderRight: '1px solid #1e293b',
    display: 'flex', flexDirection: 'column', padding: 16, gap: 8, flexShrink: 0,
  },
  sidebarTop: { display: 'flex', flexDirection: 'column', gap: 4, marginBottom: 4 },
  logo: { color: '#e2e8f0', fontSize: 16, fontWeight: 700, margin: 0 },
  newBtn: {
    background: '#6366f1', color: '#fff', border: 'none', borderRadius: 8,
    padding: '8px 12px', cursor: 'pointer', fontWeight: 600, fontSize: 13,
  },
  sessionList: { flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 2 },
  sessionItem: {
    display: 'flex', alignItems: 'center', justifyContent: 'space-between',
    padding: '7px 10px', borderRadius: 8, cursor: 'pointer', transition: 'background 0.15s',
  },
  sessionItemName: {
    color: '#cbd5e1', fontSize: 12, flex: 1,
    overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
  },
  deleteBtn: {
    background: 'none', border: 'none', color: '#475569',
    cursor: 'pointer', fontSize: 11, padding: '0 2px', flexShrink: 0,
  },
  emptyHint: { color: '#475569', fontSize: 12, margin: '8px 0', textAlign: 'center' },
  settingsBtn: {
    background: 'none', border: '1px solid #334155', color: '#64748b',
    borderRadius: 8, padding: '7px 12px', cursor: 'pointer', fontSize: 12,
  },
  main: { flex: 1, minWidth: 0, overflow: 'hidden', padding: 24, display: 'flex', flexDirection: 'column' },
  empty: {
    flex: 1, display: 'flex', flexDirection: 'column',
    alignItems: 'center', justifyContent: 'center', gap: 16,
  },
  emptyText: { color: '#475569', fontSize: 15 },
  newBtnLarge: {
    background: '#6366f1', color: '#fff', border: 'none', borderRadius: 10,
    padding: '12px 32px', cursor: 'pointer', fontWeight: 600, fontSize: 15,
  },
};
