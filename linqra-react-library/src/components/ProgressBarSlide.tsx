import React from 'react';
import type { ProgressBarSlideContent } from '../schemas';
import { renderText } from '../utils';

interface Props {
  content: ProgressBarSlideContent;
}

export const ProgressBarSlide: React.FC<Props> = ({ content }) => {
  const progress = Math.min(100, Math.max(0, content.progress || 0));
  const isRadial = content.type === 'radial';
  const color = content.color || '#2563eb';
  const thickness = content.thickness || (isRadial ? '8px' : '6px');

  if (isRadial) {
    const viewBoxSize = 100;
    const strokeWidth = parseInt(thickness, 10) || 10;
    const radius = (viewBoxSize - strokeWidth) / 2;
    const circumference = radius * 2 * Math.PI;
    const offset = circumference - (progress / 100) * circumference;

    return (
      <div style={{
        display: 'inline-flex',
        flexDirection: 'column',
        alignItems: 'center',
        gap: '0.8cqi',
        ...(content.containerStyle || {})
      }}>
        <div style={{ position: 'relative', width: '100%', maxWidth: '10cqi', aspectRatio: '1/1' }}>
          <svg width="100%" height="100%" viewBox={`0 0 ${viewBoxSize} ${viewBoxSize}`}>
            <circle
              stroke="#e2e8f0"
              fill="transparent"
              strokeWidth={strokeWidth}
              r={radius}
              cx={viewBoxSize / 2}
              cy={viewBoxSize / 2}
            />
            <circle
              stroke={color}
              fill="transparent"
              strokeWidth={strokeWidth}
              strokeDasharray={`${circumference} ${circumference}`}
              style={{ strokeDashoffset: offset, transition: 'stroke-dashoffset 0.5s ease' }}
              strokeLinecap="round"
              r={radius}
              cx={viewBoxSize / 2}
              cy={viewBoxSize / 2}
            />
          </svg>
          <div style={{
            position: 'absolute',
            top: 0, left: 0, right: 0, bottom: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: '1.5cqi',
            fontWeight: 'bold',
            color: '#0f172a'
          }}>
            {Math.round(progress)}%
          </div>
        </div>
        {content.label && renderText(content.label, { fontSize: '0.8cqi', color: '#64748b', fontWeight: 500 }, 'div')}
      </div>
    );
  }

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: '0.5cqi',
      width: '100%',
      ...(content.containerStyle || {})
    }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        {content.label && renderText(content.label, { fontSize: '0.85cqi', fontWeight: 600, color: '#0f172a' }, 'div')}
        <span style={{ fontSize: '1.2cqi', color: '#64748b', fontWeight: 600 }}>{Math.round(progress)}%</span>
      </div>
      <div style={{
        width: '100%',
        height: thickness,
        background: '#e2e8f0',
        borderRadius: '999px',
        overflow: 'hidden'
      }}>
        <div style={{
          height: '100%',
          width: `${progress}%`,
          background: color,
          transition: 'width 0.5s ease',
          borderRadius: '999px'
        }} />
      </div>
    </div>
  );
};
