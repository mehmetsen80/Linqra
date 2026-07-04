import type { SlideData, ChartFocusSlideContent } from './schemas';

// Helper to normalize strings into TextContent objects
const normalizeTextContent = (value: any) => {
  if (typeof value === 'string') {
    return { text: value };
  }
  return value;
};

export function sanitizeSlide(slide: SlideData): SlideData {
  // Deep clone to avoid mutating the original prop
  const sanitized = JSON.parse(JSON.stringify(slide)) as SlideData;

  // RULE 1: TEXTCONTENT NORMALIZATION
  if (sanitized.content) {
    if ((sanitized.content as any).title) (sanitized.content as any).title = normalizeTextContent((sanitized.content as any).title);
    if ((sanitized.content as any).subtitle) (sanitized.content as any).subtitle = normalizeTextContent((sanitized.content as any).subtitle);
    if ((sanitized.content as any).summary) (sanitized.content as any).summary = normalizeTextContent((sanitized.content as any).summary);
    
    // Also normalize metrics array in MetricGridSlide
    if (sanitized.layout === 'metric_grid' && (sanitized.content as any).metrics) {
      (sanitized.content as any).metrics.forEach((m: any) => {
        if (m.label) m.label = normalizeTextContent(m.label);
        if (m.value) m.value = normalizeTextContent(m.value);
        if (m.trend) m.trend = normalizeTextContent(m.trend);
      });
    }
  }

  // RULE 2: CHART DATA SANITIZATION
  if (sanitized.layout === 'chart' && sanitized.content?.chartSpec) {
    const chartContent = sanitized.content as ChartFocusSlideContent;
    const chartSpec = chartContent.chartSpec;

    // Chart Axis Normalizer (x/y to label/value)
    if (chartSpec.data && Array.isArray(chartSpec.data)) {
      chartSpec.data = chartSpec.data.map(d => {
        // If it looks like a scatter point, leave it alone.
        if (chartSpec.chartType === 'scatter') return d;
        
        // If label is missing but x exists, auto-map
        if (d.label === undefined && d.x !== undefined) {
          d.label = String(d.x);
        }
        // If value is missing but y exists, auto-map
        if (d.value === undefined && d.y !== undefined) {
          d.value = d.y;
        }
        return d;
      });
    }

    const fallbackName = chartSpec.yAxisLabel || chartSpec.xAxisLabel || (typeof sanitized.content.title === 'string' ? sanitized.content.title : (sanitized.content.title as any)?.text) || "Data";

    // Inject meaningful series name if missing
    if (!chartSpec.series || chartSpec.series.length === 0) {
      chartSpec.series = [
        { 
          dataKey: "value", 
          name: fallbackName as string,
          color: chartSpec.primaryColor || "#3b82f6"
        }
      ];
    } else {
      chartSpec.series.forEach((s: any) => {
        if (!s.name) {
          s.name = s.dataKey;
        }
      });
    }

    if (!chartSpec.primaryColor) {
      chartSpec.primaryColor = "#3b82f6";
    }
  }

  // RULE 3: HTML MARKDOWN STRIPPER
  if (sanitized.layout === 'custom_html' && (sanitized.content as any)?.html) {
    let htmlStr = (sanitized.content as any).html;
    if (typeof htmlStr === 'string') {
      htmlStr = htmlStr.replace(/^```html\s*\n/i, '');
      htmlStr = htmlStr.replace(/```\s*$/i, '');
      (sanitized.content as any).html = htmlStr;
    }
  }

  return sanitized;
}
