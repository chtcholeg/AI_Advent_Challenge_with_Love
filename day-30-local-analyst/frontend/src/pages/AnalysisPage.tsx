import { useEffect, useRef, useState } from 'react';
import { streamChat, getMessages, exportUrl } from '../api/client';
import type { FileOut, MessageOut, ChartData, SSEStage } from '../types';
import { FileUpload } from '../components/FileUpload';
import { SchemaConfirm } from '../components/SchemaConfirm';
import { ChatMessage } from '../components/ChatMessage';
import { QuickActions } from '../components/QuickActions';
import { ProgressStages } from '../components/ProgressStages';
import { DataTable } from '../components/DataTable';
import { ChartRenderer } from '../components/ChartRenderer';
import { RatingDistributionCharts } from '../components/RatingDistributionCharts';

interface Props {
  sessionId: string;
  sessionName: string;
}

interface StreamState {
  stage: SSEStage | null;
  stageLabel: string;
  streaming: boolean;
  currentText: string;
  currentSql: string | null;
  currentRows: Record<string, unknown>[] | null;
  currentChart: ChartData | null;
  error: string | null;
}

const INIT_STREAM: StreamState = {
  stage: null,
  stageLabel: '',
  streaming: false,
  currentText: '',
  currentSql: null,
  currentRows: null,
  currentChart: null,
  error: null,
};

export function AnalysisPage({ sessionId, sessionName }: Props) {
  const [files, setFiles] = useState<FileOut[]>([]);
  const [confirmedFiles, setConfirmedFiles] = useState<Set<string>>(new Set());
  const [pendingSchema, setPendingSchema] = useState<FileOut | null>(null);
  const [messages, setMessages] = useState<MessageOut[]>([]);
  const [input, setInput] = useState('');
  const [stream, setStream] = useState<StreamState>(INIT_STREAM);
  const abortRef = useRef<AbortController | null>(null);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    getMessages(sessionId).then(setMessages).catch(() => {});
  }, [sessionId]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, stream.currentText]);

  const handleUploaded = (file: FileOut) => {
    setFiles((prev) => [...prev, file]);
    setPendingSchema(file);
  };

  const handleSchemaConfirmed = (file: FileOut) => {
    setConfirmedFiles((prev) => new Set([...prev, file.id]));
    setPendingSchema(null);
  };

  const handleSend = (question: string) => {
    if (!question.trim() || stream.streaming) return;
    setInput('');

    // Optimistic user message
    const userMsg: MessageOut = {
      id: `tmp-${Date.now()}`,
      session_id: sessionId,
      role: 'user',
      content: question,
      created_at: new Date().toISOString(),
    };
    setMessages((prev) => [...prev, userMsg]);

    const ctrl = new AbortController();
    abortRef.current = ctrl;

    setStream({ ...INIT_STREAM, streaming: true });

    const fileIds = files.filter((f) => confirmedFiles.has(f.id)).map((f) => f.id);

    streamChat(sessionId, question, fileIds, ctrl.signal, (type, data) => {
      switch (type) {
        case 'stage':
          setStream((s) => ({
            ...s,
            stage: data.stage as SSEStage,
            stageLabel: String(data.label ?? ''),
          }));
          break;
        case 'token':
          setStream((s) => ({ ...s, currentText: s.currentText + String(data.token ?? '') }));
          break;
        case 'sql':
          setStream((s) => ({ ...s, currentSql: String(data.sql ?? '') }));
          break;
        case 'result':
          setStream((s) => ({ ...s, currentRows: data.rows as Record<string, unknown>[] }));
          break;
        case 'chart':
          setStream((s) => ({ ...s, currentChart: (data.chart as ChartData) ?? null }));
          break;
        case 'error':
          setStream((s) => ({ ...s, streaming: false, error: String(data.message ?? '') }));
          break;
        case 'done':
          setStream((s) => ({ ...s, streaming: false, stage: 'done' }));
          // Перезагружаем историю
          getMessages(sessionId).then(setMessages).catch(() => {});
          break;
      }
    });
  };

  const handleAbort = () => {
    abortRef.current?.abort();
    setStream((s) => ({ ...s, streaming: false }));
  };

  const primaryFileId = files.find((f) => confirmedFiles.has(f.id))?.id;
  const isReady = confirmedFiles.size > 0;

  return (
    <div style={{ ...styles.page, overflow: 'hidden' }}>
      {/* Header */}
      <div style={styles.header}>
        <span style={styles.sessionName}>{sessionName}</span>
        <div style={styles.exportBtns}>
          {(['markdown', 'json', 'csv', 'pdf'] as const).map((fmt) => (
            <a
              key={fmt}
              href={exportUrl(sessionId, fmt)}
              download
              style={styles.exportBtn}
            >
              ↓ {fmt.toUpperCase()}
            </a>
          ))}
        </div>
      </div>

      {/* File upload */}
      <div style={styles.uploadArea}>
        <FileUpload sessionId={sessionId} onUploaded={handleUploaded} />
        {files.length > 0 && (
          <div style={styles.fileList}>
            {files.map((f) => (
              <span
                key={f.id}
                style={{
                  ...styles.fileChip,
                  background: confirmedFiles.has(f.id) ? '#14532d' : '#1e293b',
                  borderColor: confirmedFiles.has(f.id) ? '#22c55e' : '#334155',
                }}
              >
                {confirmedFiles.has(f.id) ? '✓ ' : '⏳ '}{f.filename}
              </span>
            ))}
          </div>
        )}
      </div>

      {/* Schema confirmation */}
      {pendingSchema && (
        <SchemaConfirm
          fileId={pendingSchema.id}
          filename={pendingSchema.filename}
          onConfirmed={() => handleSchemaConfirmed(pendingSchema)}
        />
      )}

      {/* Chat area */}
      {isReady && (
        <div style={styles.chatWrap}>
          {/* Quick actions */}
          {messages.length === 0 && (
            <QuickActions onSelect={handleSend} />
          )}

          {/* Messages */}
          <div style={styles.messages}>
            {messages.map((msg) => (
              <ChatMessage
                key={msg.id}
                message={msg}
                sessionId={sessionId}
                fileId={primaryFileId}
              />
            ))}

            {/* Streaming response */}
            {stream.streaming && (
              <div>
                <ProgressStages stage={stream.stage} label={stream.stageLabel} />
                {stream.currentText && (
                  <div style={styles.streamBubble}>
                    {stream.currentText}
                    <span style={styles.cursor}>▌</span>
                  </div>
                )}
                {stream.currentChart && <ChartRenderer chart={stream.currentChart} />}
                {stream.currentRows && stream.currentRows.length > 0 && (
                  <RatingDistributionCharts rows={stream.currentRows} />
                )}
                {stream.currentRows && stream.currentRows.length > 0 && (
                  <DataTable rows={stream.currentRows} />
                )}
              </div>
            )}

            {stream.error && (
              <div style={styles.errorBubble}>{stream.error}</div>
            )}

            <div ref={bottomRef} />
          </div>

          {/* Input */}
          <div style={styles.inputRow}>
            <input
              style={styles.input}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder="Ask a question about your logs..."
              onKeyDown={(e) => e.key === 'Enter' && !e.shiftKey && handleSend(input)}
              disabled={stream.streaming}
            />
            {stream.streaming ? (
              <button style={styles.stopBtn} onClick={handleAbort}>Stop</button>
            ) : (
              <button style={styles.sendBtn} onClick={() => handleSend(input)} disabled={!input.trim()}>
                Send
              </button>
            )}
          </div>

          {messages.length > 0 && !stream.streaming && (
            <QuickActions onSelect={handleSend} />
          )}
        </div>
      )}
    </div>
  );
}

const styles: Record<string, React.CSSProperties> = {
  page: { display: 'flex', flexDirection: 'column', flex: 1, minWidth: 0, gap: 16 },
  header: {
    display: 'flex', alignItems: 'center', justifyContent: 'space-between',
    paddingBottom: 8, borderBottom: '1px solid #1e293b',
  },
  sessionName: { color: '#e2e8f0', fontWeight: 600, fontSize: 15 },
  exportBtns: { display: 'flex', gap: 8 },
  exportBtn: {
    color: '#64748b', fontSize: 11, textDecoration: 'none', background: '#1e293b',
    padding: '4px 10px', borderRadius: 4, border: '1px solid #334155',
  },
  uploadArea: { flexShrink: 0 },
  fileList: { display: 'flex', flexWrap: 'wrap', gap: 8, marginTop: 8 },
  fileChip: {
    fontSize: 12, padding: '4px 10px', borderRadius: 20, border: '1px solid',
    color: '#94a3b8',
  },
  chatWrap: { display: 'flex', flexDirection: 'column', flex: 1, minHeight: 0, minWidth: 0 },
  messages: { flex: 1, overflowY: 'auto', overflowX: 'hidden', padding: '8px 0', minHeight: 0 },
  streamBubble: {
    background: '#1e293b', borderRadius: 12, padding: '12px 16px',
    color: '#e2e8f0', fontSize: 14, lineHeight: 1.6, maxWidth: '85%',
  },
  cursor: { animation: 'blink 1s step-end infinite', color: '#6366f1' },
  errorBubble: {
    background: '#450a0a', borderRadius: 12, padding: '12px 16px',
    color: '#fca5a5', fontSize: 13, marginBottom: 8,
  },
  inputRow: { display: 'flex', gap: 8, paddingTop: 8 },
  input: {
    flex: 1, background: '#1e293b', border: '1px solid #334155', borderRadius: 10,
    color: '#e2e8f0', padding: '10px 16px', fontSize: 14, outline: 'none',
  },
  sendBtn: {
    background: '#6366f1', color: '#fff', border: 'none', borderRadius: 10,
    padding: '10px 20px', cursor: 'pointer', fontWeight: 600, fontSize: 14,
  },
  stopBtn: {
    background: '#ef4444', color: '#fff', border: 'none', borderRadius: 10,
    padding: '10px 20px', cursor: 'pointer', fontWeight: 600, fontSize: 14,
  },
};
