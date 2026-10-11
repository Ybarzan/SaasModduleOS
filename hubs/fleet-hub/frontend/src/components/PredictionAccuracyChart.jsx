import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'

export default function PredictionAccuracyChart({ data }) {
  return (
    <div style={{ height: 256, width: '100%' }}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} barGap={4} margin={{ top: 8, right: 8, left: -16, bottom: 0 }}>
          <CartesianGrid stroke="rgba(148, 163, 184, 0.15)" strokeDasharray="3 3" vertical={false} />
          <XAxis dataKey="label" tick={{ fontSize: 11, fill: 'var(--muted)' }} axisLine={{ stroke: 'var(--border-strong)' }} tickLine={false} />
          <YAxis
            tick={{ fontSize: 11, fill: 'var(--muted)' }}
            axisLine={false}
            tickLine={false}
            label={{ value: 'jours', angle: -90, position: 'insideLeft', fontSize: 11, fill: 'var(--muted)' }}
          />
          <Tooltip
            contentStyle={{ fontSize: 12, borderRadius: 10, border: '1px solid #32405a', background: '#16202e', color: '#dbe3ee' }}
            formatter={(value) => [`${value} jours`, '']}
          />
          <Legend wrapperStyle={{ fontSize: 12 }} />
          <Bar dataKey="predicted" name="Prédit" fill="var(--primary)" radius={[4, 4, 0, 0]} maxBarSize={28} />
          <Bar dataKey="actual" name="Réel" fill="var(--primary-2)" radius={[4, 4, 0, 0]} maxBarSize={28} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}
