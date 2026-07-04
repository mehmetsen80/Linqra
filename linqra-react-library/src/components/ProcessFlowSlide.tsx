import React from 'react';
import type { ProcessFlowSlideContent } from '../schemas';
import { DynamicIcon } from './DynamicIcon';

interface Props {
  content: ProcessFlowSlideContent;
}

const STEP_COLORS = ['#6366f1', '#ec4899', '#14b8a6', '#f59e0b', '#22c55e', '#3b82f6', '#ef4444'];

import { Arrow } from './Arrow';
import { renderImage, renderText } from '../utils';

export const ProcessFlowSlide: React.FC<Props> = ({ content }) => {
  const steps = content.steps;
  const direction = content.direction || 'horizontal';
  const isVertical = direction === 'vertical';
  // Compute a scale factor that grows as the number of steps shrinks (e.g. 5 steps = 1x, 3 steps = 1.6x)
  // Disable scaling for vertical layouts because vertical height on a 16:9 slide is highly constrained.
  const stepScale = isVertical ? 0.85 : Math.min(1.6, Math.max(1, 5 / Math.max(1, steps.length)));

  return (
    <div
      style={{
        containerType: 'size',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        boxSizing: 'border-box',
        padding: 'var(--slide-padding, clamp(0.5rem, 3cqmin, 2rem))',
        fontFamily: 'var(--font-family)',
        overflow: 'hidden',
      }}
    >
      {renderText(content.title, { margin: '0 0 clamp(0.5rem, 2cqmin, 1rem) 0', color: 'var(--text-main)', fontSize: 'min(var(--title-font-size, clamp(1.2rem, 5cqmin, 2.5rem)), 8cqmin)', fontWeight: 'var(--title-font-weight, 700)', textAlign: 'var(--title-align, left)' as any }, 'h1')}
      {renderImage(content.image)}
      {renderText(content.subtitle, { margin: '0 0 var(--content-gap, 0.5rem) 0', color: 'var(--text-secondary)', fontSize: 'min(var(--body-font-size, clamp(0.8rem, 2.5cqmin, 1.1rem)), 3cqmin)' }, 'p')}

      <div
        style={{
          flex: 1,
          minHeight: 0,
          minWidth: 0,
          display: 'flex',
          flexDirection: isVertical ? 'column' : 'row',
          alignItems: isVertical ? 'center' : 'stretch',
          gap: 0,
          '--step-scale': stepScale,
        } as React.CSSProperties}
      >
        {steps.map((step, i) => {
          const color = step.color || STEP_COLORS[i % STEP_COLORS.length];
          const nextColor = i < steps.length - 1
            ? (steps[i + 1].color || STEP_COLORS[(i + 1) % STEP_COLORS.length])
            : color;
          const isLast = i === steps.length - 1;

          return (
            <React.Fragment key={i}>
              {/* Step card */}
              <div
                style={{
                  flex: 1,
                  width: isVertical ? '80%' : undefined,
                  minHeight: 0,
                  minWidth: 0,
                  background: 'var(--card-bg)',
                  border: '1px solid var(--card-border)',
                  borderRadius: 'var(--card-radius)',
                  borderTop: `4px solid ${color}`,
                  padding: 'var(--card-padding, calc(var(--step-scale) * clamp(0.5rem, 2.5cqmin, 1.1rem)))',
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'flex-start',
                  textAlign: 'left',
                  gap: 'calc(var(--step-scale) * clamp(0.25rem, 1cqmin, 0.45rem))',
                  position: 'relative',
                  boxShadow: '0 2px 8px rgba(0,0,0,0.06)',
                  // cardStyle from data overrides any of the above
                  ...step.cardStyle,
                }}
              >
                {/* Header row: badge + icon + title */}
                <div style={{ display: 'flex', alignItems: 'center', gap: 'calc(var(--step-scale) * clamp(0.3rem, 1cqmin, 0.5rem))', width: '100%' }}>
                  {/* Step number badge */}
                  <div
                    style={{
                      width: 'var(--badge-width, calc(var(--step-scale) * clamp(20px, 3cqmin, 32px)))',
                      height: 'var(--badge-height, calc(var(--step-scale) * clamp(20px, 3cqmin, 32px)))',
                      borderRadius: 'var(--badge-radius, 50%)',
                      background: `var(--badge-bg, ${color})`,
                      color: 'var(--badge-color, white)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontSize: 'var(--badge-font-size, calc(var(--step-scale) * clamp(10px, 1.8cqmin, 14px)))',
                      fontWeight: 'var(--badge-font-weight, bold)' as any,
                      flexShrink: 0,
                      boxShadow: `var(--badge-shadow, 0 0 0 3px ${color}22)`,
                    }}
                  >
                    {i + 1}
                  </div>

                  {/* Icon */}
                  {step.icon && (
                    <div style={{ color, opacity: 0.85, lineHeight: 1, flexShrink: 0 }}>
                      <DynamicIcon name={step.icon} size="calc(var(--step-scale) * 16px)" />
                    </div>
                  )}

                  {/* Title */}
                  {renderText(step.title, { fontWeight: 700, fontSize: 'calc(var(--step-scale) * clamp(0.7rem, 2cqmin, 0.95rem))', color: 'var(--text-main)', lineHeight: 1.2, flex: 1 }, 'div')}
                </div>

                {/* Divider */}
                <div style={{ width: '100%', height: '1px', background: `linear-gradient(90deg, ${color}44, transparent)` }} />

                {/* Content Wrapper */}
                <div style={{
                  display: 'flex',
                  flexDirection: 'column',
                  flex: 1,
                  justifyContent: 'var(--content-justify, flex-start)',
                  gap: '0.6rem',
                  width: '100%',
                  fontFamily: 'var(--content-font-family, inherit)'
                }}>
                  {/* Description */}
                  {renderText(step.description, { fontSize: 'var(--desc-font-size, calc(var(--step-scale) * clamp(0.7rem, 2.2cqmin, 1.1rem)))', textAlign: 'var(--desc-text-align, inherit)' as any, color: 'var(--text-secondary)', lineHeight: 1.45, width: '100%' }, 'div')}

                  {/* Bullet points */}
                  {step.points && step.points.length > 0 && (
                    <ul
                      style={{
                        margin: 0,
                        paddingLeft: 'calc(var(--step-scale) * clamp(0.8rem, 2cqmin, 1.1rem))',
                        display: 'flex',
                        flexDirection: 'column',
                        gap: 'calc(var(--step-scale) * clamp(0.1rem, 0.5cqmin, 0.25rem))',
                        width: '100%',
                        boxSizing: 'border-box',
                        listStyle: 'none',
                      }}
                    >
                      {step.points.map((pt, pi) => (
                        <li
                          key={pi}
                          style={{
                            fontSize: 'var(--point-font-size, calc(var(--step-scale) * clamp(0.65rem, 2cqmin, 1.0rem)))',
                            color: 'var(--text-secondary)',
                            lineHeight: 1.4,
                            display: 'flex',
                            alignItems: 'flex-start',
                            gap: '0.3rem',
                          }}
                        >
                          <span style={{ color, fontWeight: 700, flexShrink: 0, marginTop: '0.05em' }}>›</span>
                          {renderText(pt, {}, 'span')}
                        </li>
                      ))}
                    </ul>
                  )}
                </div>

                {/* Subtle bottom accent line */}
                <div
                  style={{
                    position: 'absolute',
                    bottom: 0,
                    left: '15%',
                    right: '15%',
                    height: '2px',
                    background: `linear-gradient(90deg, transparent, ${color}55, transparent)`,
                    borderRadius: '0 0 4px 4px',
                  }}
                />
              </div>

              {/* Arrow connector — with breathing room on both sides */}
              {!isLast && (
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    flexShrink: 0,
                    padding: `var(--arrow-padding, ${isVertical ? 'clamp(2px, 0.5cqmin, 6px) 0' : '0 clamp(2px, 0.5cqmin, 6px)'})`,
                    width: isVertical ? '100%' : 'var(--step-connector-width, clamp(28px, 4cqmin, 52px))',
                    height: isVertical ? 'var(--step-connector-width, clamp(28px, 4cqmin, 44px))' : '100%',
                    zIndex: 1, // Ensure arrow renders on top of subsequent cards so the head isn't cut off
                    ...step.arrowStyle
                  }}
                >
                  <Arrow fromColor={color} toColor={nextColor} vertical={isVertical} type={step.arrowType} />
                </div>
              )}
            </React.Fragment>
          );
        })}
      </div>
    </div>
  );
};
