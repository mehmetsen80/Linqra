import React from 'react';
import type { FocalMetricSlideContent } from '../schemas';

interface Props {
  content: FocalMetricSlideContent;
}

export const FocalMetricSlide: React.FC<Props> = ({ content }) => {
  const { title, subtitle, focalMetric, description, secondaryMetrics, variant = 'centered' } = content;

  const renderText = (textObj: any, defaultStyle: React.CSSProperties = {}, Tag: any = 'span') => {
    if (!textObj) return null;
    if (typeof textObj === 'string') return <Tag style={defaultStyle}>{textObj}</Tag>;
    return <Tag style={{ ...defaultStyle, ...(textObj.style || {}) }}>{textObj.text}</Tag>;
  };

  // Helper for rendering the main metric number
  const renderMetricValue = (align: 'center' | 'left' = 'center', extraStyle: React.CSSProperties = {}) => {
    let style: React.CSSProperties = {
      fontSize: 'clamp(5rem, 15cqmin, 9rem)',
      fontWeight: 900,
      lineHeight: 1,
      letterSpacing: '-0.04em',
      margin: 0,
      textAlign: align,
      ...extraStyle
    };
    
    if (variant === 'gradient') {
      style = {
        ...style,
        background: `linear-gradient(135deg, ${focalMetric.color || '#10b981'} 0%, #3b82f6 100%)`,
        WebkitBackgroundClip: 'text',
        WebkitTextFillColor: 'transparent',
        color: 'transparent', // Fallback
      };
    } else {
      style.color = focalMetric.color || '#10b981';
    }

    return renderText(focalMetric.value, style, 'h1');
  };

  // Helper for rendering secondary metrics
  const renderSecondaryMetrics = (align: 'center' | 'left' = 'center') => {
    if (!secondaryMetrics || secondaryMetrics.length === 0) return null;
    return (
      <div style={{
        marginTop: '2rem',
        paddingTop: '2rem',
        borderTop: '2px solid #e2e8f0',
        display: 'flex',
        flexDirection: 'row',
        justifyContent: align === 'center' ? 'center' : 'flex-start',
        gap: '3rem',
        flexWrap: 'wrap'
      }}>
        {secondaryMetrics.map((metric, idx) => (
          <div key={idx} style={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: align === 'center' ? 'center' : 'flex-start',
            gap: '0.25rem'
          }}>
            {renderText(metric.value, {
              fontSize: '2rem',
              fontWeight: 800,
              color: '#1e293b',
              lineHeight: 1,
              margin: 0
            }, 'div')}
            {renderText(metric.label, {
              fontSize: '0.9rem',
              fontWeight: 600,
              color: '#64748b',
              textTransform: 'uppercase',
              letterSpacing: '0.05em',
              margin: 0
            }, 'div')}
          </div>
        ))}
      </div>
    );
  };

  if (variant === 'split') {
    return (
      <div style={{
        width: '100%',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        padding: 'clamp(2rem, 4cqmin, 4rem)',
        boxSizing: 'border-box',
        background: '#ffffff'
      }}>
        {(title || subtitle) && (
          <div style={{ marginBottom: '3rem' }}>
            {renderText(title, {
              fontSize: 'clamp(1.5rem, 4cqmin, 2.5rem)',
              fontWeight: 800,
              color: '#0f172a',
              margin: '0 0 1rem 0',
              lineHeight: 1.2
            }, 'h2')}
            {renderText(subtitle, {
              fontSize: 'clamp(1rem, 2cqmin, 1.25rem)',
              color: '#64748b',
              margin: 0,
              lineHeight: 1.5,
              maxWidth: '800px'
            }, 'p')}
          </div>
        )}
        
        <div style={{
          flex: 1,
          display: 'flex',
          flexDirection: 'row',
          alignItems: 'center',
          gap: '4rem'
        }}>
          {/* Left Side: Giant Metric */}
          <div style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'flex-start' }}>
            {renderMetricValue('left')}
            {renderText(focalMetric.label, {
              fontSize: 'clamp(1.5rem, 4cqmin, 2.5rem)',
              fontWeight: 700,
              color: '#0f172a',
              margin: '0.5rem 0 0 0',
              letterSpacing: '-0.01em'
            }, 'h2')}
          </div>
          
          {/* Right Side: Details */}
          <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '2rem' }}>
            {description && (
              <div style={{
                padding: 'clamp(1rem, 3cqmin, 2rem)',
                background: '#f8fafc',
                borderRadius: '16px',
                border: '1px solid #e2e8f0'
              }}>
                {renderText(description, {
                  fontSize: '1.25rem',
                  lineHeight: 1.6,
                  color: '#334155',
                  margin: 0,
                  fontWeight: 500
                }, 'p')}
              </div>
            )}
            {renderSecondaryMetrics('left')}
          </div>
        </div>
      </div>
    );
  }

  // Centered, Left-aligned, Card, Gradient share a similar vertical stack structure
  const isLeft = variant === 'left-aligned';
  const isCard = variant === 'card';

  return (
    <div style={{
      width: '100%',
      height: '100%',
      display: 'flex',
      flexDirection: 'column',
      padding: 'clamp(2rem, 4cqmin, 4rem)',
      boxSizing: 'border-box',
      background: isCard ? '#f8fafc' : '#ffffff',
      justifyContent: 'center',
      alignItems: isLeft ? 'flex-start' : 'center',
      textAlign: isLeft ? 'left' : 'center'
    }}>
      
      {/* Header Section */}
      {(title || subtitle) && (
        <div style={{ marginBottom: '3rem', alignSelf: isLeft ? 'flex-start' : 'center', textAlign: isLeft ? 'left' : 'center' }}>
          {renderText(title, {
            fontSize: 'clamp(1.5rem, 4cqmin, 2.5rem)',
            fontWeight: 800,
            color: '#0f172a',
            margin: '0 0 1rem 0',
            lineHeight: 1.2
          }, 'h2')}
          {renderText(subtitle, {
            fontSize: 'clamp(1rem, 2cqmin, 1.25rem)',
            color: '#64748b',
            margin: 0,
            lineHeight: 1.5,
            maxWidth: '800px',
            marginLeft: isLeft ? '0' : 'auto',
            marginRight: isLeft ? '0' : 'auto'
          }, 'p')}
        </div>
      )}

      {/* Main Focal Area */}
      <div style={{
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'center',
        alignItems: isLeft ? 'flex-start' : 'center',
        gap: '2rem',
        padding: isCard ? '4rem' : '0',
        background: isCard ? '#ffffff' : 'transparent',
        borderRadius: isCard ? '24px' : '0',
        boxShadow: isCard ? '0 20px 25px -5px rgba(0, 0, 0, 0.05), 0 8px 10px -6px rgba(0, 0, 0, 0.01)' : 'none',
        border: isCard ? '1px solid #e2e8f0' : 'none',
        width: isCard ? '100%' : 'auto',
        maxWidth: isCard ? '900px' : 'none'
      }}>
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: isLeft ? 'flex-start' : 'center',
          gap: '0.5rem'
        }}>
          {renderMetricValue(isLeft ? 'left' : 'center')}
          
          {renderText(focalMetric.label, {
            fontSize: 'clamp(1.5rem, 4cqmin, 2.5rem)',
            fontWeight: 700,
            color: '#0f172a',
            margin: 0,
            letterSpacing: '-0.01em'
          }, 'h2')}
        </div>

        {description && (
          <div style={{
            maxWidth: '700px',
            padding: isCard ? '0' : '2rem',
            background: isCard ? 'transparent' : '#f8fafc',
            borderRadius: isCard ? '0' : '16px',
            border: isCard ? 'none' : '1px solid #e2e8f0'
          }}>
            {renderText(description, {
              fontSize: '1.25rem',
              lineHeight: 1.6,
              color: '#334155',
              margin: 0,
              fontWeight: 500
            }, 'p')}
          </div>
        )}
        
        {isCard && renderSecondaryMetrics(isLeft ? 'left' : 'center')}
      </div>

      {!isCard && renderSecondaryMetrics(isLeft ? 'left' : 'center')}
      
    </div>
  );
};
