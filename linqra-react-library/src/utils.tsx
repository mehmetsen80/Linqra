import React from 'react';
import type { ImageContent } from './schemas';

export const renderImage = (img: ImageContent | undefined, key?: string | number) => {
  if (!img) return null;
  const isObj = typeof img === 'object' && img !== null;
  const url = isObj ? (img as any).url : img;
  const alt = isObj ? (img as any).alt || '' : '';
  const customStyle = isObj ? (img as any).style : {};
  return <img key={key} src={url} alt={alt} style={{ maxWidth: '100%', maxHeight: '40vh', objectFit: 'contain', margin: '0 auto 1rem auto', ...customStyle }} />;
};

export const renderText = (
  item: string | { text: string; style?: React.CSSProperties } | undefined, 
  defaultStyle: React.CSSProperties, 
  Tag: React.ElementType,
  key?: string | number
) => {
  if (!item) return null;
  const isObj = typeof item === 'object' && item !== null;
  const text = isObj ? (item as any).text : item;
  const customStyle = isObj ? (item as any).style : {};
  return <Tag key={key} style={{ ...defaultStyle, ...customStyle }}>{text}</Tag>;
};
