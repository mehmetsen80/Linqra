import React from 'react';
import type { AlternatingFlowSlideContent } from '../schemas';
import { DynamicIcon } from './DynamicIcon';
import { renderText } from '../utils';

export interface AlternatingFlowSlideProps {
  content: AlternatingFlowSlideContent;
}

export const AlternatingFlowSlide: React.FC<AlternatingFlowSlideProps> = ({ content }) => {
  const steps = content.steps || [];
  
  if (steps.length === 0) return null;

  return (
    <div style={{ 
      width: '100%', 
      height: '100%', 
      display: 'flex', 
      flexDirection: 'column', 
      padding: '2rem'
    }}>
      {/* Slide Header */}
      <div style={{ marginBottom: '2rem' }}>
        {renderText(content.title, { 
            fontSize: '2.5rem', 
            fontWeight: 700, 
            margin: 0, 
            color: '#1e293b' 
          }, 'h2')}
        {renderText(content.subtitle, { 
            fontSize: '1.25rem', 
            margin: '0.5rem 0 0 0', 
            color: '#64748b' 
          }, 'p')}
      </div>

      {/* Main Alternating Grid */}
      <div style={{ 
        flex: 1, 
        position: 'relative', 
        display: 'flex', 
        alignItems: 'center', 
        justifyContent: 'space-around',
        padding: '2rem 0'
      }}>
        
        {/* SVG Connectors Background */}
        <div style={{ position: 'absolute', top: 0, left: 0, width: '100%', height: '100%', zIndex: 0, pointerEvents: 'none' }}>
          <svg width="100%" height="100%" viewBox="0 0 100 100" preserveAspectRatio="none" style={{ overflow: 'visible' }}>
            <defs>
              {steps.map((step, i) => {
                if (i === steps.length - 1) return null;
                const nextStep = steps[i + 1];
                const fromColor = step.color || '#cbd5e1';
                const toColor = nextStep.color || '#cbd5e1';
                return (
                  <linearGradient key={`grad-${i}`} id={`alt-grad-${i}`} x1="0%" y1="0%" x2="100%" y2={i % 2 === 0 ? "100%" : "-100%"}>
                    <stop offset="0%" stopColor={fromColor} />
                    <stop offset="100%" stopColor={toColor} />
                  </linearGradient>
                );
              })}
            </defs>
            {steps.map((_, i) => {
              if (i === steps.length - 1) return null;
              
              // Calculate exact percentage-based coordinates for the SVG path
              // The nodes are spaced using 'space-around', so the centers are at:
              // center_x = (i + 0.5) / n * 100%
              const n = steps.length;
              const startX = ((i + 0.5) / n) * 100;
              const endX = ((i + 1.5) / n) * 100;
              
              const isStartTop = (i % 2 === 0);
              // Top nodes are vertically at ~25% height (flex-start in a 50% split)
              // Bottom nodes are vertically at ~75% height (flex-end)
              const startY = isStartTop ? 25 : 75;
              const endY = isStartTop ? 75 : 25;
              
              const midX = (startX + endX) / 2;

              return (
                <path
                  key={`path-${i}`}
                  d={`M ${startX} ${startY} C ${midX} ${startY}, ${midX} ${endY}, ${endX} ${endY}`}
                  fill="none"
                  stroke={`url(#alt-grad-${i})`}
                  strokeWidth="8"
                  strokeLinecap="round"
                  style={{ vectorEffect: 'non-scaling-stroke' }}
                />
              );
            })}
          </svg>
        </div>

        {/* The Nodes */}
        {steps.map((step, i) => {
          const isTop = i % 2 === 0;
          const nodeColor = step.color || '#cbd5e1';

          return (
            <div key={i} style={{
              position: 'relative',
              zIndex: 1,
              width: `${100 / steps.length}%`,
              height: '100%',
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: isTop ? 'flex-start' : 'flex-end',
            }}>
              <div style={{
                display: 'flex',
                flexDirection: isTop ? 'column' : 'column-reverse',
                alignItems: 'center',
                height: '75%', // Leaves 25% empty space on the opposite side
                gap: '1rem',
              }}>
                {/* Node Icon/Circle */}
                <div style={{
                  width: '80px',
                  height: '80px',
                  borderRadius: '50%',
                  backgroundColor: '#ffffff',
                  border: `4px solid ${nodeColor}`,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  boxShadow: '0 4px 12px rgba(0,0,0,0.1)',
                  flexShrink: 0,
                  ...step.nodeStyle
                }}>
                  {step.icon ? (
                    <DynamicIcon name={step.icon} size={32} color={nodeColor} />
                  ) : (
                    <span style={{ fontSize: '1.5rem', fontWeight: 700, color: nodeColor }}>{i + 1}</span>
                  )}
                </div>

                {/* Text Content */}
                <div style={{
                  textAlign: 'center',
                  padding: '0 1rem',
                }}>
                  {renderText(step.title, { margin: '0 0 0.5rem 0', color: nodeColor, fontSize: '1.25rem' }, 'h3')}
                  {renderText(step.description, { margin: 0, color: '#64748b', fontSize: '0.9rem', lineHeight: 1.4 }, 'p')}
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
