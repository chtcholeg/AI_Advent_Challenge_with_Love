import type { SSEStage } from '../types';

interface Props {
  stage: SSEStage | null;
  label: string;
}

const STAGE_ORDER: SSEStage[] = ['schema', 'sql_gen', 'sql_exec', 'llm'];
const SEMANTIC_STAGE_ORDER: SSEStage[] = ['schema', 'semantic'];
const STAGE_LABELS: Record<SSEStage, string> = {
  schema: 'Схема',
  chunking: 'Чанкинг',
  sql_gen: 'SQL',
  sql_retry: 'Retry',
  sql_exec: 'Выполнение',
  llm: 'Ответ',
  semantic: 'Кластеризация',
  done: 'Готово',
};

export function ProgressStages({ stage, label }: Props) {
  if (!stage) return null;

  const isDone = stage === 'done';
  const isSemantic = stage === 'semantic';
  const order = isSemantic ? SEMANTIC_STAGE_ORDER : STAGE_ORDER;
  const currentIdx = order.indexOf(stage as SSEStage);

  return (
    <div style={styles.wrap}>
      <div style={styles.stages}>
        {order.map((s, i) => {
          const done = isDone || i < currentIdx;
          const active = !isDone && i === currentIdx;
          return (
            <span
              key={s}
              style={{
                ...styles.stage,
                color: done ? '#22c55e' : active ? '#6366f1' : '#334155',
                fontWeight: active ? 700 : 400,
              }}
            >
              {done ? '✓' : active ? '●' : '○'} {STAGE_LABELS[s]}
            </span>
          );
        })}
      </div>
      {!isDone && <p style={styles.label}>{label}</p>}
    </div>
  );
}

const styles: Record<string, React.CSSProperties> = {
  wrap: { padding: '8px 0' },
  stages: { display: 'flex', gap: 16, flexWrap: 'wrap' },
  stage: { fontSize: 12, transition: 'color 0.3s' },
  label: { color: '#6366f1', fontSize: 12, margin: '6px 0 0', fontStyle: 'italic' },
};
