import React, { useEffect, useRef, useState } from 'react';
import type { AlternatingRingFlowSlideContent } from '../schemas';
import { renderText } from '../utils';
import { DynamicIcon } from './DynamicIcon';

interface Props {
  content: AlternatingRingFlowSlideContent;
}

export const AlternatingRingFlowSlide: React.FC<Props> = ({ content }) => {
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

  const startDirection = content.startDirection || 'over';

  // Calculate dynamic width and smart centering
  // We cap the StepWidth at 250px so a small number of nodes doesn't stretch ridiculously wide
  const StepWidth = Math.min(250, dimensions.width / N);
  const TotalWidth = StepWidth * N;
  const offsetX = (dimensions.width - TotalWidth) / 2;

  // Shift the entire graph up slightly to visually balance it better with the title and account for footer space
  const CY = (dimensions.height / 2) - 30; 
  const maxOuterR = 60;
  // Ensure the rings don't overlap by limiting OuterR based on available width
  const OuterR = Math.max(10, Math.min(maxOuterR, (StepWidth / 2) - 20));
  const RingThickness = Math.max(4, OuterR * 0.25);
  const InnerR = Math.max(1, OuterR - RingThickness);
  const ArcRY = OuterR + 20; // Height of the arrow arc

  return (
    <div ref={containerRef} style={{
      width: '100%',
      height: '100%',
      flex: 1,
      minHeight: 0,
      position: 'relative',
      display: 'flex',
      flexDirection: 'column',
      padding: 'clamp(1rem, 3cqmin, 2rem)',
      boxSizing: 'border-box',
      backgroundColor: 'transparent'
    }}>
      <div style={{ marginBottom: '1rem', zIndex: 10 }}>
        {renderText(content.title, { fontSize: 'clamp(1.5rem, 4cqmin, 2.5rem)', fontWeight: 700, margin: 0, color: '#1e293b' }, 'h2')}
        {content.subtitle && (
          renderText(content.subtitle, { fontSize: 'clamp(1rem, 2cqmin, 1.25rem)', margin: '0.5rem 0 0 0', color: '#64748b' }, 'p')
        )}
      </div>

      {/* SVG Canvas for Rings and Arrows */}
      <div style={{ position: 'relative', flex: 1, width: '100%' }}>
        <svg 
          width="100%" 
          height="100%" 
          style={{ position: 'absolute', top: 0, left: 0, pointerEvents: 'none', overflow: 'visible' }}
        >
          <defs>
            <filter id="ring-shadow" x="-20%" y="-20%" width="140%" height="140%">
              <feDropShadow dx="0" dy="4" stdDeviation="6" floodOpacity="0.15" />
            </filter>
            {/* Arrowhead marker for over-arcs (points down) */}
            {steps.map((step, i) => (
              <marker 
                key={`marker-${i}`}
                id={`arrowhead-${i}`} 
                markerWidth="10" 
                markerHeight="10" 
                refX="8" 
                refY="5" 
                orient="auto"
              >
                <path d="M 0 0 L 10 5 L 0 10 Z" fill={step.color || '#3b82f6'} />
              </marker>
            ))}
          </defs>

          {steps.map((step, i) => {
            // If startDirection is 'under', even indices (0, 2, 4) go UNDER (text DOWN).
            // If 'over', even indices (0, 2, 4) go OVER (text UP).
            const isDown = startDirection === 'under' ? (i % 2 === 0) : (i % 2 !== 0); 
            const CX = offsetX + i * StepWidth + (StepWidth / 2);
            
            // Arrow Arc Logic
            // The arrows touch on the X axis perfectly. 
            // We use stroke-dasharray to hide the first 5% of the curve for i > 0, creating a perfect gap without distorting the ellipse!
            const startX = offsetX + i * StepWidth;
            const endX = offsetX + (i + 1) * StepWidth;
            
            const rx = Math.max(1, StepWidth / 2);
            const ry = Math.max(1, ArcRY);
            const sweepFlag = isDown ? 1 : 0; // 1 = DOWN (positive Y, arc UNDER), 0 = UP (negative Y, arc OVER)
            
            const arcPath = `M ${startX} ${CY} A ${rx} ${ry} 0 0 ${sweepFlag} ${endX} ${CY}`;
            const arrowColor = step.color || '#3b82f6';

            return (
              <g key={`svg-step-${i}`}>
                {/* Arrow Path */}
                <path 
                  d={arcPath} 
                  fill="none" 
                  stroke={arrowColor} 
                  strokeWidth="2"
                  pathLength="100"
                  strokeDasharray={i > 0 ? "0 5 95 0" : "none"}
                  markerEnd={`url(#arrowhead-${i})`}
                />
                
                {/* Outer Ring */}
                <circle 
                  cx={CX} 
                  cy={CY} 
                  r={OuterR - (RingThickness / 2)} 
                  fill="none" 
                  stroke={arrowColor} 
                  strokeWidth={RingThickness} 
                />
                
                {/* Inner White Circle */}
                <circle 
                  cx={CX} 
                  cy={CY} 
                  r={InnerR} 
                  fill="#ffffff" 
                  filter="url(#ring-shadow)"
                />
              </g>
            );
          })}
        </svg>

        {/* HTML Overlays for Icons and Text */}
        {steps.map((step, i) => {
          const isDown = startDirection === 'under' ? (i % 2 === 0) : (i % 2 !== 0);
          const CX = offsetX + i * StepWidth + (StepWidth / 2);
          
          return (
            <React.Fragment key={`html-step-${i}`}>
              {/* Icon Container */}
              <div style={{
                position: 'absolute',
                left: `${CX - InnerR}px`,
                top: `${CY - InnerR}px`,
                width: `${InnerR * 2}px`,
                height: `${InnerR * 2}px`,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: 'var(--text-main, #1e293b)'
              }}>
                {step.icon && <DynamicIcon name={step.icon} size={InnerR * 0.8} color="#334155" />}
              </div>

              {/* Text Container Logic */}
              {(() => {
                // We want the text to be up to 1.8x the step width, but it MUST not overflow the screen edges.
                // To keep the text perfectly centered over the node, the maximum safe width is 2x the distance to the closest edge.
                const idealTextWidth = StepWidth * 1.8;
                const distToLeftEdge = CX;
                const distToRightEdge = dimensions.width - CX;
                const maxSafeWidth = 2 * Math.min(distToLeftEdge, distToRightEdge);
                
                const finalTextWidth = Math.min(idealTextWidth, maxSafeWidth);
                const textLeft = CX - (finalTextWidth / 2);

                // By pushing the text vertically out to OuterR + 28px, it perfectly grazes the peak of the adjacent arcs without intersecting them!
                return (
                  <div style={{
                    position: 'absolute',
                    left: `${textLeft}px`,
                    width: `${finalTextWidth}px`,
                    top: isDown ? `${CY + OuterR + 28}px` : `${CY - OuterR - 28}px`,
                    transform: !isDown ? 'translateY(-100%)' : 'none',
                    textAlign: 'center'
                  }}>
                    <h3 style={{ margin: '0 0 2px 0' }}>
                      {renderText(step.title, { fontSize: 'clamp(1rem, 2.5cqmin, 1.25rem)', fontWeight: 600, color: step.color || '#3b82f6', margin: 0 }, 'span')}
                    </h3>
                    {step.description && (
                      <p style={{ margin: 0 }}>
                        {renderText(step.description, { fontSize: 'clamp(0.85rem, 2cqmin, 1rem)', color: 'var(--text-secondary, #64748b)', lineHeight: 1.4, margin: 0 }, 'span')}
                      </p>
                    )}
                  </div>
                );
              })()}
            </React.Fragment>
          );
        })}
      </div>
    </div>
  );
};
