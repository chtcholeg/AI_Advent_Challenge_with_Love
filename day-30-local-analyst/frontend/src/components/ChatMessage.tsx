import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import type { MessageOut, ChartData } from '../types';
import { DataTable } from './DataTable';
import { ChartRenderer } from './ChartRenderer';
import { RatingDistributionCharts } from './RatingDistributionCharts';
import { SqlBlock } from './SqlBlock';

interface Props {
  message: MessageOut;
  sessionId: string;
  fileId?: string;
  onSqlResult?: (rows: Record<string, unknown>[]) => void;
}

export function ChatMessage({ message, sessionId, fileId, onSqlResult }: Props) {
  const isUser = message.role === 'user';

  let resultRows: Record<string, unknown>[] | null = null;
  if (message.result_json) {
    try { resultRows = JSON.parse(message.result_json); } catch { /* ignore */ }
  }

  let chartData: ChartData | null = null;
  if (message.chart_json) {
    try {
      const parsed = JSON.parse(message.chart_json);
      chartData = parsed.chart ?? parsed;
    } catch { /* ignore */ }
  }

  return (
    <div style={{ ...styles.wrap, justifyContent: isUser ? 'flex-end' : 'flex-start' }}>
      <div style={{ ...styles.bubble, ...(isUser ? styles.userBubble : styles.aiBubble) }}>
        {isUser ? (
          <p style={styles.userText}>{message.content}</p>
        ) : (
          <>
            <div style={styles.markdown}>
              <ReactMarkdown remarkPlugins={[remarkGfm]}>
                {message.content}
              </ReactMarkdown>
            </div>
            {message.sql_query && fileId && (
              <SqlBlock
                sql={message.sql_query}
                sessionId={sessionId}
                fileId={fileId}
                onResult={onSqlResult}
              />
            )}
            {chartData && <ChartRenderer chart={chartData} />}
            {resultRows && resultRows.length > 0 && <RatingDistributionCharts rows={resultRows} />}
            {resultRows && resultRows.length > 0 && <DataTable rows={resultRows} />}
          </>
        )}
      </div>
    </div>
  );
}

const styles: Record<string, React.CSSProperties> = {
  wrap: { display: 'flex', marginBottom: 16 },
  bubble: { maxWidth: '85%', padding: '12px 16px', borderRadius: 12 },
  userBubble: { background: '#4f46e5', color: '#fff' },
  aiBubble: { background: '#1e293b', color: '#e2e8f0' },
  userText: { margin: 0, fontSize: 14 },
  markdown: { fontSize: 14, lineHeight: 1.6 },
};
