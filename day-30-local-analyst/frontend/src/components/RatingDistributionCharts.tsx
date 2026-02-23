import { ChartRenderer } from './ChartRenderer';
import type { ChartData } from '../types';

const RATING_COLORS = ['#ef4444', '#f97316', '#f59e0b', '#84cc16', '#22c55e'];

const PRODUCT_EXACT = new Set([
  'product', 'app_name', 'appname', 'name', 'title', 'application',
  'app', 'app_title', 'product_name', 'product_title',
]);
const RATING_SUBSTRINGS = ['rating', 'score', 'star', 'оценка', 'звезд', 'балл'];
const COUNT_SUBSTRINGS = ['count', 'cnt', 'total', 'num', 'кол', 'количест'];

function findProductCol(cols: string[]): string | null {
  for (const c of cols) {
    if (PRODUCT_EXACT.has(c.toLowerCase())) return c;
  }
  for (const c of cols) {
    const cl = c.toLowerCase();
    if (cl.includes('product') || cl.includes('app_name') || cl.includes('appname')) return c;
  }
  return null;
}

function findRatingCol(cols: string[], rows: Record<string, unknown>[]): string | null {
  for (const c of cols) {
    const cl = c.toLowerCase();
    if (RATING_SUBSTRINGS.some((s) => cl.includes(s))) {
      const vals = rows.map((r) => Number(r[c])).filter((v) => !isNaN(v));
      if (vals.length > 0 && vals.every((v) => v >= 1 && v <= 5)) return c;
    }
  }
  return null;
}

function findCountCol(cols: string[], skip: string[]): string | null {
  for (const c of cols) {
    if (skip.includes(c)) continue;
    const cl = c.toLowerCase();
    if (COUNT_SUBSTRINGS.some((s) => cl.includes(s))) return c;
  }
  return null;
}

interface ProductChart {
  product: string;
  chart: ChartData;
}

export function buildPerProductRatingCharts(
  rows: Record<string, unknown>[],
): ProductChart[] | null {
  if (!rows || rows.length < 2) return null;
  const cols = Object.keys(rows[0]);

  const productCol = findProductCol(cols);
  if (!productCol) return null;

  const ratingCol = findRatingCol(cols, rows);
  if (!ratingCol) return null;

  const countCol = findCountCol(cols, [productCol, ratingCol]);
  if (!countCol) return null;

  const products = [...new Set(rows.map((r) => String(r[productCol])))];
  if (products.length < 1) return null;

  return products.map((product) => {
    const productRows = rows.filter((r) => String(r[productCol]) === product);
    const data = [1, 2, 3, 4, 5].map((star) => {
      const row = productRows.find((r) => Number(r[ratingCol]) === star);
      return row ? Number(row[countCol]) : 0;
    });

    const chart: ChartData = {
      type: 'bar',
      labels: ['1 ★', '2 ★', '3 ★', '4 ★', '5 ★'],
      datasets: [
        {
          label: 'Отзывов',
          data,
          backgroundColor: RATING_COLORS,
        },
      ],
    };

    return { product, chart };
  });
}

interface Props {
  rows: Record<string, unknown>[];
}

export function RatingDistributionCharts({ rows }: Props) {
  const charts = buildPerProductRatingCharts(rows);
  if (!charts || charts.length === 0) return null;

  return (
    <div style={styles.wrap}>
      {charts.map(({ product, chart }) => (
        <div key={product} style={styles.productWrap}>
          <p style={styles.title}>{product}</p>
          <ChartRenderer chart={chart} />
        </div>
      ))}
    </div>
  );
}

const styles: Record<string, React.CSSProperties> = {
  wrap: { marginTop: 16 },
  productWrap: { marginBottom: 20 },
  title: { color: '#94a3b8', fontSize: 13, fontWeight: 600, margin: '0 0 4px' },
};
