import React, { useEffect, useRef, useState } from 'react';
import type { HexagonTimelineSlideContent } from '../schemas';
import { renderText } from '../utils';
import { DynamicIcon } from './DynamicIcon';

interface Props {
  content: HexagonTimelineSlideContent;
}

export const HexagonTimelineSlide: React.FC<Props> = ({ content }) => {
  const steps = content.steps || [];
  const N = steps.length;
  
  const containerRef = useRef<HTMLDivElement>(null);
  const [dimensions, setDimensions] = useState({ width: 1000, height: 500 });

  useEffect(() => {
    if (!containerRef.current) return;
    const observer = new ResizeObserver(entries => {
      for (let entry of entries) {
        setDimensions({
          width: entry.contentRect.width,
          height: entry.contentRect.height
        });
      }
    });
    observer.observe(containerRef.current);
    return () => observer.disconnect();
  }, []);

  if (N === 0) return null;

  // Responsive font scaling
  const headerFontSize = Math.max(1.5, (dimensions.width / 1200) * 2.5);
  const subFontSize = Math.max(1, (dimensions.width / 1200) * 1.25);

  const StepWidth = Math.min(250, dimensions.width / N);
  const TotalWidth = StepWidth * N;
  const offsetX = (dimensions.width - TotalWidth) / 2;
  const CY = dimensions.height / 2;
  const RIBBON_HEIGHT = 60;
  
  const ARROW_HEAD_WIDTH = 25; // The overlap width of the chevron
  const CHEVRON_GAP = 10; // Space between white and colored chevrons
  const arrowType = content.arrowType || 'solid';

  // Hexagon Math
  const HEX_R = content.nodeSize || 42; // Radius of the hexagon
  
  // To draw a hexagon (flat top/bottom):
  const hexPoints = [];
  for (let i = 0; i < 6; i++) {
    const angle_deg = 60 * i; // 0, 60, 120... gives flat top/bottom
    const angle_rad = Math.PI / 180 * angle_deg;
    hexPoints.push(`${HEX_R * Math.cos(angle_rad)},${HEX_R * Math.sin(angle_rad)}`);
  }
  const hexPointsString = hexPoints.join(' ');

  return (
    <div style={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column', padding: '1rem 2rem' }}>
      {(content.title || content.subtitle) && (
        <div style={{ marginBottom: '1rem' }}>
          {content.title && renderText(content.title, { fontSize: `${headerFontSize}rem`, fontWeight: 700, margin: 0, color: '#1e293b' }, 'h2')}
          {content.subtitle && renderText(content.subtitle, { fontSize: `${subFontSize}rem`, margin: '0.5rem 0 0 0', color: '#64748b' }, 'p')}
        </div>
      )}

      <div style={{ flex: 1, position: 'relative', marginTop: '2rem' }} ref={containerRef}>
        {dimensions.width > 0 && (
          <svg style={{ position: 'absolute', top: 0, left: 0, width: '100%', height: '100%', overflow: 'visible', zIndex: 0 }}>
            <defs>
              <filter id="hex-shadow" x="-30%" y="-30%" width="160%" height="160%">
                <feDropShadow dx="0" dy="4" stdDeviation="6" floodOpacity="0.15" />
              </filter>
              <filter id="ribbon-shadow" x="-5%" y="-30%" width="110%" height="160%">
                <feDropShadow dx="0" dy="2" stdDeviation="4" floodOpacity="0.1" />
              </filter>
              <marker 
                id="hex-arrow-default"
                markerWidth="12" 
                markerHeight="12" 
                refX="10" 
                refY="6" 
                orient="auto"
              >
                <path d="M 2 2 L 10 6 L 2 10" fill="none" stroke="#94a3b8" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
              </marker>
              <marker 
                id="hex-arrow-solid"
                markerWidth="12" 
                markerHeight="12" 
                refX="10" 
                refY="6" 
                orient="auto"
              >
                <path d="M 2 2 L 10 6 L 2 10 Z" fill="#94a3b8" stroke="none" />
              </marker>
              <marker 
                id="hex-arrow-circle"
                markerWidth="12" 
                markerHeight="12" 
                refX="6" 
                refY="6" 
                orient="auto"
              >
                <circle cx="6" cy="6" r="4" fill="#94a3b8" />
              </marker>
            </defs>

            {/* 1. Draw the Vertical Lines (drawn first so they are behind the ribbon) */}
            {steps.map((_, idx) => {
              const startX = offsetX + idx * StepWidth;
              const chevronStartX = startX + (idx === 0 ? 0 : CHEVRON_GAP);
              const chevronW = Math.min(60, StepWidth * 0.4);
              const isTop = idx % 2 === 0;

              // Line is centered on the total width of the colored chevron
              const lineX = chevronStartX + (chevronW + ARROW_HEAD_WIDTH) / 2;
              
              // Move Hexagon away from the ribbon, keep Text further away
              const hexCY = isTop ? CY - RIBBON_HEIGHT/2 - 40 : CY + RIBBON_HEIGHT/2 + 40;
              const lineStartY = isTop ? hexCY + HEX_R + 5 : hexCY - HEX_R - 5;
              
              const textCY = isTop ? CY + RIBBON_HEIGHT/2 + 55 : CY - RIBBON_HEIGHT/2 - 55;
              const lineEndY = isTop ? textCY - 65 : textCY + 65;

              return (
                <line 
                  key={`line-${idx}`}
                  x1={lineX} 
                  y1={lineStartY} 
                  x2={lineX} 
                  y2={lineEndY} 
                  stroke="#94a3b8" 
                  strokeWidth="2"
                  markerEnd={arrowType !== 'none' ? `url(#hex-arrow-${arrowType})` : undefined}
                />
              );
            })}

            {/* 2. Draw the Background White Ribbon */}
            <path 
              d={`
                M ${offsetX - 20} ${CY - RIBBON_HEIGHT/2} 
                L ${offsetX + TotalWidth} ${CY - RIBBON_HEIGHT/2}
                L ${offsetX + TotalWidth + ARROW_HEAD_WIDTH} ${CY}
                L ${offsetX + TotalWidth} ${CY + RIBBON_HEIGHT/2}
                L ${offsetX - 20} ${CY + RIBBON_HEIGHT/2}
                L ${offsetX - 20 + ARROW_HEAD_WIDTH} ${CY}
                Z
              `} 
              fill="#ffffff" 
              filter="url(#ribbon-shadow)"
            />

            {/* 3. Draw the Colored Chevrons and Labels */}
            {steps.map((step, idx) => {
              const startX = offsetX + idx * StepWidth;
              const chevronStartX = startX + (idx === 0 ? 0 : CHEVRON_GAP);
              const chevronW = Math.min(60, StepWidth * 0.4);
              const color = step.color || '#3b82f6';
              const chevronPath = `
                M ${chevronStartX} ${CY - RIBBON_HEIGHT/2} 
                L ${chevronStartX + chevronW} ${CY - RIBBON_HEIGHT/2}
                L ${chevronStartX + chevronW + ARROW_HEAD_WIDTH} ${CY}
                L ${chevronStartX + chevronW} ${CY + RIBBON_HEIGHT/2}
                L ${chevronStartX} ${CY + RIBBON_HEIGHT/2}
                L ${chevronStartX + ARROW_HEAD_WIDTH} ${CY}
                Z
              `;

              return (
                <g key={`chevron-${idx}`}>
                  <path d={chevronPath} fill={color} />

                  {/* Draw the "STEP XX" label inside the white part of the ribbon */}
                  <text
                    x={chevronStartX + chevronW + ARROW_HEAD_WIDTH + (StepWidth - chevronW - ARROW_HEAD_WIDTH - CHEVRON_GAP) / 2}
                    y={CY}
                    fill={color}
                    fontSize={Math.max(9, Math.min(14, StepWidth * 0.07))}
                    fontWeight="600"
                    fontFamily="sans-serif"
                    textAnchor="middle"
                    dominantBaseline="middle"
                  >
                    {step.stepLabel || `STEP 0${idx + 1}`}
                  </text>
                </g>
              );
            })}
            
            {/* 4. Draw the Hexagons */}
            {steps.map((step, idx) => {
              const startX = offsetX + idx * StepWidth;
              const chevronStartX = startX + (idx === 0 ? 0 : CHEVRON_GAP);
              const chevronW = Math.min(60, StepWidth * 0.4);
              const lineX = chevronStartX + (chevronW + ARROW_HEAD_WIDTH) / 2;
              
              const isTop = idx % 2 === 0;
              const color = step.color || '#3b82f6';
              const hexCY = isTop ? CY - RIBBON_HEIGHT/2 - 65 : CY + RIBBON_HEIGHT/2 + 65;

              return (
                <g key={`hex-${idx}`}>
                  <polygon 
                    transform={`translate(${lineX}, ${hexCY})`}
                    points={hexPointsString} 
                    fill={color} 
                    stroke="#ffffff" 
                    strokeWidth="6" 
                    filter="url(#hex-shadow)" 
                  />
                  {step.icon && (
                    <foreignObject 
                      x={lineX - HEX_R} 
                      y={hexCY - HEX_R} 
                      width={HEX_R * 2} 
                      height={HEX_R * 2}
                    >
                      <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                        <DynamicIcon name={step.icon} size={32} color="#ffffff" />
                      </div>
                    </foreignObject>
                  )}
                </g>
              );
            })}
          </svg>
        )}

        {/* 5. HTML Overlays for Text */}
        {dimensions.width > 0 && steps.map((step, idx) => {
          const startX = offsetX + idx * StepWidth;
          const chevronStartX = startX + (idx === 0 ? 0 : CHEVRON_GAP);
          const chevronW = Math.min(60, StepWidth * 0.4);
          
          // Line is centered on the total width of the colored chevron
          const lineX = chevronStartX + (chevronW + ARROW_HEAD_WIDTH) / 2;
          
          const isTop = idx % 2 === 0;
          const textCY = isTop ? CY + RIBBON_HEIGHT/2 + 55 : CY - RIBBON_HEIGHT/2 - 55;
          const maxTextWidth = Math.max(150, StepWidth * 0.9);

          return (
            <React.Fragment key={`html-${idx}`}>
              {/* Text Block */}
              <div style={{
                position: 'absolute',
                left: `${lineX - maxTextWidth / 2}px`,
                width: `${maxTextWidth}px`,
                ...(isTop 
                  ? { top: `${textCY - 45}px` } 
                  : { bottom: `${dimensions.height - (textCY + 45)}px` }
                ),
                textAlign: 'center',
                zIndex: 1,
                padding: '0 8px'
              }}>
                <div style={{ 
                  color: step.color || '#3b82f6', 
                  fontSize: '0.9rem', 
                  fontWeight: 700, 
                  textTransform: 'uppercase',
                  marginBottom: '4px'
                }}>
                  {renderText(step.title, {}, 'span')}
                </div>
                {step.description && (
                  <div style={{ color: '#64748b', fontSize: '0.8rem', lineHeight: 1.4 }}>
                    {renderText(step.description, {}, 'span')}
                  </div>
                )}
              </div>
            </React.Fragment>
          );
        })}
      </div>
    </div>
  );
};
