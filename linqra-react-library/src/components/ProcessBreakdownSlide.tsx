import React from 'react';
import type { ProcessBreakdownSlideContent } from '../schemas';
import { renderText } from '../utils';
import { DynamicIcon } from './DynamicIcon';

interface ProcessBreakdownSlideProps {
  content: ProcessBreakdownSlideContent;
}

export const ProcessBreakdownSlide: React.FC<ProcessBreakdownSlideProps> = ({ content }) => {
  return (
    <div style={{
      containerType: 'inline-size',
      padding: 'var(--slide-padding, 4cqi)',
      height: '100%',
      display: 'flex',
      flexDirection: 'column',
      gap: 'var(--content-gap, 3cqi)',
      position: 'relative'
    }}>
      {/* Header */}
      <div style={{ position: 'relative', zIndex: 2, textAlign: 'var(--title-align, left)' as any }}>
        {renderText(content.title, { 
          fontSize: 'var(--title-font-size, 3.5cqi)', 
          fontWeight: '800', 
          margin: '0 0 1cqi 0', 
          color: 'var(--title-color, var(--text-main))',
          letterSpacing: '-0.02cqi' 
        }, 'h1')}
        {content.subtitle && renderText(content.subtitle, { 
          fontSize: '1.8cqi', 
          color: 'var(--subtitle-color, var(--text-secondary))', 
          margin: 0 
        }, 'div')}
      </div>

      {/* Process Flow */}
      <div style={{
        flex: 1,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        position: 'relative',
        zIndex: 2,
        padding: '2cqi 0'
      }}>
        {/* Connecting Line */}
        <div style={{
          position: 'absolute',
          top: '50%',
          left: '10%',
          right: '10%',
          height: '4px',
          background: 'var(--card-border, #e2e8f0)',
          transform: 'translateY(-50%)',
          zIndex: 0
        }} />

        <div style={{
          display: 'flex',
          justifyContent: 'space-between',
          width: '100%',
          position: 'relative',
          zIndex: 1
        }}>
          {content.steps.map((step, idx) => {
            const isBottleneck = step.isBottleneck;
            
            return (
              <div key={`step-${idx}`} style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                gap: '1.5cqi',
                width: `${100 / content.steps.length}%`,
                position: 'relative'
              }}>
                {/* Step Node */}
                <div style={{
                  width: '6cqi',
                  height: '6cqi',
                  borderRadius: '50%',
                  background: isBottleneck ? 'var(--accent-bg, #fef2f2)' : 'var(--bg-main, white)',
                  border: `3px solid ${isBottleneck ? 'var(--accent-color, #ef4444)' : 'var(--text-main)'}`,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color: isBottleneck ? 'var(--accent-color, #ef4444)' : 'var(--text-main)',
                  boxShadow: '0 4px 10px rgba(0,0,0,0.1)',
                  zIndex: 2
                }}>
                  {step.icon ? (
                    <DynamicIcon name={step.icon} />
                  ) : (
                    <span style={{ fontSize: '2cqi', fontWeight: '800' }}>{idx + 1}</span>
                  )}
                </div>

                {/* Step Content */}
                <div style={{
                  textAlign: 'center',
                  background: isBottleneck ? 'var(--accent-bg, #fef2f2)' : 'transparent',
                  padding: isBottleneck ? '1cqi' : '0',
                  borderRadius: '1cqi',
                  border: isBottleneck ? '1px solid var(--accent-border, #fecaca)' : 'none',
                }}>
                  {renderText(step.label, {
                    fontSize: '2.4cqi',
                    fontWeight: '800',
                    color: isBottleneck ? 'var(--accent-color, #ef4444)' : 'var(--text-main)',
                    margin: '0 0 0.8cqi 0'
                  }, 'h3')}
                  
                  {step.description && renderText(step.description, {
                    fontSize: '1.6cqi',
                    color: isBottleneck ? 'var(--accent-color, #b91c1c)' : 'var(--text-secondary)',
                    margin: 0,
                    lineHeight: 1.4
                  }, 'p')}
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
};
