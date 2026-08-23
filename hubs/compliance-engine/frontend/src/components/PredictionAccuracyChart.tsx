import { useEffect, useState } from 'react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { onThemeChange } from '../lib/theme';

export interface AccuracyPoint {
  label: string;
  predicted: number;
  actual: number;
}

interface PredictionAccuracyChartProps {
  data: AccuracyPoint[];
}

// rgb(var(--c-x)) est évalué en dehors du SVG (recharts injecte la couleur en style inline),
// donc on résout les tokens via getComputedStyle plutôt que de passer la chaîne CSS brute.
// La résolution doit être refaite à chaque changement de thème (voir lib/theme.ts) sinon
// les couleurs restent figées sur celles du thème actif au premier rendu.
const resolveToken = (token: string): string => {
  if (typeof window === 'undefined') return '#1B6B4F';
  const value = getComputedStyle(document.documentElement).getPropertyValue(token).trim();
  return value ? `rgb(${value})` : '#1B6B4F';
};

const resolveColors = () => ({
  predictedColor: resolveToken('--c-accent'),
  actualColor: '#2a78d6',
  gridColor: resolveToken('--c-line'),
  inkSoftColor: resolveToken('--c-ink-soft'),
  surfaceColor: resolveToken('--c-surface'),
  inkColor: resolveToken('--c-ink'),
});

export const PredictionAccuracyChart = ({ data }: PredictionAccuracyChartProps) => {
  const [colors, setColors] = useState(resolveColors);

  useEffect(() => onThemeChange(() => setColors(resolveColors())), []);

  const { predictedColor, actualColor, gridColor, inkSoftColor, surfaceColor, inkColor } = colors;

  return (
    <div className="h-64 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} barGap={4} margin={{ top: 8, right: 8, left: -16, bottom: 0 }}>
          <CartesianGrid stroke={gridColor} strokeDasharray="3 3" vertical={false} />
          <XAxis
            dataKey="label"
            tick={{ fontSize: 11, fill: inkSoftColor }}
            axisLine={{ stroke: gridColor }}
            tickLine={false}
          />
          <YAxis
            tick={{ fontSize: 11, fill: inkSoftColor }}
            axisLine={false}
            tickLine={false}
            label={{ value: 'jours', angle: -90, position: 'insideLeft', fontSize: 11, fill: inkSoftColor }}
          />
          <Tooltip
            contentStyle={{
              fontSize: '12px',
              borderRadius: '8px',
              border: 'none',
              backgroundColor: surfaceColor,
              color: inkColor,
              boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.15)',
            }}
            formatter={(value) => [`${value} jours`, '']}
          />
          <Legend wrapperStyle={{ fontSize: '12px' }} formatter={(value) => <span style={{ color: inkColor }}>{value}</span>} />
          <Bar dataKey="predicted" name="Prédit" fill={predictedColor} radius={[4, 4, 0, 0]} maxBarSize={28} />
          <Bar dataKey="actual" name="Réel" fill={actualColor} radius={[4, 4, 0, 0]} maxBarSize={28} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
};
