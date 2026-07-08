import React from 'react';
import type { TaskListSlideContent } from '../schemas';
import { Square, CheckSquare } from 'lucide-react';

interface Props {
  content: TaskListSlideContent;
}

export const TaskListSlide: React.FC<Props> = ({ content }) => {
  const { title, tasks } = content;

  const renderText = (textObj: any, defaultStyle: React.CSSProperties = {}, Tag: any = 'span') => {
    if (!textObj) return null;
    if (typeof textObj === 'string') return <Tag style={defaultStyle}>{textObj}</Tag>;
    return <Tag style={{ ...defaultStyle, ...(textObj.style || {}) }}>{textObj.text}</Tag>;
  };

  return (
    <div style={{
      width: '100%',
      height: '100%',
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'flex-start',
      padding: 'clamp(0.5rem, 3cqmin, 4rem)',
      boxSizing: 'border-box',
      background: 'var(--slide-bg, #ffffff)',
      fontFamily: 'var(--font-family, inherit)'
    }}>
      <div style={{
        width: '100%',
        maxWidth: '550px',
        backgroundColor: '#ffffff',
        borderRadius: '16px',
        boxShadow: '0 10px 25px -5px rgba(0, 0, 0, 0.05), 0 8px 10px -6px rgba(0, 0, 0, 0.01)',
        border: '1px solid #f1f5f9',
        padding: 'clamp(0.75rem, 2cqmin, 2.5rem)',
        display: 'flex',
        flexDirection: 'column'
      }}>
        {/* Header */}
        {title && (
          <div style={{ marginBottom: 'clamp(0.5rem, 2cqmin, 1rem)' }}>
            {renderText(title, {
              fontSize: 'clamp(1.15rem, 2cqmin, 1.35rem)',
              fontWeight: 800,
              color: '#1e293b',
              margin: 0
            }, 'h2')}
          </div>
        )}

        {/* Task List */}
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          {tasks.map((task, idx) => {
            const isLast = idx === tasks.length - 1;
            
            return (
              <div key={idx} style={{
                display: 'flex',
                alignItems: 'center',
                padding: 'clamp(0.5rem, 2cqmin, 1.25rem) 0',
                borderBottom: isLast ? 'none' : '1px solid #f1f5f9',
                gap: 'clamp(0.5rem, 2cqmin, 1rem)'
              }}>
                {/* Checkbox Icon */}
                <div style={{ 
                  flexShrink: 0, 
                  color: task.checked ? (task.checkedColor || '#3b82f6') : '#cbd5e1'
                }}>
                  {task.checked ? (
                    <CheckSquare size={24} strokeWidth={2.5} />
                  ) : (
                    <Square size={24} strokeWidth={2.5} />
                  )}
                </div>

                {/* Text Content */}
                <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
                  {renderText(task.title, {
                    fontSize: 'clamp(0.85rem, 2cqmin, 1rem)',
                    fontWeight: 700,
                    color: '#334155',
                    margin: 0,
                    lineHeight: 1.3
                  }, 'div')}
                  
                  {task.subtitle && renderText(task.subtitle, {
                    fontSize: 'clamp(0.75rem, 1.5cqmin, 0.85rem)',
                    fontWeight: 600,
                    color: task.subtitleColor || '#94a3b8',
                    margin: 0
                  }, 'div')}
                </div>

                {/* Badge */}
                {task.badgeText && (
                  <div style={{
                    backgroundColor: task.badgeBg || '#f1f5f9',
                    color: task.badgeColor || '#475569',
                    padding: '0.2rem 0.6rem',
                    borderRadius: '8px',
                    fontSize: 'clamp(0.7rem, 1.5cqmin, 0.85rem)',
                    fontWeight: 700,
                    flexShrink: 0,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    minWidth: '1.5rem'
                  }}>
                    {task.badgeText}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
};
