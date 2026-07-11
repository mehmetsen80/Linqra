import React from 'react';
import * as LucideIcons from 'lucide-react';

interface DynamicIconProps {
  name?: string;
  size?: number | string;
  color?: string;
  containerStyle?: React.CSSProperties;
}

export const DynamicIcon: React.FC<DynamicIconProps> = ({ name, size = 24, color, containerStyle }) => {
  if (!name) return null;
  const iconName = name.split('-').map(part => part.charAt(0).toUpperCase() + part.slice(1)).join('');
  const IconComponent = (LucideIcons as any)[iconName];
  if (!IconComponent) return null;
  
  if (containerStyle) {
    return (
      <div style={containerStyle}>
        <IconComponent size={size} color={color} />
      </div>
    );
  }
  return <IconComponent size={size} color={color} />;
};
