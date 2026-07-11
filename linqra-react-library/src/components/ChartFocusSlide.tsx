import React from 'react';
import type { ChartFocusSlideContent } from '../schemas';
import { ResponsiveContainer, BarChart, Bar, LineChart, Line, PieChart, Pie, XAxis, YAxis, Tooltip, CartesianGrid, Cell, AreaChart, Area, RadarChart, PolarGrid, PolarAngleAxis, PolarRadiusAxis, Radar, LabelList, Legend, ComposedChart, FunnelChart, Funnel, ScatterChart, Scatter, ZAxis, RadialBarChart, RadialBar, Treemap } from 'recharts';
import { renderText } from '../utils';

interface Props {
  content: ChartFocusSlideContent;
}

const COLORS = ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899', '#06b6d4'];

export const StandaloneChart: React.FC<{ chartSpec: ChartFocusSlideContent['chartSpec'] }> = ({ chartSpec }) => {
  const seriesToRender = chartSpec.series || [{ dataKey: 'value', color: chartSpec.primaryColor }];
  const formatLabel = (val: any) => Array.isArray(val) ? val[1] - val[0] : val;
  const displayLegend = chartSpec.showLegend !== undefined ? chartSpec.showLegend : seriesToRender.length > 1;
  const renderLegend = () => {
    if (!displayLegend) return null;
    if (chartSpec.legendPayload) {
      return (
        <Legend
          wrapperStyle={{ paddingTop: '10px', ...chartSpec.legendStyle }}
          content={() => (
            <ul style={{ display: 'flex', justifyContent: 'center', gap: '20px', padding: 0, margin: 0, listStyle: 'none', fontSize: chartSpec.legendStyle?.fontSize || '12px', ...chartSpec.legendStyle as any }}>
              {chartSpec.legendPayload!.map((item, idx) => (
                <li key={idx} style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                  <svg width="10" height="10" viewBox="0 0 10 10">
                    {item.type === 'rect' || item.type === 'square' ? (
                      <rect width="10" height="10" fill={item.color} />
                    ) : (
                      <circle cx="5" cy="5" r="5" fill={item.color} />
                    )}
                  </svg>
                  <span style={{ color: 'var(--text-main)', ...chartSpec.legendStyle as any }}>{item.value}</span>
                </li>
              ))}
            </ul>
          )}
        />
      );
    }
    return (
      <Legend 
        wrapperStyle={{ paddingTop: '10px', ...chartSpec.legendStyle }} 
        formatter={(value) => <span style={{ color: 'var(--text-main)', fontSize: '14px', ...chartSpec.legendStyle as any }}>{value}</span>}
      />
    );
  };

  const mappedData = React.useMemo(() => {
    if (chartSpec.chartType === 'funnel') {
      return chartSpec.data.map(d => ({ ...d, labelWithValue: `${d.label}: ${d.value}` }));
    }
    if (chartSpec.chartType === 'scatter') {
      return chartSpec.data.map(d => ({ ...d, xyLabel: `(${d.x}, ${d.y})` }));
    }
    if (chartSpec.chartType === 'radial_bar') {
      return chartSpec.data.map((d, index) => ({ 
        ...d, 
        name: d.label, 
        fill: d.color || COLORS[index % COLORS.length] 
      }));
    }
    return chartSpec.data;
  }, [chartSpec.data, chartSpec.chartType]);

  const renderCustomTick = React.useCallback((isXAxis: boolean, baseStyle: any) => (props: any) => {
    const { x, y, payload } = props;
    const dataItem = chartSpec.data.find(d => d.label === payload.value);
    const customStyle = dataItem?.labelStyle || {};
    return (
      <text 
        x={x} 
        y={y} 
        dy={isXAxis ? 16 : 4} 
        textAnchor={isXAxis ? "middle" : "end"} 
        fill="var(--text-secondary)" 
        style={{ fontSize: 11, ...baseStyle, ...customStyle }}
      >
        {payload.value}
      </text>
    );
  }, [chartSpec.data]);



  switch (chartSpec.chartType) {
    case 'bar':
    case 'stacked_bar':
    case 'horizontal_bar': {
      const isHorizontal = chartSpec.chartType === 'horizontal_bar';
      const isStacked = chartSpec.chartType === 'stacked_bar';
      return (
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={chartSpec.data} margin={{ top: 20, right: 30, left: 0, bottom: 5 }} layout={isHorizontal ? 'vertical' : 'horizontal'}>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--card-border)" />
            <XAxis dataKey={isHorizontal ? undefined : "label"} type={isHorizontal ? "number" : "category"} stroke="var(--text-secondary)" interval={0} tick={renderCustomTick(true, chartSpec.xAxisStyle as any)} />
            <YAxis dataKey={isHorizontal ? "label" : undefined} type={isHorizontal ? "category" : "number"} stroke="var(--text-secondary)" width={isHorizontal ? 100 : undefined} interval={0} tick={renderCustomTick(false, chartSpec.yAxisStyle as any)} />
            <Tooltip contentStyle={{ backgroundColor: 'var(--card-bg)', borderColor: 'var(--card-border)', color: 'var(--text-main)', ...chartSpec.tooltipStyle }} />
            {renderLegend()}
            {seriesToRender.map((s, idx) => (
              <Bar key={s.dataKey} dataKey={s.dataKey} name={typeof s.name === 'object' ? s.name.text : (s.name || s.dataKey)} fill={s.color || COLORS[idx % COLORS.length]} stackId={isStacked ? (s.stackId || "a") : undefined} radius={isStacked ? undefined : [4, 4, 0, 0]}>
                {!chartSpec.series && chartSpec.data.map((entry, index) => (
                  <Cell key={`cell-${index}`} fill={entry.color || s.color || "#3b82f6"} />
                ))}
                <LabelList dataKey={s.dataKey} position={isStacked ? "center" : isHorizontal ? "right" : "top"} offset={isStacked ? 0 : 10} fill={isStacked ? "#ffffff" : "var(--text-secondary)"} fontSize={12} formatter={formatLabel} />
              </Bar>
            ))}
          </BarChart>
        </ResponsiveContainer>
      );
    }
    case 'line':
      return (
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={chartSpec.data} margin={{ top: 20, right: 30, left: 0, bottom: 5 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--card-border)" />
            <XAxis dataKey="label" stroke="var(--text-secondary)" interval={0} tick={renderCustomTick(true, chartSpec.xAxisStyle as any)} />
            <YAxis stroke="var(--text-secondary)" interval={0} tick={renderCustomTick(false, chartSpec.yAxisStyle as any)} />
            <Tooltip contentStyle={{ backgroundColor: 'var(--card-bg)', borderColor: 'var(--card-border)', color: 'var(--text-main)', ...chartSpec.tooltipStyle }} />
            {renderLegend()}
            {seriesToRender.map((s, idx) => (
              <Line key={s.dataKey} type="monotone" dataKey={s.dataKey} name={typeof s.name === 'object' ? s.name.text : (s.name || s.dataKey)} stroke={s.color || COLORS[idx % COLORS.length]} strokeWidth={3} activeDot={{ r: 8 }}>
                <LabelList dataKey={s.dataKey} position="top" offset={10} fill="var(--text-secondary)" fontSize={12} />
              </Line>
            ))}
          </LineChart>
        </ResponsiveContainer>
      );
    case 'pie':
    case 'donut':
      return (
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Tooltip contentStyle={{ backgroundColor: 'var(--card-bg)', borderColor: 'var(--card-border)', color: 'var(--text-main)', ...chartSpec.tooltipStyle }} />
            {renderLegend()}
            <Pie
              data={chartSpec.data}
              cx="50%"
              cy="50%"
              innerRadius={chartSpec.chartType === 'donut' ? '50%' : 0}
              outerRadius="80%"
              paddingAngle={chartSpec.chartType === 'donut' ? 5 : 0}
              dataKey="value"
              nameKey="label"
              label={({ name, percent, value }) => `${name}: ${value} (${((percent || 0) * 100).toFixed(0)}%)`}
            >
              {chartSpec.data.map((entry, index) => (
                <Cell key={`cell-${index}`} fill={entry.color || COLORS[index % COLORS.length]} />
              ))}
            </Pie>
          </PieChart>
        </ResponsiveContainer>
      );
    case 'area':
      return (
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={chartSpec.data} margin={{ top: 20, right: 30, left: 0, bottom: 5 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--card-border)" />
            <XAxis dataKey="label" stroke="var(--text-secondary)" interval={0} tick={renderCustomTick(true, chartSpec.xAxisStyle as any)} />
            <YAxis stroke="var(--text-secondary)" interval={0} tick={renderCustomTick(false, chartSpec.yAxisStyle as any)} />
            <Tooltip contentStyle={{ backgroundColor: 'var(--card-bg)', borderColor: 'var(--card-border)', color: 'var(--text-main)', ...chartSpec.tooltipStyle }} />
            {renderLegend()}
            {seriesToRender.map((s, idx) => (
              <Area key={s.dataKey} type="monotone" dataKey={s.dataKey} name={typeof s.name === 'object' ? s.name.text : (s.name || s.dataKey)} stroke={s.color || COLORS[idx % COLORS.length]} fill={s.color || COLORS[idx % COLORS.length]} fillOpacity={0.3}>
                <LabelList dataKey={s.dataKey} position="top" offset={10} fill="var(--text-secondary)" fontSize={12} />
              </Area>
            ))}
          </AreaChart>
        </ResponsiveContainer>
      );
    case 'radar':
      return (
        <ResponsiveContainer width="100%" height="100%">
          <RadarChart cx="50%" cy="50%" outerRadius="80%" data={chartSpec.data}>
            <PolarGrid stroke="var(--card-border)" />
            <PolarAngleAxis dataKey="label" tick={{ fill: 'var(--text-secondary)', fontSize: 11 }} />
            <PolarRadiusAxis angle={30} domain={[0, 100]} tick={false} axisLine={false} />
            <Tooltip contentStyle={{ backgroundColor: 'var(--card-bg)', borderColor: 'var(--card-border)', color: 'var(--text-main)', ...chartSpec.tooltipStyle }} />
            {renderLegend()}
            {seriesToRender.map((s, idx) => (
              <Radar key={s.dataKey} dataKey={s.dataKey} name={typeof s.name === 'object' ? s.name.text : (s.name || s.dataKey)} stroke={s.color || COLORS[idx % COLORS.length]} fill={s.color || COLORS[idx % COLORS.length]} fillOpacity={0.6} label={{ fill: 'var(--text-secondary)', fontSize: 12 }} />
            ))}
          </RadarChart>
        </ResponsiveContainer>
      );
    case 'composed':
      return (
        <ResponsiveContainer width="100%" height="100%">
          <ComposedChart data={chartSpec.data} margin={{ top: 20, right: 30, left: 0, bottom: 5 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--card-border)" />
            <XAxis dataKey="label" stroke="var(--text-secondary)" interval={0} tick={renderCustomTick(true, chartSpec.xAxisStyle as any)} />
            <YAxis stroke="var(--text-secondary)" interval={0} tick={renderCustomTick(false, chartSpec.yAxisStyle as any)} />
            <Tooltip contentStyle={{ backgroundColor: 'var(--card-bg)', borderColor: 'var(--card-border)', color: 'var(--text-main)', ...chartSpec.tooltipStyle }} />
            {renderLegend()}
            {seriesToRender.map((s, idx) => {
              const color = s.color || COLORS[idx % COLORS.length];
              if (s.type === 'line') {
                return <Line key={s.dataKey} type="monotone" dataKey={s.dataKey} name={typeof s.name === 'object' ? s.name.text : (s.name || s.dataKey)} stroke={color} strokeWidth={3} activeDot={{ r: 8 }}><LabelList dataKey={s.dataKey} position="top" offset={10} fill={color} style={{ textShadow: '1px 1px 0px var(--card-bg), -1px -1px 0px var(--card-bg), 1px -1px 0px var(--card-bg), -1px 1px 0px var(--card-bg)' }} fontSize={12} formatter={formatLabel} /></Line>;
              } else if (s.type === 'area') {
                return <Area key={s.dataKey} type="monotone" dataKey={s.dataKey} name={typeof s.name === 'object' ? s.name.text : (s.name || s.dataKey)} stroke={color} fill={color} fillOpacity={0.3}><LabelList dataKey={s.dataKey} position="top" offset={10} fill="var(--text-secondary)" fontSize={12} formatter={formatLabel} /></Area>;
              }
              return <Bar key={s.dataKey} dataKey={s.dataKey} name={typeof s.name === 'object' ? s.name.text : (s.name || s.dataKey)} fill={color} radius={[4, 4, 0, 0]}><LabelList dataKey={s.dataKey} position="top" offset={10} fill="var(--text-secondary)" fontSize={12} formatter={formatLabel} /></Bar>;
            })}
          </ComposedChart>
        </ResponsiveContainer>
      );
    case 'funnel':
      return (
        <ResponsiveContainer width="100%" height="100%">
          <FunnelChart>
            <Tooltip contentStyle={{ backgroundColor: 'var(--card-bg)', borderColor: 'var(--card-border)', color: 'var(--text-main)', ...chartSpec.tooltipStyle }} />
            {renderLegend()}
            <Funnel
              dataKey="value"
              data={mappedData}
              isAnimationActive
            >
              <LabelList position="right" offset={10} fill="var(--text-secondary)" stroke="none" dataKey="labelWithValue" fontSize={12} />
              {mappedData.map((entry, index) => (
                <Cell key={`cell-${index}`} fill={entry.color || COLORS[index % COLORS.length]} />
              ))}
            </Funnel>
          </FunnelChart>
        </ResponsiveContainer>
      );
    case 'scatter':
      return (
        <ResponsiveContainer width="100%" height="100%">
          <ScatterChart margin={{ top: 40, right: 20, bottom: 20, left: 0 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--card-border)" />
            <XAxis type="number" dataKey="x" name={chartSpec.xAxisLabel || 'X'} stroke="var(--text-secondary)" interval={0} tick={renderCustomTick(true, chartSpec.xAxisStyle as any)} />
            <YAxis type="number" dataKey="y" name={chartSpec.yAxisLabel || 'Y'} stroke="var(--text-secondary)" interval={0} tick={renderCustomTick(false, chartSpec.yAxisStyle as any)} />
            <ZAxis type="number" dataKey="z" range={[60, 400]} />
            <Tooltip cursor={{ strokeDasharray: '3 3' }} contentStyle={{ backgroundColor: 'var(--card-bg)', borderColor: 'var(--card-border)', color: 'var(--text-main)', ...chartSpec.tooltipStyle }} />
            {renderLegend()}
            <Scatter name="Data" data={mappedData} fill={chartSpec.primaryColor || COLORS[0]}>
              <LabelList dataKey="xyLabel" position="top" offset={10} fill="var(--text-secondary)" fontSize={12} />
              {mappedData.map((entry, index) => (
                <Cell key={`cell-${index}`} fill={entry.color || COLORS[index % COLORS.length]} />
              ))}
            </Scatter>
          </ScatterChart>
        </ResponsiveContainer>
      );
    case 'radial_bar':
      return (
        <ResponsiveContainer width="100%" height="100%">
          <RadialBarChart cx="50%" cy="50%" innerRadius="10%" outerRadius="100%" data={mappedData}>
            <Tooltip contentStyle={{ backgroundColor: 'var(--card-bg)', borderColor: 'var(--card-border)', color: 'var(--text-main)', ...chartSpec.tooltipStyle }} />
            {renderLegend()}
            <RadialBar
              label={{ position: 'insideStart', fill: '#fff', fontSize: 12, fontWeight: 'bold' }}
              background
              dataKey="value"
            >
              {chartSpec.data.map((entry, index) => (
                <Cell key={`cell-${index}`} fill={entry.color || COLORS[index % COLORS.length]} />
              ))}
            </RadialBar>
          </RadialBarChart>
        </ResponsiveContainer>
      );
    case 'treemap': {
      const renderTreemapContent = (props: any) => {
        const { x, y, width, height, index, payload, name, value } = props;
        const rectColor = payload?.color || COLORS[index % COLORS.length] || chartSpec.primaryColor;
        const displayValue = value !== undefined ? value : payload?.value;
        return (
          <g>
            <rect
              x={x}
              y={y}
              width={width}
              height={height}
              style={{
                fill: rectColor,
                stroke: '#fff',
                strokeWidth: 2,
              }}
            />
            {width > 50 && height > 30 && (
              <text x={x + width / 2} y={y + height / 2} textAnchor="middle" fill="#fff" fontSize={14} fontWeight="bold">
                {name || payload?.name || payload?.label}
              </text>
            )}
            {width > 50 && height > 45 && displayValue !== undefined && (
              <text x={x + width / 2} y={y + height / 2 + 18} textAnchor="middle" fill="#fff" fontSize={12} opacity={0.8}>
                {displayValue.toLocaleString()}
              </text>
            )}
          </g>
        );
      };

      return (
        <ResponsiveContainer width="100%" height="100%">
          <Treemap
            data={chartSpec.data}
            dataKey="value"
            aspectRatio={4 / 3}
            stroke="#fff"
            content={renderTreemapContent}
          >
            <Tooltip contentStyle={{ backgroundColor: 'var(--card-bg)', borderColor: 'var(--card-border)', color: 'var(--text-main)', ...chartSpec.tooltipStyle }} />
          </Treemap>
        </ResponsiveContainer>
      );
    }
    default:
      return <div>Unsupported chart type: {chartSpec.chartType}</div>;
  }
};

export const ChartFocusSlide: React.FC<Props> = ({ content }) => {
  const { chartSpec } = content;

  return (
    <div style={{ containerType: 'inline-size', padding: 'clamp(1rem, 5cqi, 3rem) clamp(1rem, 6cqi, 4rem)', height: '100%', display: 'flex', flexDirection: 'column', boxSizing: 'border-box' }}>
      {renderText(content.title, { marginBottom: 'clamp(1rem, 2cqi, 2rem)', color: 'var(--text-main)', fontSize: 'clamp(1.5rem, 5cqi, 2.25rem)' }, 'h1')}
      {renderText(content.summary, { fontSize: 'clamp(1rem, 3cqi, 1.5rem)', color: 'var(--text-secondary)', lineHeight: 1.6, marginBottom: 'clamp(1rem, 3cqi, 2rem)' }, 'p')}
      <div style={{ flex: 1, minHeight: 0, minWidth: 0, width: '100%', background: 'var(--card-bg)', padding: 'clamp(0.5rem, 2cqmin, 1rem)', borderRadius: '12px', border: '1px solid var(--card-border)' }}>
        <StandaloneChart chartSpec={chartSpec} />
      </div>
    </div>
  );
};
