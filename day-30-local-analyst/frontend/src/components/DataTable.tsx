import { useState } from 'react';

interface Props {
  rows: Record<string, unknown>[];
  maxRows?: number;
}

export function DataTable({ rows, maxRows = 50 }: Props) {
  const [sortCol, setSortCol] = useState<string | null>(null);
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>('asc');

  if (!rows.length) return null;

  const headers = Object.keys(rows[0]);

  const handleSort = (col: string) => {
    if (sortCol === col) {
      setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortCol(col);
      setSortDir('asc');
    }
  };

  const sorted = [...rows].sort((a, b) => {
    if (!sortCol) return 0;
    const av = a[sortCol];
    const bv = b[sortCol];
    if (av === bv) return 0;
    const cmp = String(av ?? '').localeCompare(String(bv ?? ''), undefined, { numeric: true });
    return sortDir === 'asc' ? cmp : -cmp;
  });

  const visible = sorted.slice(0, maxRows);

  return (
    <div style={styles.wrap}>
      <div style={styles.tableWrap}>
        <table style={styles.table}>
          <thead>
            <tr>
              {headers.map((h) => (
                <th
                  key={h}
                  style={styles.th}
                  onClick={() => handleSort(h)}
                  title="Click to sort"
                >
                  {h} {sortCol === h ? (sortDir === 'asc' ? '↑' : '↓') : ''}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {visible.map((row, i) => (
              <tr key={i} style={i % 2 === 0 ? styles.rowEven : styles.rowOdd}>
                {headers.map((h) => (
                  <td key={h} style={styles.td}>
                    {String(row[h] ?? '')}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {rows.length > maxRows && (
        <p style={styles.hint}>Showing {maxRows} of {rows.length} rows</p>
      )}
    </div>
  );
}

const styles: Record<string, React.CSSProperties> = {
  wrap: { marginTop: 12 },
  tableWrap: { overflowX: 'auto' },
  table: { width: '100%', borderCollapse: 'collapse', fontSize: 12 },
  th: {
    background: '#1e293b', color: '#94a3b8', padding: '7px 10px',
    textAlign: 'left', cursor: 'pointer', userSelect: 'none', whiteSpace: 'nowrap',
  },
  td: { padding: '5px 10px', color: '#cbd5e1', borderBottom: '1px solid #1e293b' },
  rowEven: { background: '#0f172a' },
  rowOdd: { background: '#0d1424' },
  hint: { color: '#64748b', fontSize: 11, margin: '6px 0 0' },
};
