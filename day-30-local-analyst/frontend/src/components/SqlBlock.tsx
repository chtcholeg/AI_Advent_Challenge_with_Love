import { useState } from 'react';
import { runSql } from '../api/client';

interface Props {
  sql: string;
  sessionId: string;
  fileId: string;
  onResult?: (rows: Record<string, unknown>[]) => void;
}

export function SqlBlock({ sql, sessionId, fileId, onResult }: Props) {
  const [expanded, setExpanded] = useState(false);
  const [editing, setEditing] = useState(false);
  const [editedSql, setEditedSql] = useState(sql);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState('');

  const handleRun = async () => {
    setRunning(true);
    setError('');
    try {
      const res = await runSql(sessionId, editedSql, fileId);
      onResult?.(res.rows);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setRunning(false);
    }
  };

  return (
    <div style={styles.wrap}>
      <button style={styles.toggle} onClick={() => setExpanded((v) => !v)}>
        {expanded ? '▾' : '▸'} SQL query
      </button>
      {expanded && (
        <div style={styles.body}>
          {editing ? (
            <textarea
              style={styles.textarea}
              value={editedSql}
              onChange={(e) => setEditedSql(e.target.value)}
              rows={6}
            />
          ) : (
            <pre style={styles.pre}><code>{editedSql}</code></pre>
          )}
          <div style={styles.actions}>
            <button style={styles.btnEdit} onClick={() => setEditing((v) => !v)}>
              {editing ? 'Cancel' : 'Edit'}
            </button>
            {editing && (
              <button style={styles.btnRun} onClick={handleRun} disabled={running}>
                {running ? 'Running...' : 'Run'}
              </button>
            )}
          </div>
          {error && <p style={styles.error}>{error}</p>}
        </div>
      )}
    </div>
  );
}

const styles: Record<string, React.CSSProperties> = {
  wrap: { marginTop: 8 },
  toggle: {
    background: 'none', border: 'none', color: '#64748b', cursor: 'pointer',
    fontSize: 12, padding: '2px 0',
  },
  body: { background: '#0f172a', borderRadius: 6, padding: 12, marginTop: 4 },
  pre: { margin: 0, color: '#93c5fd', fontSize: 12, overflowX: 'auto' },
  textarea: {
    width: '100%', background: '#1e293b', border: '1px solid #334155',
    color: '#e2e8f0', borderRadius: 4, padding: 8, fontSize: 12,
    fontFamily: 'monospace', resize: 'vertical', boxSizing: 'border-box',
  },
  actions: { display: 'flex', gap: 8, marginTop: 8 },
  btnEdit: {
    background: '#334155', color: '#94a3b8', border: 'none', borderRadius: 6,
    padding: '5px 14px', cursor: 'pointer', fontSize: 12,
  },
  btnRun: {
    background: '#6366f1', color: '#fff', border: 'none', borderRadius: 6,
    padding: '5px 14px', cursor: 'pointer', fontSize: 12,
  },
  error: { color: '#ef4444', fontSize: 12, margin: '6px 0 0' },
};
