interface Props {
  onSelect: (question: string) => void;
}

const PRESETS = [
  { label: 'Проблемы по продуктам', q: 'Проанализируй проблемы и жалобы для каждого продукта' },
  { label: 'Распределение оценок', q: 'Покажи количество отзывов с каждой оценкой (1, 2, 3, 4, 5 звёзд) для каждого продукта' },
  { label: 'Динамика отзывов', q: 'Как менялось количество отзывов по месяцам?' },
  { label: 'Общая сводка', q: 'Дай общую сводку по данным' },
  { label: 'Лучшие отзывы', q: 'Какие продукты получают лучшие отзывы и почему?' },
];

export function QuickActions({ onSelect }: Props) {
  return (
    <div style={styles.wrap}>
      <p style={styles.label}>Quick questions:</p>
      <div style={styles.row}>
        {PRESETS.map((p) => (
          <button key={p.q} style={styles.btn} onClick={() => onSelect(p.q)}>
            {p.label}
          </button>
        ))}
      </div>
    </div>
  );
}

const styles: Record<string, React.CSSProperties> = {
  wrap: { padding: '8px 0 4px' },
  label: { color: '#64748b', fontSize: 11, margin: '0 0 8px', textTransform: 'uppercase', letterSpacing: 1 },
  row: { display: 'flex', flexWrap: 'wrap', gap: 6 },
  btn: {
    background: '#1e293b', border: '1px solid #334155', color: '#94a3b8',
    borderRadius: 20, padding: '5px 14px', fontSize: 12, cursor: 'pointer',
    transition: 'all 0.15s',
  },
};
