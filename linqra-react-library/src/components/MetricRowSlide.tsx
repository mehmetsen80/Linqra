import React from 'react';
import type { MetricRowSlideContent } from '../schemas';
import { DynamicIcon } from './DynamicIcon';

interface Props {
  content: MetricRowSlideContent;
}

export const MetricRowSlide: React.FC<Props> = ({ content }) => {
  const renderText = (textObj: any, defaultStyle: React.CSSProperties = {}, Tag: any = 'div') => {
    if (!textObj) return null;
    if (typeof textObj === 'string') {
      return <Tag style={defaultStyle}>{textObj}</Tag>;
    }
    return <Tag style={{ ...defaultStyle, ...(textObj.style || {}) }}>{textObj.text}</Tag>;
  };

  return (
    <div style={{
      marginTop: '2cqi',
      background: 'white',
      border: '1px solid #e2e8f0',
      borderRadius: '12px',
      padding: '1cqi 1.5cqi',
      display: 'flex',
      justifyContent: 'center',
      gap: '4cqi',
      ...(content.containerStyle || {})
    }}>
      {content.metrics.map((metric, idx) => {
        const isLast = idx === content.metrics.length - 1;
        const color = metric.iconColor || '#ea580c';
        
        return (
          <div key={idx} style={{
            display: 'flex',
            alignItems: 'center',
            gap: '1cqi',
            paddingRight: isLast ? '0' : '4cqi',
            borderRight: isLast ? 'none' : '1px solid #e2e8f0'
          }}>
            {metric.icon && (
              <div style={{
                width: '2.2cqi',
                height: '2.2cqi',
                borderRadius: '50%',
                border: `1px solid ${color}`,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: color
              }}>
                <DynamicIcon name={metric.icon} size="1cqi" color={color} />
              </div>
            )}
            <div style={{ display: 'flex', flexDirection: 'column', ...(content.metricLayoutStyle || {}) }}>
              {content.textLayout === 'title-top' ? (
                <>
                  {renderText(metric.title, {
                    fontSize: '0.9cqi',
                    fontWeight: 800,
                    color: '#0f172a'
                  })}
                  {metric.subtitle && renderText(metric.subtitle, {
                    fontSize: '0.7cqi',
                    fontWeight: 800,
                    color: '#64748b',
                    letterSpacing: '0.05em',
                    textTransform: 'uppercase'
                  })}
                </>
              ) : (
                <>
                  {metric.subtitle && renderText(metric.subtitle, {
                    fontSize: '0.7cqi',
                    fontWeight: 800,
                    color: '#64748b',
                    letterSpacing: '0.05em',
                    textTransform: 'uppercase'
                  })}
                  {renderText(metric.title, {
                    fontSize: '0.9cqi',
                    fontWeight: 800,
                    color: '#0f172a'
                  })}
                </>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
};
