import React from 'react';
import type { NumberedTimelineSlideContent } from '../schemas';
import { renderText } from '../utils';
import { DynamicIcon } from './DynamicIcon';

interface Props {
  content: NumberedTimelineSlideContent;
}

const DEFAULT_COLORS = ['#3b82f6', '#3b82f6', '#3b82f6', '#3b82f6', '#3b82f6'];

export const NumberedTimelineSlide: React.FC<Props> = ({ content }) => {
  const { title, subtitle, steps, timelines: rawTimelines } = content;
  
  // Normalize into an array of timelines
  const timelines = rawTimelines || (steps ? [{ steps }] : []);

  return (
    <div style={{
      width: '100%',
      height: '100%',
      display: 'flex',
      flexDirection: 'column',
      padding: 'clamp(2.5rem, 4cqmin, 4rem)',
      boxSizing: 'border-box',
      background: 'var(--slide-bg, #ffffff)',
      fontFamily: 'var(--font-family, inherit)',
      overflow: 'hidden'
    }}>
      
      {/* Slide Title & Subtitle */}
      {(title || subtitle) && (
        <div style={{ marginBottom: '2rem', flexShrink: 0 }}>
          {renderText(title, {
            fontSize: 'clamp(1.75rem, 4cqmin, 2.5rem)',
            fontWeight: 800,
            color: 'var(--text-main, #0f172a)',
            margin: '0 0 1rem 0',
            lineHeight: 1.2
          }, 'h1')}
          
          {renderText(subtitle, {
            fontSize: 'clamp(1.1rem, 2cqmin, 1.25rem)',
            color: 'var(--text-secondary, #475569)',
            margin: 0,
            lineHeight: 1.5,
            maxWidth: '80%'
          }, 'p')}
        </div>
      )}

      {/* Timelines Container */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 'clamp(2rem, 4cqmin, 3rem)' }}>
        {timelines.map((timeline, tIdx) => (
          <div key={tIdx} style={{ display: 'flex', flexDirection: 'column' }}>
            
            {/* Timeline Title & Subtitle (Optional) */}
            {(timeline.title || timeline.subtitle) && (
              <div style={{ marginBottom: 'clamp(1rem, 2cqmin, 1.5rem)' }}>
                {renderText(timeline.title, {
                  fontSize: '1.5rem',
                  fontWeight: 700,
                  color: 'var(--text-main, #0f172a)',
                  margin: '0 0 0.5rem 0'
                }, 'h2')}
                {renderText(timeline.subtitle, {
                  fontSize: '1rem',
                  color: 'var(--text-secondary, #475569)',
                  margin: 0
                }, 'p')}
              </div>
            )}

            {/* Timeline Section */}
            <div style={{
              position: 'relative',
              display: 'flex',
              flexDirection: 'row',
              justifyContent: 'space-between',
              alignItems: 'flex-start',
            }}>
              
              {/* Dashed background line */}
              {timeline.steps.length > 1 && (
                <div style={{
                  position: 'absolute',
                  top: 'clamp(40px, 5cqmin, 48px)', // center of the responsive container
                  left: `calc(50% / ${timeline.steps.length})`, // center of first item
                  right: `calc(50% / ${timeline.steps.length})`, // center of last item
                  height: '2px',
                  borderTop: '2px dashed #3b82f6',
                  opacity: 0.5,
                  zIndex: 0
                }} />
              )}

              {/* Steps */}
              {timeline.steps.map((step, idx) => {
                const color = step.color || DEFAULT_COLORS[idx % DEFAULT_COLORS.length] || '#2563eb';
                const scale = step.nodeScale || 1;

                return (
                  <div key={idx} style={{
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    width: `${100 / timeline.steps.length}%`,
                    position: 'relative',
                    zIndex: 1
                  }}>
                    
                    {/* Number Circle Wrapper (Fixed Height for Alignment) */}
                    <div style={{
                      height: 'clamp(80px, 10cqmin, 96px)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      marginBottom: 'clamp(0.75rem, 2cqmin, 1rem)',
                      flexShrink: 0
                    }}>
                      <div style={{
                        width: `calc(clamp(56px, 7cqmin, 64px) * ${scale})`,
                        height: `calc(clamp(56px, 7cqmin, 64px) * ${scale})`,
                        borderRadius: '50%',
                        background: 'var(--slide-bg, #ffffff)', // Solid background to block dashed line
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                      }}>
                        <div style={{
                          width: '100%',
                          height: '100%',
                          borderRadius: '50%',
                          background: `${color}33`, // semi-transparent background
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                        }}>
                        <div style={{
                          width: `calc(clamp(40px, 5cqmin, 48px) * ${scale})`,
                          height: `calc(clamp(40px, 5cqmin, 48px) * ${scale})`,
                          borderRadius: '50%',
                          background: color,
                          color: '#ffffff',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          fontWeight: 700,
                          fontSize: `calc(clamp(1.25rem, 2cqmin, 1.5rem) * ${scale})`
                        }}>
                            {step.number}
                          </div>
                        </div>
                      </div>
                    </div>

                    {/* Icon Box */}
                    {step.icon && (
                      <div style={{
                        width: 'clamp(56px, 6cqmin, 64px)',
                        height: 'clamp(56px, 6cqmin, 64px)',
                        borderRadius: 'clamp(14px, 2cqmin, 16px)',
                        background: '#f8fafc',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        marginBottom: 'clamp(0.75rem, 2cqmin, 1.5rem)',
                        boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.05)',
                        flexShrink: 0
                      }}>
                        <DynamicIcon name={step.icon} size={24} color={color} />
                      </div>
                    )}

                    {/* Text Block */}
                    <div style={{ textAlign: 'center', padding: '0 clamp(0.25rem, 1cqmin, 1rem)' }}>
                      {renderText(step.title, {
                        display: 'block',
                        fontSize: 'clamp(1.1rem, 2cqmin, 1.25rem)',
                        fontWeight: 700,
                        color: color,
                        marginBottom: '0.25rem'
                      }, 'div')}

                      {renderText(step.subtitle, {
                        display: 'block',
                        fontSize: 'clamp(0.95rem, 1.5cqmin, 1rem)',
                        color: '#64748b',
                        marginBottom: 'clamp(0.5rem, 1.5cqmin, 1rem)'
                      }, 'div')}

                      {renderText(step.description, {
                        display: 'block',
                        fontSize: 'clamp(1rem, 1.5cqmin, 1.1rem)',
                        color: '#334155',
                        lineHeight: 1.5
                      }, 'div')}
                    </div>

                  </div>
                );
              })}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};
