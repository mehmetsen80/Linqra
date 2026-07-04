import React from 'react';
import type { MetricGridSlideContent } from '../schemas';
import { DynamicIcon } from './DynamicIcon';
import { renderImage, renderText } from '../utils';

interface Props {
  content: MetricGridSlideContent;
}

export const MetricGridSlide: React.FC<Props> = ({ content }) => {
  return (
    <div style={{ containerType: 'size', padding: 'clamp(1rem, 5cqmin, 4rem)', height: '100%', display: 'flex', flexDirection: 'column', boxSizing: 'border-box', fontFamily: 'var(--font-family)', letterSpacing: 'var(--letter-spacing)' }}>
      {renderText(content.title, { marginBottom: '0.5rem', color: 'var(--text-main)', fontSize: 'min(var(--title-font-size, clamp(1.5rem, 6cqmin, 3rem)), 8cqmin)', fontWeight: 'var(--title-font-weight)', textAlign: 'var(--title-align)' as any }, 'h1')}
      {renderImage(content.image)}
      {renderText(content.subtitle, { color: 'var(--text-secondary)', marginBottom: '1rem', fontSize: 'min(var(--body-font-size, clamp(1rem, 3cqmin, 1.5rem)), 4cqmin)', textAlign: 'var(--body-align)' as any }, 'p')}
      
      <div style={{ flex: 1, minHeight: 0, display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(clamp(100px, 30cqmin, 200px), 1fr))', gap: 'var(--card-gap)', overflowY: 'hidden' }}>
        {content.metrics.map((metric, i) => (
          <div key={i} style={{ border: '1px solid var(--card-border)', borderRadius: 'var(--card-radius)', padding: 'var(--card-padding)', background: 'var(--card-bg)', position: 'relative', display: 'flex', flexDirection: 'column', justifyContent: 'center', ...metric.cardStyle }}>
            <div style={{ position: 'absolute', top: 'clamp(0.5rem, 3cqmin, 1.5rem)', right: 'clamp(0.5rem, 3cqmin, 1.5rem)', color: 'var(--text-tertiary)', opacity: 0.5 }}>
              <DynamicIcon name={metric.icon} size={32} />
            </div>
            {renderText(metric.label, { margin: 0, fontSize: 'clamp(0.75rem, 2cqmin, 1.1rem)', color: 'var(--text-secondary)' }, 'h3')}
            {renderText(metric.value, { fontSize: 'clamp(1.2rem, 5cqmin, 2.5rem)', fontWeight: 'bold', margin: 'clamp(0.2rem, 1cqmin, 0.5rem) 0', color: 'var(--text-main)' }, 'div')}
            {renderText(metric.trend, { fontSize: 'clamp(0.7rem, 1.5cqmin, 0.875rem)', color: (typeof metric.trend === 'string' && metric.trend.startsWith('+')) || (typeof metric.trend === 'object' && metric.trend.text.startsWith('+')) ? '#22c55e' : 'inherit' }, 'div')}
          </div>
        ))}
      </div>
    </div>
  );
};
