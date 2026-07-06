import type { CSSProperties } from 'react';

export interface DeckMetadata {
  theme?: string;
  aspectRatio?: string;
  author?: string;
  [key: string]: any;
}

export interface DeckData {
  deckTitle: string;
  metadata?: DeckMetadata;
  slides: SlideData[];
}

export interface StyleOverrides {
  // Slide-level
  backgroundColor?: string;       // --slide-bg
  textColor?: string;             // --text-main
  textSecondary?: string;         // --text-secondary
  titleColor?: string;            // --text-main (applied as title override)
  subtitleColor?: string;         // --text-secondary
  align?: 'left' | 'center' | 'right'; // shorthand — sets both title and body
  slidePadding?: string;          // --slide-padding  outer padding of the slide container
  contentGap?: string;            // --content-gap    gap between title/subtitle and main body
  // Typography
  fontFamily?: string;            // --font-family
  titleAlign?: 'left' | 'center' | 'right'; // --title-align
  titleFontSize?: string;         // --title-font-size  (e.g. '2rem', 'clamp(1.5rem,5cqi,3rem)')
  titleFontWeight?: string;       // --title-font-weight (e.g. '400', '700', 'bold')
  bodyAlign?: 'left' | 'center' | 'right';  // --body-align
  bodyFontSize?: string;          // --body-font-size
  bodyLineHeight?: string;        // --body-line-height (e.g. '1.4', '1.8')
  letterSpacing?: string;         // --letter-spacing   (e.g. '0.05em', '0.1em')
  // Card / panel
  cardBg?: string;                // --card-bg
  cardBorder?: string;            // --card-border
  cardRadius?: string;            // --card-radius  (e.g. '0px', '8px', '12px')
  cardPadding?: string;           // --card-padding  inner padding of card boxes
  cardGap?: string;               // --card-gap      gap between cards in a grid
  stepConnectorWidth?: string;    // --step-connector-width  arrow width in process_flow
  // Diagram-specific
  diagramNodeBg?: string;         // --diagram-node-bg
  diagramNodeBorder?: string;     // --diagram-node-border
  diagramNodeText?: string;       // --diagram-node-text
  diagramEdge?: string;           // --diagram-edge
  diagramLabelBg?: string;        // --diagram-label-bg
  diagramNodeRadius?: string;     // --diagram-node-radius  (SVG rx, e.g. '0', '4', '8')
  slideNumberPosition?: 'bottom-right' | 'bottom-left' | 'top-right' | 'top-left' | 'hidden';
  [key: string]: any;
}

export interface SlideData {
  id: string;
  layout: string;
  speakerNotes?: string;
  styleOverrides?: StyleOverrides;
  content: any;
}

export interface CustomHtmlSlideContent {
  html: string;
}

// Shared types
export type ImageContent = string | { url: string; alt?: string; style?: CSSProperties };
export type TextContent = string | { text: string; style?: CSSProperties };

// Layout-specific content schemas
export interface TitleSlideContent {
  title: TextContent;
  subtitle?: TextContent;
  tagline?: TextContent;
  footer?: TextContent;
  image?: ImageContent;
  backgroundImage?: string;
}

export interface StandardContentSlideContent {
  title: TextContent;
  image?: ImageContent;
  bodyText?: TextContent;
  listItems?: (TextContent | {
    text: TextContent;
    subItems?: TextContent[];
    style?: CSSProperties;      // pass-through CSS for this list item
  })[];
}

export interface SplitMediaSlideContent {
  title: TextContent;
  image?: ImageContent;
  layoutDirection?: 'text_left' | 'text_right';
  text?: {
    heading?: TextContent;
    body?: TextContent;
    bullets?: TextContent[];
  };
  media?: {
    type: 'image' | 'chart' | 'diagram';
    source: string;
    caption?: TextContent;
  };
}

export interface MetricGridSlideContent {
  title: TextContent;
  subtitle?: TextContent;
  image?: ImageContent;
  metrics: {
    value: TextContent;
    label: TextContent;
    trend?: TextContent;
    icon?: string;
    cardStyle?: CSSProperties;  // pass-through CSS for this metric card
  }[];
}

export interface ChartFocusSlideContent {
  title: TextContent;
  summary?: TextContent;
  chartSpec: {
    chartType: 'bar' | 'line' | 'pie' | 'donut' | 'area' | 'radar' | 'horizontal_bar' | 'stacked_bar' | 'composed' | 'funnel' | 'scatter' | 'radial_bar' | 'treemap';
    primaryColor?: string;
    xAxisLabel?: string;
    yAxisLabel?: string;
    showLegend?: boolean;
    legendPayload?: { value: string; color: string; type?: 'line' | 'square' | 'rect' | 'circle' | 'cross' | 'diamond' | 'star' | 'triangle' | 'wye' }[];
    xAxisStyle?: CSSProperties;
    yAxisStyle?: CSSProperties;
    legendStyle?: CSSProperties;
    tooltipStyle?: CSSProperties;
    data: ({ label: string; value?: number; color?: string; labelStyle?: CSSProperties } & Record<string, any>)[];
    series?: { dataKey: string; color?: string; name?: string; stackId?: string; type?: 'bar' | 'line' | 'area' }[];
  };
}

export interface DiagramFocusSlideContent {
  title: TextContent;
  diagramSpec: {
    type: 'flowchart' | 'sequence' | 'network';
    nodes: { id: string; label: string }[];
    edges: {
      source: string;
      target: string;
      label?: string;
      arrowType?: 'classic' | 'thick' | 'dotted' | 'line' | 'bidirectional';
    }[];
    edgeLabelStyle?: {
      background?: string;
      color?: string;
      borderRadius?: string;
      padding?: string;           // e.g. '2px 8px', '4px 12px'
    };
  };
}

export interface TimelineSlideContent {
  title: TextContent;
  image?: ImageContent;
  subtitle?: TextContent;
  events: {
    date: TextContent;
    title: TextContent;
    description?: TextContent;
    color?: string;      // accent color for dot, bar, border, date text
    fontSize?: number;   // SVG font size scale factor (default 1.0)
    cardBg?: string;     // override card background color (SVG fill)
    titleColor?: string; // override event title text color
    descColor?: string;  // override description text color
    icon?: string;
  }[];
}

export interface HexagonTimelineSlideContent {
  title?: TextContent;
  subtitle?: TextContent;
  nodeSize?: number;
  arrowType?: 'default' | 'solid' | 'circle' | 'none'; // Optional arrowhead style
  steps: {
    title: TextContent;
    description?: TextContent;
    color?: string;
    icon?: string;
    stepLabel?: string; // e.g. "STEP 01"
  }[];
}

export interface NodeBranchTimelineSlideContent {
  title?: TextContent;
  subtitle?: TextContent;
  nodeSize?: number; // Radius of the nodes
  arrowType?: 'default' | 'solid' | 'circle' | 'none'; 
  steps: {
    title: TextContent;
    description?: TextContent;
    color?: string;
    icon?: string;
    stepLabel?: string; // e.g. "STEP 01"
  }[];
}

export interface ProcessFlowSlideContent {
  title: TextContent;
  image?: ImageContent;
  subtitle?: TextContent;
  direction?: 'horizontal' | 'vertical';  // default: 'horizontal'
  steps: {
    title: TextContent;
    description?: TextContent;
    points?: TextContent[];    // bullet points to fill the card's vertical space
    color?: string;       // accent color for this step
    icon?: string;
    cardStyle?: CSSProperties;  // pass-through: any CSS to apply to the card div
    arrowStyle?: CSSProperties; // pass-through: any CSS to apply to the arrow following this card
    arrowType?: 'classic' | 'chevron' | 'wedge' | 'line' | 'block' | 'curved' | 'looping' | '3d_ribbon_curve';
  }[];
}

export interface AlternatingFlowSlideContent {
  title?: TextContent;
  subtitle?: TextContent;
  steps: {
    title: TextContent;
    description?: TextContent;
    color?: string;
    icon?: string;
    nodeStyle?: CSSProperties;
  }[];
}

export interface AlternatingRingFlowSlideContent {
  title: TextContent;
  subtitle?: TextContent;
  startDirection?: 'over' | 'under';
  steps: {
    title: TextContent;
    description?: TextContent;
    color?: string;
    icon?: string;
  }[];
}

export interface SerpentineFlowSlideContent {
  title?: TextContent;
  subtitle?: TextContent;
  itemsPerRow?: number; // Default: 4
  steps: {
    title: TextContent;
    description?: TextContent;
    color?: string;
    icon?: string;
    nodeStyle?: CSSProperties;
  }[];
}

export interface ChevronProcessSlideContent {
  title?: TextContent;
  subtitle?: TextContent;
  cornerRadius?: number; // Global border radius for the interlocking chevrons
  steps: {
    title: TextContent;
    description?: TextContent;
    color?: string;
    icon?: string;
    widthRatio?: number; // Optional relative width (e.g. 2 means twice as wide as ratio 1)
  }[];
}

export interface ArrowTimelineSlideContent {
  title?: TextContent;
  subtitle?: TextContent;
  timelineColor?: string; // Color of the central arrow timeline
  events: {
    date: TextContent;
    title: TextContent;
    description?: TextContent;
    color?: string;
    icon?: string;
  }[];
}

export interface InterlockingTrianglesSlideContent {
  title?: TextContent;
  subtitle?: TextContent;
  flatStyle?: boolean; // If true, disables the 3D folded-paper split-shading
  outlineCircle?: boolean; // If true, the circle has a white fill and a colored border
  softCircle?: boolean; // If true, the circle has a soft pastel fill (tinted with the base color)
  gapWidth?: number; // Customizes the thickness of the white divider between triangle groups
  steps: {
    title: TextContent;
    description?: TextContent;
    color?: string;
    nodeBgColor?: string; // Custom background color for this specific circular node
    nodeRadius?: number; // Custom size for this specific circular node
    hideDivider?: boolean; // If true, removes the white stroke separating this group
    icon?: string;
  }[];
}

export interface CodeBlockContent {
  title?: TextContent;
  subtitle?: TextContent;
  code: string;
  language: string;
  theme?: 'dark' | 'light';
  showLineNumbers?: boolean;
  containerStyle?: CSSProperties;
}

