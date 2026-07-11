import React from 'react';
import { 
  BarChart, Bar, LineChart, Line, XAxis, YAxis, 
  CartesianGrid, Tooltip, Legend, ResponsiveContainer 
} from 'recharts';
import type { ChartSlideContent } from '../schemas';

interface Props {
  content: ChartSlideContent;
}

export const ChartSlide: React.FC<Props> = ({ content }) => {
  const ChartComponent = content.type === 'line' ? LineChart : BarChart;
  const DataComponent = content.type === 'line' ? Line : Bar;

  return (
    <div style={{
      width: content.width || '100%',
      height: content.height || '300px',
      ...(content.containerStyle || {})
    }}>
      <ResponsiveContainer width="100%" height="100%">
        <ChartComponent
          data={content.data}
          margin={{ top: 5, right: 30, left: 20, bottom: 5 }}
        >
          {content.showGrid !== false && <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e2e8f0" />}
          <XAxis 
            dataKey={content.xAxisKey} 
            axisLine={false}
            tickLine={false}
            tick={{ fill: '#64748b', fontSize: '0.8cqi' }}
            dy={10}
          />
          <YAxis 
            axisLine={false}
            tickLine={false}
            tick={{ fill: '#64748b', fontSize: '0.8cqi' }}
            dx={-10}
          />
          <Tooltip 
            cursor={{ fill: '#f1f5f9' }}
            contentStyle={{ borderRadius: '8px', border: 'none', boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1)' }}
          />
          {content.showLegend !== false && (
            <Legend wrapperStyle={{ paddingTop: '20px' }} />
          )}
          {content.series.map((s) => (
            <DataComponent 
              key={s.key}
              type="monotone" // for lines
              dataKey={s.key} 
              name={typeof s.name === 'object' ? s.name.text : (s.name || s.key)}
              fill={s.color || '#ea580c'} 
              stroke={s.color || '#ea580c'} 
              strokeWidth={3} // for lines
              radius={(content.type === 'bar' ? [4, 4, 0, 0] : 0) as any} // for bars
              activeDot={{ r: 6 }} // for lines
            />
          ))}
        </ChartComponent>
      </ResponsiveContainer>
    </div>
  );
};
