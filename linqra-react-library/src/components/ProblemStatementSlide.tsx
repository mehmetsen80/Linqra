import React from 'react';
import type { ProblemStatementSlideContent } from '../schemas';
import { renderText } from '../utils';

interface ProblemStatementSlideProps {
  content: ProblemStatementSlideContent;
}

export const ProblemStatementSlide: React.FC<ProblemStatementSlideProps> = ({ content }) => {
  return (
    <div style={{
      containerType: 'inline-size',
      padding: 'var(--slide-padding, 4cqi)',
      height: '100%',
      display: 'flex',
      flexDirection: 'column',
      justifyContent: 'center',
      alignItems: 'center',
      position: 'relative',
      textAlign: 'center'
    }}>
      {/* Background Elements */}
      {content.backgroundElements?.map((style, idx) => (
        <div key={`bg-${idx}`} style={{ position: 'absolute', ...style }} />
      ))}

      <div style={{ 
        maxWidth: '85%', 
        position: 'relative', 
        zIndex: 2,
        display: 'flex',
        flexDirection: 'column',
        gap: '4cqi',
        alignItems: 'center'
      }}>
        {/* Massive Statement */}
        {renderText(content.statement, { 
          fontSize: '5cqi', 
          fontWeight: '900', 
          lineHeight: 1.1,
          color: 'var(--title-color, var(--text-main))',
          letterSpacing: '-0.05cqi',
          margin: 0
        }, 'h1')}

        {/* Secondary Metrics */}
        {content.metrics && content.metrics.length > 0 && (
          <div style={{
            display: 'flex',
            gap: '4cqi',
            flexWrap: 'wrap',
            justifyContent: 'center',
            marginTop: '2cqi'
          }}>
            {content.metrics.map((metric, idx) => (
              <div key={`metric-${idx}`} style={{
                display: 'flex',
                flexDirection: 'column',
                gap: '0.5cqi',
                alignItems: 'center'
              }}>
                {renderText(metric.value, {
                  fontSize: '3cqi',
                  fontWeight: '800',
                  color: 'var(--text-main)',
                  margin: 0
                }, 'div')}
                {renderText(metric.label, {
                  fontSize: '1.2cqi',
                  color: 'var(--text-secondary)',
                  fontWeight: '600',
                  textTransform: 'uppercase',
                  letterSpacing: '0.1cqi',
                  margin: 0
                }, 'div')}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};
