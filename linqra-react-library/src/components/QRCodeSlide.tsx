import React from 'react';
import type { QRCodeSlideContent } from '../schemas';
import QRCode from 'react-qr-code';

interface Props {
  content: QRCodeSlideContent;
}

export const QRCodeSlide: React.FC<Props> = ({ content }) => {
  return (
    <div style={{
      display: 'inline-flex',
      padding: '1cqi',
      background: content.bgColor || '#ffffff',
      borderRadius: '8px',
      boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)',
      ...(content.containerStyle || {})
    }}>
      <QRCode
        value={content.value || 'https://linqra.com'}
        size={content.size || 128}
        bgColor={content.bgColor || '#ffffff'}
        fgColor={content.fgColor || '#000000'}
        level="Q"
        style={{ height: 'auto', maxWidth: '100%', width: '100%' }}
      />
    </div>
  );
};
