import React from 'react';
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip } from 'recharts';

export interface CostItem {
  name: string;
  value: number;
}

interface CostChartProps {
  data: CostItem[];
}

const COLORS = ['#3B82F6', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6', '#EC4899', '#06B6D4', '#F97316'];

export const CostChart: React.FC<CostChartProps> = ({ data }) => {
  // Filtrer les valeurs à 0 pour ne pas polluer le graphique
  const filteredData = data.filter(item => item.value > 0);

  if (filteredData.length === 0) return <div className="h-32 flex items-center justify-center text-ink-soft text-xs">Aucun coût à afficher</div>;

  return (
    <div className="h-32 w-full">
      <ResponsiveContainer width="100%" height="100%" minWidth={50} minHeight={50}>
        <PieChart>
          <Pie
            data={filteredData}
            cx="50%"
            cy="50%"
            innerRadius={35}
            outerRadius={50}
            paddingAngle={5}
            dataKey="value"
          >
            {filteredData.map((_entry, index) => (
              <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
            ))}
          </Pie>
          <Tooltip
            contentStyle={{ fontSize: '12px', borderRadius: '8px', border: 'none', boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1)' }}
            formatter={(value) => [`${Number(value).toFixed(2)} €`, '']}
          />
        </PieChart>
      </ResponsiveContainer>
    </div>
  );
};
