export interface Session {
  id: string;
  name: string;
  created_at: string;
}

export interface ColumnInfo {
  name: string;
  dtype: string;
  description: string;
  examples: unknown[];
}

export interface SchemaOut {
  file_id: string;
  columns: ColumnInfo[];
  row_count: number;
  table_name: string;
}

export interface FileOut {
  id: string;
  session_id: string;
  filename: string;
  format: string;
  row_count: number | null;
  created_at: string;
}

export interface MessageOut {
  id: string;
  session_id: string;
  role: 'user' | 'assistant';
  content: string;
  sql_query?: string;
  result_json?: string;
  chart_json?: string;
  created_at: string;
}

export interface OllamaStatus {
  available: boolean;
  models: string[];
  error?: string;
}

export interface AppSettings {
  ollama_model: string;
  ollama_base_url: string;
}

export interface ChartData {
  type: 'bar' | 'line' | 'pie';
  labels: string[];
  datasets: {
    label?: string;
    data: number[];
    backgroundColor?: string | string[];
  }[];
}

export type SSEStage =
  | 'schema'
  | 'chunking'
  | 'sql_gen'
  | 'sql_retry'
  | 'sql_exec'
  | 'llm'
  | 'semantic'
  | 'done';

export interface SSEEvent {
  type: 'stage' | 'token' | 'sql' | 'result' | 'chart' | 'cache_hit' | 'done' | 'error';
  data: Record<string, unknown>;
}
