import React from 'react';
import * as LucideIcons from 'lucide-react';

interface DynamicIconProps {
  name?: string;
  size?: number | string;
  color?: string;
}

export const DynamicIcon: React.FC<DynamicIconProps> = ({ name, size = 24, color }) => {
  if (!name) return null;
  const iconName = name.split('-').map(part => part.charAt(0).toUpperCase() + part.slice(1)).join('');
  const IconComponent = (LucideIcons as any)[iconName];
  if (!IconComponent) return null;
  return <IconComponent size={size} color={color} />;
};
