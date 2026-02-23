import { useRef, useState } from 'react';
import { uploadFile } from '../api/client';
import type { FileOut } from '../types';

interface Props {
  sessionId: string;
  onUploaded: (file: FileOut) => void;
}

const ALLOWED = '.csv,.json,.jsonl,.log,.txt,.db,.sqlite';

export function FileUpload({ sessionId, onUploaded }: Props) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState('');

  const handleFiles = async (files: FileList | null) => {
    if (!files || files.length === 0) return;
    setError('');
    setUploading(true);
    try {
      for (const file of Array.from(files)) {
        const result = await uploadFile(sessionId, file);
        onUploaded(result);
      }
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setUploading(false);
    }
  };

  return (
    <div
      style={{
        ...styles.zone,
        borderColor: dragging ? '#6366f1' : '#334155',
        background: dragging ? '#1e1b4b' : '#0f172a',
      }}
      onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
      onDragLeave={() => setDragging(false)}
      onDrop={(e) => { e.preventDefault(); setDragging(false); handleFiles(e.dataTransfer.files); }}
      onClick={() => inputRef.current?.click()}
    >
      <input
        ref={inputRef}
        type="file"
        multiple
        accept={ALLOWED}
        style={{ display: 'none' }}
        onChange={(e) => handleFiles(e.target.files)}
      />
      {uploading ? (
        <p style={styles.text}>Uploading...</p>
      ) : (
        <>
          <p style={styles.icon}>📂</p>
          <p style={styles.text}>Drop log files here or click to browse</p>
          <p style={styles.hint}>CSV, JSON, JSONL, TXT, LOG, SQLite</p>
        </>
      )}
      {error && <p style={styles.error}>{error}</p>}
    </div>
  );
}

const styles: Record<string, React.CSSProperties> = {
  zone: {
    border: '2px dashed',
    borderRadius: 12,
    padding: '32px 24px',
    textAlign: 'center',
    cursor: 'pointer',
    transition: 'all 0.2s',
  },
  icon: { fontSize: 40, margin: '0 0 8px' },
  text: { color: '#e2e8f0', margin: '0 0 4px', fontSize: 15 },
  hint: { color: '#64748b', margin: 0, fontSize: 12 },
  error: { color: '#ef4444', marginTop: 8, fontSize: 13 },
};
