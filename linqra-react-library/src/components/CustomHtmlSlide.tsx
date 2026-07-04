import React from 'react';
import type { CustomHtmlSlideContent } from '../schemas';
import parse, { Element } from 'html-react-parser';
import { TitleSlide } from './TitleSlide';
import { MetricGridSlide } from './MetricGridSlide';
import { StandaloneChart } from './ChartFocusSlide';
import { StandardContentSlide } from './StandardContentSlide';
import { SplitMediaSlide } from './SplitMediaSlide';
import { StandaloneDiagram } from './DiagramFocusSlide';
import { CodeBlockSlide } from './CodeBlockSlide';

interface Props {
  content: CustomHtmlSlideContent;
}

export const CustomHtmlSlide: React.FC<Props> = ({ content }) => {
  const options = {
    replace: (domNode: any) => {
      if (domNode instanceof Element && domNode.attribs) {
        if (domNode.name === 'deqra-title-slide') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedContent = JSON.parse(dataAttr);
              return <TitleSlide key={`title-${Math.random()}`} content={parsedContent} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-title-slide", e);
            }
          }
        }
        if (domNode.name === 'deqra-metric-grid') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedContent = JSON.parse(dataAttr);
              return <MetricGridSlide key={`metric-${Math.random()}`} content={parsedContent} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-metric-grid", e);
            }
          }
        }
        if (domNode.name === 'deqra-chart') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedSpec = JSON.parse(dataAttr);
              return <StandaloneChart key={`chart-${Math.random()}`} chartSpec={parsedSpec.chartSpec || parsedSpec} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-chart", e);
            }
          }
        }
        if (domNode.name === 'deqra-standard-content') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedContent = JSON.parse(dataAttr);
              return <StandardContentSlide key={`std-${Math.random()}`} content={parsedContent} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-standard-content", e);
            }
          }
        }
        if (domNode.name === 'deqra-split-media') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedContent = JSON.parse(dataAttr);
              return <SplitMediaSlide key={`split-${Math.random()}`} content={parsedContent} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-split-media", e);
            }
          }
        }
        if (domNode.name === 'deqra-diagram') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedSpec = JSON.parse(dataAttr);
              return <StandaloneDiagram key={`diag-${Math.random()}`} diagramSpec={parsedSpec.diagramSpec || parsedSpec} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-diagram", e);
            }
          }
        }
        if (domNode.name === 'deqra-code-block') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedContent = JSON.parse(dataAttr);
              return <CodeBlockSlide key={`code-${Math.random()}`} content={parsedContent} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-code-block", e);
            }
          }
        }
      }
    }
  };

  return (
    <div 
      className="custom-html-slide"
      style={{ width: '100%', height: '100%', boxSizing: 'border-box', overflow: 'hidden' }}
    >
      {parse(content.html, options)}
    </div>
  );
};
