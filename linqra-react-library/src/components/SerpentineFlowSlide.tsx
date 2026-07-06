import React, { useState, useEffect, useRef } from 'react';
import type { SerpentineFlowSlideContent } from '../schemas';
import { DynamicIcon } from './DynamicIcon';
import { renderText } from '../utils';

export interface SerpentineFlowSlideProps {
  content: SerpentineFlowSlideContent;
}

export const SerpentineFlowSlide: React.FC<SerpentineFlowSlideProps> = ({ content }) => {
  const steps = content.steps || [];
  
  const containerRef = useRef<HTMLDivElement>(null);
  const [containerWidth, setContainerWidth] = useState(0);

  useEffect(() => {
    if (!containerRef.current) return;
    const observer = new ResizeObserver((entries) => {
      setContainerWidth(entries[0].contentRect.width);
    });
    observer.observe(containerRef.current);
    return () => observer.disconnect();
  }, []);

  if (steps.length === 0) return null;

  // Dynamic columns calculation to prevent squishing
  const MIN_NODE_WIDTH = 250;
  const calculatedC = containerWidth > 0 ? Math.max(1, Math.floor(containerWidth / MIN_NODE_WIDTH)) : 4;
  const C = content.itemsPerRow || calculatedC; // Columns
  const R = Math.ceil(steps.length / C); // Rows

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
        {renderText(content.title, { fontSize: '2.5rem', fontWeight: 700, margin: 0, color: '#1e293b' }, 'h2')}
        {renderText(content.subtitle, { fontSize: '1.25rem', margin: '0.5rem 0 0 0', color: '#64748b' }, 'p')}
      </div>

      {/* Main Serpentine Grid Container */}
      <div ref={containerRef} style={{ 
        flex: 1, 
        position: 'relative', 
        display: 'flex', 
        flexDirection: 'column',
        marginRight: '8%', // Shove the entire graph to the left to balance the visual weight of the right-side U-turn
        minHeight: 0 // Prevent flex overflow
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
                  <linearGradient key={`grad-${i}`} id={`serp-grad-${i}`} x1="0%" y1="0%" x2="100%" y2="0%">
                    <stop offset="0%" stopColor={fromColor} />
                    <stop offset="100%" stopColor={toColor} />
                  </linearGradient>
                );
              })}
            </defs>
            {steps.map((_, i) => {
              if (i === steps.length - 1) return null;
              
              const row1 = Math.floor(i / C);
              const col1 = i % C;
              const isLtr1 = (row1 % 2 === 0);
              const visCol1 = isLtr1 ? col1 : (C - 1 - col1);
              
              const row2 = Math.floor((i + 1) / C);
              const col2 = (i + 1) % C;
              const isLtr2 = (row2 % 2 === 0);
              const visCol2 = isLtr2 ? col2 : (C - 1 - col2);
              
              // Apply symmetric math: rows have 8% padding on left/right, meaning content spans 84%
              const x1 = 8 + (((visCol1 + 0.5) / C) * 84);
              const y1 = ((row1 + 0.5) / R) * 100;
              const x2 = 8 + (((visCol2 + 0.5) / C) * 84);
              const y2 = ((row2 + 0.5) / R) * 100 + (row1 === row2 ? 0.01 : 0);

              if (row1 === row2) {
                return (
                  <path
                    key={`path-${i}`}
                    d={`M ${x1} ${y1} L ${x2} ${y2}`}
                    fill="none"
                    stroke={`url(#serp-grad-${i})`}
                    strokeWidth="8"
                    strokeLinecap="round"
                    style={{ vectorEffect: 'non-scaling-stroke' }}
                  />
                );
              } else {
                const curveExtent = isLtr1 ? 100 : 0; // Exactly touches the absolute edge of the container
                return (
                  <path
                    key={`path-${i}`}
                    d={`M ${x1} ${y1} C ${curveExtent} ${y1}, ${curveExtent} ${y2}, ${x2} ${y2}`}
                    fill="none"
                    stroke={`url(#serp-grad-${i})`}
                    strokeWidth="8"
                    strokeLinecap="round"
                    style={{ vectorEffect: 'non-scaling-stroke' }}
                  />
                );
              }
            })}
          </svg>
        </div>

        {/* The Rows and Nodes */}
        {Array.from({ length: R }).map((_, r) => {
          const isLtr = r % 2 === 0;
          const rowItems = steps.slice(r * C, (r + 1) * C);
          
          return (
            <div key={`row-${r}`} style={{
              display: 'flex',
              flexDirection: isLtr ? 'row' : 'row-reverse',
              alignItems: 'stretch', // CRITICAL FIX: Fill the row height so grid 1fr perfectly matches mathematical bounds
              width: '100%',
              height: `${100 / R}%`,
              padding: '0 8%', // Perfectly squeeze the nodes inwards to leave space for the U-turn curves
              boxSizing: 'border-box'
            }}>
              {rowItems.map((step, localIndex) => {
                const nodeColor = step.color || '#cbd5e1';
                return (
                  <div key={localIndex} style={{
                    width: `${100 / C}%`,
                    height: '100%',
                    display: 'grid',
                    // CRITICAL FIX: use minmax(0, 1fr) to prevent the bottom track's text height from warping the grid's equal distribution!
                    gridTemplateRows: 'minmax(0, 1fr) auto minmax(0, 1fr)', 
                    alignItems: 'center',
                    justifyItems: 'center',
                    position: 'relative',
                    zIndex: 1,
                  }}>
                    {/* Top spacer (1fr) */}
                    <div />
                    
                    {/* Middle: Node Icon/Circle (auto) */}
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
                        <span style={{ fontSize: '1.5rem', fontWeight: 700, color: nodeColor }}>{r * C + localIndex + 1}</span>
                      )}
                    </div>
                    
                    {/* Bottom: Text Content (1fr) */}
                    <div style={{ 
                      textAlign: 'center', 
                      padding: '0.5rem 1rem 1rem', // Reduced top padding to move text closer to the node
                      alignSelf: 'start' // Align to the top of its 1fr track
                    }}>
                      {renderText(step.title, { margin: '0 0 0.25rem 0', color: nodeColor, fontSize: '1.25rem' }, 'h3')}
                      {renderText(step.description, { margin: 0, color: '#64748b', fontSize: '0.9rem', lineHeight: 1.4 }, 'p')}
                    </div>
                  </div>
                );
              })}
            </div>
          );
        })}
      </div>
    </div>
  );
};
