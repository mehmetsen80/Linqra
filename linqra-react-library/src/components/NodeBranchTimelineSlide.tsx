import React, { useEffect, useRef, useState } from 'react';
import type { NodeBranchTimelineSlideContent } from '../schemas';
import { renderText } from '../utils';
import { DynamicIcon } from './DynamicIcon';

export interface NodeBranchTimelineSlideProps {
  content: NodeBranchTimelineSlideContent;
}

export const NodeBranchTimelineSlide: React.FC<NodeBranchTimelineSlideProps> = ({ content }) => {
  const steps = content.steps || [];
  const containerRef = useRef<HTMLDivElement>(null);
  const [dimensions, setDimensions] = useState({ width: 0, height: 0 });

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

  const N = steps.length;
  if (N === 0) return null;

  const arrowType = content.arrowType || 'solid';
  const NODE_R = content.nodeSize || 36; // Increased from 28 to 36 for larger nodes

  // Geometry Math
  const PADDING_LEFT = 30; // Visual left balance
  const PADDING_RIGHT = 220; // Reserves exact space needed for the final text box overflow
  const TotalWidth = dimensions.width - PADDING_LEFT - PADDING_RIGHT;
  const StepWidth = N > 1 ? TotalWidth / (N - 1) : TotalWidth; // Spreads nodes fully across the available width
  const offsetX = PADDING_LEFT; 
  const CY = dimensions.height / 2 + 5; 
  const BRANCH_OFFSET_X = NODE_R + 15;
  const TEXT_BOX_OFFSET_X = BRANCH_OFFSET_X + 25; // Consistent 25px horizontal arrow run for all nodes 
  const BRANCH_Y_STEP = 20; // Decreased to reduce vertical footprint

  // HTML Text Box Sizing (Takes advantage of alternating top/bottom to allow wider boxes)
  // We use a high multiplier (1.85) because top/bottom alternating gives us 2x StepWidth clearance
  const maxTextWidth = Math.min(260, Math.max(200, StepWidth * 1.85)); 

  const headerFontSize = 2.2;
  const subFontSize = 1.1;

  return (
    <div style={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column', padding: '2rem 3rem' }}>
      {(content.title || content.subtitle) && (
        <div style={{ marginBottom: '1rem' }}>
          {content.title && renderText(content.title, { fontSize: `${headerFontSize}rem`, fontWeight: 700, margin: 0, color: '#1e293b' }, 'h2')}
          {content.subtitle && renderText(content.subtitle, { fontSize: `${subFontSize}rem`, margin: '0.5rem 0 0 0', color: '#64748b' }, 'p')}
        </div>
      )}

      <div style={{ flex: 1, position: 'relative', marginTop: '1rem' }} ref={containerRef}>
        {dimensions.width > 0 && (
          <svg style={{ position: 'absolute', top: 0, left: 0, width: '100%', height: '100%', overflow: 'visible', zIndex: 0 }}>
            <defs>
              <filter id="node-shadow" x="-20%" y="-20%" width="140%" height="140%">
                <feDropShadow dx="0" dy="2" stdDeviation="4" floodOpacity="0.15" />
              </filter>

              <marker id="branch-arrow-default" markerWidth="12" markerHeight="12" refX="10" refY="6" orient="auto">
                <path d="M 2 2 L 10 6 L 2 10" fill="none" stroke="#94a3b8" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
              </marker>
              <marker id="branch-arrow-solid" markerWidth="12" markerHeight="12" refX="10" refY="6" orient="auto">
                <path d="M 2 2 L 10 6 L 2 10 Z" fill="#94a3b8" stroke="none" />
              </marker>
              <marker id="branch-arrow-circle" markerWidth="12" markerHeight="12" refX="6" refY="6" orient="auto">
                <circle cx="6" cy="6" r="4" fill="#94a3b8" />
              </marker>
            </defs>

            {/* 1. Main Horizontal Axis Line */}
            <line
              x1={offsetX - NODE_R - 20}
              y1={CY}
              x2={offsetX + (N - 1) * StepWidth + StepWidth * 0.8}
              y2={CY}
              stroke="#cbd5e1"
              strokeWidth="3"
            />

            {/* 2. Branching Paths */}
            {steps.map((_, idx) => {
              const nodeX = offsetX + idx * StepWidth;
              const isTop = idx % 2 === 0;
              const startX = nodeX + BRANCH_OFFSET_X;
              const targetX = nodeX + TEXT_BOX_OFFSET_X;
              
              const midY = isTop ? CY - BRANCH_Y_STEP : CY + BRANCH_Y_STEP;
              const endY = isTop ? CY - BRANCH_Y_STEP - 20 : CY + BRANCH_Y_STEP + 20;

              return (
                <path
                  key={`branch-${idx}`}
                  d={`
                    M ${startX} ${CY}
                    L ${startX} ${midY}
                    L ${targetX} ${midY}
                    L ${targetX} ${endY}
                  `}
                  fill="none"
                  stroke="#94a3b8"
                  strokeWidth="2"
                  markerEnd={arrowType !== 'none' ? `url(#branch-arrow-${arrowType})` : undefined}
                />
              );
            })}

            {/* 3. Circular Nodes */}
            {steps.map((step, idx) => {
              const nodeX = offsetX + idx * StepWidth;
              const color = step.color || '#3b82f6';

              return (
                <g key={`node-${idx}`} filter="url(#node-shadow)">
                  {/* Outer White Border */}
                  <circle cx={nodeX} cy={CY} r={NODE_R} fill="#ffffff" />
                  {/* Inner Colored Circle */}
                  <circle cx={nodeX} cy={CY} r={NODE_R - 6} fill={color} />

                  {step.icon && (
                    <foreignObject
                      x={nodeX - NODE_R}
                      y={CY - NODE_R}
                      width={NODE_R * 2}
                      height={NODE_R * 2}
                    >
                      <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                        <DynamicIcon name={step.icon} size={Math.max(16, NODE_R - 10)} color="#ffffff" />
                      </div>
                    </foreignObject>
                  )}
                </g>
              );
            })}
          </svg>
        )}

        {/* 4. HTML Smart Text Boxes */}
        {dimensions.width > 0 && steps.map((step, idx) => {
          const nodeX = offsetX + idx * StepWidth;
          const isTop = idx % 2 === 0;
          const targetX = nodeX + TEXT_BOX_OFFSET_X;
          const color = step.color || '#3b82f6';
          const endY = isTop ? CY - BRANCH_Y_STEP - 20 : CY + BRANCH_Y_STEP + 20;

          // Gap between arrowhead and the box
          const GAP = 15;

          const labelText = step.stepLabel || `STEP 0${idx + 1}`;

          const headerBlock = (
            <div style={{
              backgroundColor: color,
              color: '#ffffff',
              padding: '6px 12px',
              fontSize: '0.65rem',
              fontWeight: 700,
              textTransform: 'uppercase',
              textAlign: 'center',
              letterSpacing: '0.05em'
            }}>
              {labelText}
            </div>
          );

          const bodyBlock = (
            <div style={{
              backgroundColor: '#ffffff',
              padding: '16px',
              display: 'flex',
              flexDirection: 'column',
              gap: '6px'
            }}>
              <div style={{ color: color, fontSize: '0.75rem', fontWeight: 800, textTransform: 'uppercase' }}>
                {renderText(step.title, {}, 'span')}
              </div>
              {step.description && (
                <div style={{ color: '#64748b', fontSize: '0.7rem', lineHeight: 1.5 }}>
                  {renderText(step.description, {}, 'span')}
                </div>
              )}
            </div>
          );

          return (
            <div
              key={`html-${idx}`}
              style={{
                position: 'absolute',
                left: `${targetX - maxTextWidth / 2}px`,
                width: `${maxTextWidth}px`,
                ...(isTop
                  ? { bottom: `${dimensions.height - (endY - GAP)}px` }
                  : { top: `${endY + GAP}px` }
                ),
                zIndex: 1,
                borderRadius: '8px',
                overflow: 'hidden',
                boxShadow: '0 4px 15px rgba(0,0,0,0.08)',
                display: 'flex',
                flexDirection: 'column',
                border: '1px solid #f1f5f9'
              }}
            >
              {/* If node is on top, the colored label is the FOOTER (at the bottom, near the arrow).
                  If node is on bottom, the colored label is the HEADER (at the top, near the arrow). */}
              {isTop ? (
                <>
                  {bodyBlock}
                  {headerBlock}
                </>
              ) : (
                <>
                  {headerBlock}
                  {bodyBlock}
                </>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
};
