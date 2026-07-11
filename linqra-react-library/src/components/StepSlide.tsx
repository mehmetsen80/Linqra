import React from 'react';
import type { StepSlideContent } from '../schemas';
import { DynamicIcon } from './DynamicIcon';
import { renderText } from '../utils';

interface Props {
  content: StepSlideContent;
}

export const StepSlide: React.FC<Props> = ({ content }) => {
  const getStatusColor = () => {
    switch (content.status) {
      case 'completed': return { bg: '#10b981', color: '#fff', border: '#10b981' };
      case 'active': return { bg: '#3b82f6', color: '#fff', border: '#3b82f6' };
      case 'pending': 
      default: return { bg: '#ffffff', color: '#94a3b8', border: '#cbd5e1' };
    }
  };

  const colors = getStatusColor();

  return (
    <div style={{
      display: 'flex',
      alignItems: 'flex-start',
      gap: '1.5cqi',
      ...(content.containerStyle || {})
    }}>
      <div style={{
        width: '3.5cqi',
        height: '3.5cqi',
        borderRadius: '50%',
        backgroundColor: colors.bg,
        border: `2px solid ${colors.border}`,
        color: colors.color,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        flexShrink: 0,
        fontSize: '1.6cqi',
        fontWeight: 'bold',
        marginTop: '0.2cqi',
        boxSizing: 'border-box'
      }}>
        {content.icon ? <DynamicIcon name={content.icon} size="1em" /> : content.number}
      </div>
      
      {(content.title || content.description) && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5cqi' }}>
          {content.title && renderText(content.title, { fontSize: '1.8cqi', fontWeight: 700, color: 'var(--text-main, #1e293b)' }, 'div')}
          {content.description && renderText(content.description, { fontSize: '1.4cqi', color: 'var(--text-secondary, #64748b)', lineHeight: 1.5 }, 'div')}
        </div>
      )}
    </div>
  );
};
