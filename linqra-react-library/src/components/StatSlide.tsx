import React from 'react';
import type { StatSlideContent } from '../schemas';
import { renderText } from '../utils';
import { DynamicIcon } from './DynamicIcon';

interface Props {
  content: StatSlideContent;
}

export const StatSlide: React.FC<Props> = ({ content }) => {
  return (
    <div style={{
      display: 'inline-flex',
      flexDirection: 'column',
      gap: '0.2cqi',
      ...(content.containerStyle || {})
    }}>
      {content.label && renderText(content.label, { fontSize: '0.85cqi', color: '#64748b', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em' }, 'div')}
      
      <div style={{ display: 'flex', alignItems: 'baseline', gap: '0.5cqi' }}>
        {renderText(content.value, { fontSize: '2.5cqi', fontWeight: 800, color: '#0f172a', lineHeight: 1 }, 'div')}
        
        {content.trend && (
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: '0.2cqi',
            padding: '0.2cqi 0.4cqi',
            borderRadius: '4px',
            background: content.trend.direction === 'up' ? '#dcfce7' : content.trend.direction === 'down' ? '#fee2e2' : '#f1f5f9',
            color: content.trend.direction === 'up' ? '#16a34a' : content.trend.direction === 'down' ? '#dc2626' : '#64748b',
            fontSize: '0.75cqi',
            fontWeight: 700
          }}>
            <DynamicIcon 
              name={content.trend.direction === 'up' ? 'TrendingUp' : content.trend.direction === 'down' ? 'TrendingDown' : 'Minus'} 
              size="1em" 
            />
            {renderText(content.trend.value, {}, 'span')}
          </div>
        )}
      </div>
    </div>
  );
};
