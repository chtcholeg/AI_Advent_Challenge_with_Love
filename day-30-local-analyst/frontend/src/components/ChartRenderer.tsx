import {
  BarChart, Bar, LineChart, Line, PieChart, Pie, Cell,
  XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer,
} from 'recharts';
import type { ChartData } from '../types';

interface Props {
  chart: ChartData;
}

const COLORS = ['#6366f1', '#22c55e', '#f59e0b', '#ef4444', '#06b6d4', '#a78bfa', '#fb923c'];

export function ChartRenderer({ chart }: Props) {
  const { type, labels, datasets } = chart;

  // Recharts expects data in [{name, value1, value2, ...}] format
  const data = labels.map((label, i) => {
    const point: Record<string, unknown> = { name: String(label) };
    datasets.forEach((ds) => {
      const key = ds.label || 'value';
      point[key] = ds.data[i] ?? 0;
    });
    return point;
  });

  const keys = datasets.map((ds) => ds.label || 'value');

  if (type === 'pie') {
    const pieData = labels.map((label, i) => ({
      name: String(label),
      value: datasets[0]?.data[i] ?? 0,
    }));
    return (
      <div style={styles.wrap}>
        <ResponsiveContainer width="100%" height={280}>
          <PieChart>
            <Pie dataKey="value" data={pieData} label={({ name, percent }) =>
              `${name} ${((percent ?? 0) * 100).toFixed(0)}%`
            }>
              {pieData.map((_, i) => (
                <Cell key={i} fill={COLORS[i % COLORS.length]} />
              ))}
            </Pie>
            <Tooltip />
          </PieChart>
        </ResponsiveContainer>
      </div>
    );
  }

  if (type === 'line') {
    return (
      <div style={styles.wrap}>
        <ResponsiveContainer width="100%" height={260}>
          <LineChart data={data}>
            <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
            <XAxis dataKey="name" tick={{ fill: '#94a3b8', fontSize: 11 }} />
            <YAxis tick={{ fill: '#94a3b8', fontSize: 11 }} />
            <Tooltip contentStyle={tooltipStyle} />
            <Legend />
            {keys.map((k, i) => (
              <Line key={k} type="monotone" dataKey={k} stroke={COLORS[i % COLORS.length]} dot={false} />
            ))}
          </LineChart>
        </ResponsiveContainer>
      </div>
    );
  }

  // default: bar
  return (
    <div style={styles.wrap}>
      <ResponsiveContainer width="100%" height={260}>
        <BarChart data={data}>
          <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
          <XAxis dataKey="name" tick={{ fill: '#94a3b8', fontSize: 11 }} />
          <YAxis tick={{ fill: '#94a3b8', fontSize: 11 }} />
          <Tooltip contentStyle={tooltipStyle} />
          <Legend />
          {keys.map((k, i) => {
            const ds = datasets[i];
            const perBarColors = Array.isArray(ds?.backgroundColor) ? ds.backgroundColor as string[] : null;
            return (
              <Bar key={k} dataKey={k} fill={COLORS[i % COLORS.length]} radius={[3, 3, 0, 0]}>
                {perBarColors && data.map((_, j) => (
                  <Cell key={j} fill={perBarColors[j] ?? COLORS[j % COLORS.length]} />
                ))}
              </Bar>
            );
          })}
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

const tooltipStyle: React.CSSProperties = {
  background: '#1e293b', border: '1px solid #334155', color: '#e2e8f0', fontSize: 12,
};

const styles: Record<string, React.CSSProperties> = {
  wrap: { marginTop: 16, marginBottom: 8 },
};
