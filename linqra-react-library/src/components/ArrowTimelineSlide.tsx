import React from 'react';
import type { ArrowTimelineSlideContent } from '../schemas';
import { renderText } from '../utils';
import { DynamicIcon } from './DynamicIcon';

export interface ArrowTimelineSlideProps {
  content: ArrowTimelineSlideContent;
}

export const ArrowTimelineSlide: React.FC<ArrowTimelineSlideProps> = ({ content }) => {
  const events = content.events || [];
  if (events.length === 0) return null;

  const timelineColor = content.timelineColor || '#cbd5e1';

  return (
    <div style={{ width: '100%', flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column', padding: 'clamp(1rem, 3cqmin, 2rem)', boxSizing: 'border-box' }}>
      <div style={{ marginBottom: '1rem' }}>
        {renderText(content.title, { fontSize: 'clamp(1.5rem, 4cqmin, 2.5rem)', fontWeight: 700, margin: 0, color: '#1e293b' }, 'h2')}
        {renderText(content.subtitle, { fontSize: 'clamp(1rem, 2cqmin, 1.25rem)', margin: '0.5rem 0 0 0', color: '#64748b' }, 'p')}
      </div>

      {/* Apply a physical transform to shift the entire graph UP to counter-balance the title header */}
      <div style={{ flex: 1, position: 'relative', display: 'flex', alignItems: 'center', justifyContent: 'center', transform: 'translateY(-20px)' }}>

        {/* Main Central Arrow Timeline */}
        <div style={{
          position: 'absolute',
          top: '50%',
          left: '2%', // Extend slightly past the events
          right: '2%',
          height: '24px',
          marginTop: '-12px',
          backgroundColor: timelineColor,
          clipPath: 'polygon(0 0, calc(100% - 24px) 0, 100% 50%, calc(100% - 24px) 100%, 0 100%)',
          zIndex: 0
        }} />

        {/* Events Grid Container */}
        <div style={{
          width: '90%',
          height: '100%',
          display: 'flex',
          justifyContent: 'space-between',
          position: 'relative',
          zIndex: 1
        }}>
          {events.map((event, idx) => {
            const isTop = idx % 2 === 0;
            const color = event.color || '#3b82f6';

            return (
              <div key={idx} style={{
                flex: 1,
                display: 'grid',
                // 3 Tracks: Top content, center spacer (where the arrow is), bottom content
                gridTemplateRows: 'minmax(0, 1fr) 60px minmax(0, 1fr)',
                alignItems: 'center',
                justifyItems: 'center',
                textAlign: 'center',
                padding: '0 0.5rem'
              }}>
                {/* Top Content Area */}
                {isTop ? (
                  <div style={{ alignSelf: 'end', paddingBottom: '1rem', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                    <div style={{
                      padding: '0.25rem 0.75rem',
                      backgroundColor: color,
                      color: '#fff',
                      borderRadius: '999px',
                      fontWeight: 700,
                      marginBottom: '0.75rem',
                      fontSize: 'clamp(0.75rem, 1.5cqmin, 0.9rem)',
                      boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
                    }}>
                      {renderText(event.date, {}, 'span')}
                    </div>
                    {renderText(event.title, { margin: '0 0 0.25rem 0', color: '#1e293b', fontSize: 'clamp(0.85rem, 2cqmin, 1.15rem)' }, 'h3')}
                    {renderText(event.description, { margin: 0, color: '#64748b', fontSize: 'clamp(0.7rem, 1.5cqmin, 0.85rem)', lineHeight: 1.4 }, 'p')}
                  </div>
                ) : <div />}

                {/* Center Node (Dot on the timeline) */}
                <div style={{
                  width: event.icon ? '40px' : '20px',
                  height: event.icon ? '40px' : '20px',
                  borderRadius: '50%',
                  backgroundColor: event.icon ? color : '#ffffff',
                  border: event.icon ? '2px solid #ffffff' : `4px solid ${color}`,
                  boxShadow: '0 2px 6px rgba(0,0,0,0.15)',
                  position: 'relative',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  zIndex: 2
                }}>
                  {event.icon && <DynamicIcon name={event.icon} size={20} color="#ffffff" />}
                  {/* Stem connecting dot to content */}
                  <div style={{
                    position: 'absolute',
                    left: '50%',
                    marginLeft: '-1px',
                    width: '2px',
                    height: '28px',
                    backgroundColor: color,
                    top: isTop ? (event.icon ? '-32px' : '-32px') : (event.icon ? '40px' : '20px'), // Stem extends up or down
                    zIndex: -1
                  }} />
                </div>

                {/* Bottom Content Area */}
                {!isTop ? (
                  <div style={{ alignSelf: 'start', paddingTop: '1rem', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                    {renderText(event.title, { margin: '0 0 0.25rem 0', color: '#1e293b', fontSize: 'clamp(0.85rem, 2cqmin, 1.15rem)' }, 'h3')}
                    {renderText(event.description, { margin: '0 0 0.75rem 0', color: '#64748b', fontSize: 'clamp(0.7rem, 1.5cqmin, 0.85rem)', lineHeight: 1.4 }, 'p')}
                    <div style={{
                      padding: '0.25rem 0.75rem',
                      backgroundColor: color,
                      color: '#fff',
                      borderRadius: '999px',
                      fontWeight: 700,
                      fontSize: 'clamp(0.75rem, 1.5cqmin, 0.9rem)',
                      boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
                    }}>
                      {renderText(event.date, {}, 'span')}
                    </div>
                  </div>
                ) : <div />}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
};
