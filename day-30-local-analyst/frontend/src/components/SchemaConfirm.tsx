import { useEffect, useState } from 'react';
import { getSchema, updateSchema } from '../api/client';
import type { ColumnInfo, SchemaOut } from '../types';

interface Props {
  fileId: string;
  filename: string;
  onConfirmed: (schema: SchemaOut) => void;
}

export function SchemaConfirm({ fileId, filename, onConfirmed }: Props) {
  const [schema, setSchema] = useState<SchemaOut | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [columns, setColumns] = useState<ColumnInfo[]>([]);

  useEffect(() => {
    getSchema(fileId)
      .then((s) => {
        setSchema(s);
        setColumns(s.columns);
      })
      .finally(() => setLoading(false));
  }, [fileId]);

  const handleDescChange = (i: number, val: string) => {
    setColumns((prev) => prev.map((c, idx) => (idx === i ? { ...c, description: val } : c)));
  };

  const handleConfirm = async () => {
    setSaving(true);
    try {
      await updateSchema(fileId, columns);
      onConfirmed({ ...schema!, columns });
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <p style={styles.msg}>Detecting schema for <b>{filename}</b>...</p>;
  if (!schema) return <p style={styles.err}>Failed to load schema</p>;

  return (
    <div style={styles.wrap}>
      <p style={styles.title}>
        Schema for <b>{filename}</b> — {schema.row_count.toLocaleString()} rows
        <span style={styles.badge}>{schema.table_name}</span>
      </p>
      <div style={styles.tableWrap}>
        <table style={styles.table}>
          <thead>
            <tr>
              <th style={styles.th}>Column</th>
              <th style={styles.th}>Type</th>
              <th style={styles.th}>Examples</th>
              <th style={styles.th}>Description (editable)</th>
            </tr>
          </thead>
          <tbody>
            {columns.map((col, i) => (
              <tr key={col.name} style={i % 2 === 0 ? styles.rowEven : styles.rowOdd}>
                <td style={styles.td}><code style={styles.code}>{col.name}</code></td>
                <td style={styles.td}><span style={styles.type}>{col.dtype}</span></td>
                <td style={styles.td}>{col.examples.slice(0, 3).join(', ')}</td>
                <td style={styles.td}>
                  <input
                    style={styles.input}
                    value={col.description}
                    onChange={(e) => handleDescChange(i, e.target.value)}
                    placeholder="e.g. User identifier, error message..."
                  />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <button style={styles.btn} onClick={handleConfirm} disabled={saving}>
        {saving ? 'Saving...' : 'Confirm & Start Analysis'}
      </button>
    </div>
  );
}

const styles: Record<string, React.CSSProperties> = {
  wrap: { background: '#0f172a', borderRadius: 12, padding: 20, marginTop: 16 },
  title: { color: '#e2e8f0', marginBottom: 12, fontSize: 14 },
  badge: {
    background: '#334155', color: '#94a3b8', borderRadius: 4,
    padding: '2px 8px', marginLeft: 8, fontSize: 11,
  },
  tableWrap: { overflowX: 'auto', overflowY: 'auto', maxHeight: 360 },
  table: { width: '100%', borderCollapse: 'collapse', fontSize: 13 },
  th: {
    background: '#1e293b', color: '#94a3b8', padding: '8px 12px',
    textAlign: 'left', fontWeight: 600,
  },
  td: { padding: '6px 12px', color: '#cbd5e1', verticalAlign: 'middle' },
  rowEven: { background: '#0f172a' },
  rowOdd: { background: '#111827' },
  code: { background: '#1e293b', padding: '2px 6px', borderRadius: 3, fontSize: 12 },
  type: { color: '#818cf8', fontSize: 11 },
  input: {
    background: '#1e293b', border: '1px solid #334155', borderRadius: 4,
    color: '#e2e8f0', padding: '4px 8px', fontSize: 12, width: '100%',
    outline: 'none',
  },
  btn: {
    marginTop: 16, padding: '10px 24px', background: '#6366f1', color: '#fff',
    border: 'none', borderRadius: 8, cursor: 'pointer', fontWeight: 600, fontSize: 14,
  },
  msg: { color: '#94a3b8', fontSize: 14 },
  err: { color: '#ef4444', fontSize: 14 },
};
