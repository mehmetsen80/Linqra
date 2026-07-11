import React from 'react';
import type { StepperSlideContent } from '../schemas';
import { DynamicIcon } from './DynamicIcon';
import { renderText } from '../utils';

interface Props {
  content: StepperSlideContent;
}

export const StepperSlide: React.FC<Props> = ({ content }) => {
  const isVertical = content.orientation === 'vertical';
  const activeColor = content.activeColor || '#ea580c';
  const inactiveColor = content.inactiveColor || '#cbd5e1';

  return (
    <div style={{
      display: 'flex',
      flexDirection: isVertical ? 'column' : 'row',
      justifyContent: 'space-between',
      alignItems: isVertical ? 'flex-start' : 'center',
      position: 'relative',
      width: '100%',
      gap: isVertical ? '2cqi' : '0',
      ...(content.containerStyle || {})
    }}>
      {/* Background Line */}
      <div style={{
        position: 'absolute',
        top: isVertical ? '0' : '1cqi',
        left: isVertical ? '1cqi' : '5%',
        width: isVertical ? '2px' : '90%',
        height: isVertical ? '100%' : '2px',
        background: inactiveColor,
        zIndex: 0
      }} />

      {/* Progress Line */}
      <div style={{
        position: 'absolute',
        top: isVertical ? '0' : '1cqi',
        left: isVertical ? '1cqi' : '5%',
        width: isVertical ? '2px' : `${(content.activeIndex / Math.max(1, content.steps.length - 1)) * 90}%`,
        height: isVertical ? `${(content.activeIndex / Math.max(1, content.steps.length - 1)) * 100}%` : '2px',
        background: activeColor,
        zIndex: 1,
        transition: 'all 0.3s ease'
      }} />

      {content.steps.map((step, idx) => {
        const isActive = idx <= content.activeIndex;
        const color = isActive ? activeColor : inactiveColor;
        
        return (
          <div key={idx} style={{
            display: 'flex',
            flexDirection: isVertical ? 'row' : 'column',
            alignItems: isVertical ? 'center' : 'center',
            gap: '1cqi',
            zIndex: 2,
            position: 'relative',
            width: isVertical ? '100%' : 'auto',
            textAlign: isVertical ? 'left' : 'center'
          }}>
            <div style={{
              width: '2cqi',
              height: '2cqi',
              borderRadius: '50%',
              background: isActive ? activeColor : '#ffffff',
              border: `2px solid ${color}`,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              transition: 'all 0.3s ease'
            }}>
              {step.icon && (
                <DynamicIcon 
                  name={step.icon} 
                  size="1.2cqi" 
                  color={isActive ? '#ffffff' : color} 
                />
              )}
            </div>
            
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              {renderText(step.label, {
                fontSize: '0.9cqi',
                fontWeight: '700',
                color: isActive ? '#0f172a' : '#64748b'
              }, 'div')}
              {step.description && renderText(step.description, {
                  fontSize: '0.75cqi',
                  color: '#64748b',
                  marginTop: '0.3cqi',
                  maxWidth: isVertical ? 'auto' : '15cqi'
              }, 'div')}
            </div>
          </div>
        );
      })}
    </div>
  );
};
