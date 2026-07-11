import type { CSSProperties } from 'react';

export interface DeckMetadata {
  theme?: string;
  aspectRatio?: string;
  author?: string;
  [key: string]: any;
}

export interface FooterData {
  icon?: string;
  iconColor?: string;
  iconBg?: string;
  textLeft?: TextContent;
  textRight?: TextContent;
  dividerStyle?: {
    thickness?: string;
    color?: string;
    style?: 'solid' | 'dashed' | 'dotted';
  };
  containerStyle?: CSSProperties;
  contentStyle?: CSSProperties;
}

export interface DeckData {
  deckTitle: TextContent;
  metadata?: DeckMetadata;
  footer?: FooterData;
  globalBackground?: SlideData[];
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

export type AtomicComponentLayout =
  | 'text_block'
  | 'icon'
  | 'image'
  | 'badge'
  | 'stepper'
  | 'avatar'
  | 'code_snippet'
  | 'atomic_chart'
  | 'divider'
  | 'button'
  | 'progress_bar'
  | 'stat'
  | 'qr_code'
  | 'alert'
  | 'checkbox'
  | 'radio'
  | 'list_item';

export interface SlideData {
  id?: string;
  layout: 
    | 'feature_banner'
    | 'numbered_timeline'
    | 'data_grid'
    | 'split_text_media'
    | 'code_walkthrough'
    | 'orbit_diagram'
    | 'status_quo_comparison'
    | 'detailed_comparison'
    | 'badge_group'
    | 'task_list'
    | 'quadrant_matrix'
    | 'pain_point_grid'
    | 'process_breakdown'
    | 'step_slide'
    | 'problem_statement'
    | 'chart_focus'
    | 'feature_cards'
    | 'card'
    | 'portrait_hero'
    | 'flex'
    | 'grid'
    | 'highlight_card'
    // atomic
    | AtomicComponentLayout
    | string;
  speakerNotes?: string;
  hideFooter?: boolean;
  styleOverrides?: StyleOverrides;
  footerOverrides?: Partial<FooterData>;
  content: any;
}

export interface CustomHtmlSlideContent {
  html: string;
}

// Shared types
export type ImageContent = string | { url: string; alt?: string; style?: CSSProperties };
export type TextContent = string | { 
  text: string; 
  style?: CSSProperties;
  icon?: string;
  iconPosition?: 'left' | 'right';
};

// Layout-specific content schemas

export interface CodeAnnotation {
  id: string;
  lineRange: [number, number]; // [startLine, endLine] (1-indexed)
  title?: TextContent;
  description: string;
}

export interface CodeWalkthroughSlideContent {
  title?: TextContent;
  subtitle?: TextContent;
  code: string;
  language: string;
  filename?: string;
  annotations?: CodeAnnotation[];
  activeAnnotationId?: string;
  layout?: 'row' | 'row-reverse' | 'column' | 'column-reverse'; // Controls the orientation of the IDE and sidebar
}

export interface TitleBadge {
  text: TextContent;
  icon?: string;
  iconSize?: string;
  background?: string;
  textColor?: string;
  border?: string;
  style?: CSSProperties;
}

export interface TitleSlideContent {
  title: TextContent;
  subtitle?: TextContent;
  tagline?: TextContent;
  footer?: TextContent;
  image?: ImageContent;
  backgroundImage?: string;
  backgroundElements?: CSSProperties[];
  topLeftArea?: TextContent[];
  topRightArea?: TextContent[];
  bottomLeftArea?: TextContent[];
  bottomRightArea?: (TextContent | TitleBadge)[]; 
  centerBadges?: TitleBadge[];
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
    series?: { dataKey: string; color?: string; name?: TextContent; stackId?: string; type?: 'bar' | 'line' | 'area' }[];
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

export interface DataGridColumn {
  key: string;
  header: TextContent;
  align?: 'left' | 'center' | 'right';
  width?: string;
  isNumeric?: boolean;
}

export interface DataGridRow {
  id: string;
  cells: Record<string, TextContent>;
  isHighlighted?: boolean;
}

export interface DataGridSlideContent {
  title?: TextContent;
  subtitle?: TextContent;
  columns: DataGridColumn[];
  rows: DataGridRow[];
  styleOptions?: {
    headerBg?: string;
    headerColor?: string;
    rowHoverBg?: string;
    striped?: boolean;
    compact?: boolean;
  };
}

export interface TeamMember {
  id: string;
  name: TextContent;
  role: TextContent;
  bio?: TextContent;
  imageUrl?: string;
  socials?: {
    linkedin?: string;
    twitter?: string;
    github?: string;
    email?: string;
  };
}

export interface TeamProfileSlideContent {
  title?: TextContent;
  subtitle?: TextContent;
  members: TeamMember[];
  layout?: 'grid' | 'carousel';
  gridColumns?: number; // Force a specific number of columns (e.g., 2 for portrait)
}

export interface BentoBoxItem {
  id: string;
  colSpan?: number; // number of columns to span (default 1)
  rowSpan?: number; // number of rows to span (default 1)
  title?: TextContent;
  description?: TextContent;
  metric?: string;
  iconName?: string;
  imageUrl?: string;
  styleOptions?: {
    background?: string;
    color?: string;
    isGlassmorphic?: boolean;
  };
}

export interface BentoBoxSlideContent {
  title?: TextContent;
  subtitle?: TextContent;
  gridColumns?: number; // default 4
  portraitGridColumns?: number; // default 1
  items: BentoBoxItem[];
}

export interface ArchitectureSlideContent {
  title?: TextContent;
  subtitle?: TextContent;
  description?: TextContent;
  descriptionPosition?: 'right' | 'left' | 'top' | 'bottom'; // Defaults to 'right'
  mermaidCode: string;
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

export interface TextBoxProcessSlideContent {
  title?: TextContent;
  subtitle?: TextContent;
  steps: {
    title: TextContent;
    description: (TextContent | TextContent[])[]; // Can be an array of paragraphs
    color?: string;
    stepLabel?: string; // e.g., "01"
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


export interface FeatureSplitSlideContent {
  separatorStyle?: CSSProperties; // e.g. { borderLeft: '2px solid #ccc' }
  leftColumn: {
    heading: TextContent;
    icon?: string;
    iconColor?: string; // Custom color for the column header icon
    iconBg?: string;    // Custom background for the column header icon
    items: {
      title: TextContent;
      description?: TextContent;
      icon?: string;
      iconColor?: string; // Custom color for the list item icon
      iconBg?: string;    // Custom background for the list item icon
    }[];
  };
  rightColumn: {
    heading: TextContent;
    icon?: string;
    iconColor?: string; // Custom color for the column header icon
    iconBg?: string;    // Custom background for the column header icon
    items: {
      text: TextContent;
      icon?: string;
      color?: string; // Determines the icon background color in the card
      cardStyle?: CSSProperties; // Custom styling for the individual card (e.g. bg, border)
    }[];
  };
}

export interface TaskListSlideContent {
  title?: TextContent;
  tasks: {
    title: TextContent;
    subtitle?: TextContent;
    subtitleColor?: string;
    badgeText?: TextContent;
    badgeColor?: string;
    badgeBg?: string;
    checked?: boolean;
    checkedColor?: string;
  }[];
}

export interface ComparisonSlideContent {
  title?: TextContent;
  subtitle?: TextContent;
  leftColumn: {
    heading: TextContent;
    items: {
      title: TextContent;
      description?: TextContent;
      icon?: string;
      color?: string;
    }[];
  };
  rightColumn: {
    heading: TextContent;
    items: {
      title: TextContent;
      description?: TextContent;
      icon?: string;
      color?: string;
    }[];
  };
}

export interface FocalMetricSlideContent {
  variant?: 'centered' | 'left-aligned' | 'card' | 'split' | 'gradient';
  title?: TextContent;
  subtitle?: TextContent;
  focalMetric: {
    value: TextContent;
    label: TextContent;
    color?: string;
  };
  description: TextContent;
  secondaryMetrics?: {
    value: TextContent;
    label: TextContent;
  }[];
}

export interface MediaAnnotatorSlideContent {
  title?: TextContent;
  subtitle?: TextContent;
  mediaUrl?: string;
  imagePosition?: 'left' | 'right';
  containerStyle?: CSSProperties;
  contentStyle?: CSSProperties;
  lineStyle?: {
    thickness?: string;
    color?: string;
    style?: 'solid' | 'dashed' | 'dotted';
  };
  steps: {
    title: TextContent;
    description?: TextContent;
    color?: string;
    icon?: string;
    iconColor?: string;
    iconBg?: string;
    nodeScale?: number;
  }[];
}

export interface MediaColumnGridSlideContent {
  title?: TextContent;
  subtitle?: TextContent;
  columns: {
    heading: TextContent;
    bullets?: TextContent[];
    mediaUrl?: string;
    icon?: string;
    color?: string;
  }[];
}

export interface NumberedTimelineSlideContent {
  title?: TextContent;
  subtitle?: TextContent;
  steps?: {
    title: TextContent;
    description?: TextContent;
    number: string;
    color?: string;
    icon?: string;
    subtitle?: TextContent;
    nodeScale?: number;
  }[];
  timelines?: {
    title?: TextContent;
    subtitle?: TextContent;
    steps: {
      title: TextContent;
      description?: TextContent;
      number: string;
      color?: string;
      icon?: string;
      subtitle?: TextContent;
      nodeScale?: number;
    }[];
  }[];
}

export interface PortraitHeroSlideContent {
  title?: TextContent;
  subtitle?: TextContent;
  eyebrow?: TextContent;
  mediaUrls: string[];
  background?: string;
  mediaLayout?: 'row' | 'stack' | 'grid' | 'collage';
  imageFit?: 'cover' | 'contain' | 'fill';
}

export interface CalloutBoxItem {
  text: TextContent;
  icon?: string;
  iconColor?: string;
  iconSize?: number;
  iconPosition?: 'left' | 'right' | 'top' | 'bottom';
  boxBg?: string;
  boxTextColor?: string;
  boxBorderRadius?: string;
  boxPadding?: string;
  boxShadow?: string;
}

export interface CalloutBoxSlideContent {
  callouts: CalloutBoxItem[];
}

export interface PainPointGridSlideContent {
  title: TextContent;
  subtitle?: TextContent;
  columns?: number;
  cards: {
    title: TextContent;
    description: TextContent;
    icon?: string;
    iconColor?: string;
    metric?: TextContent;
    cardStyle?: CSSProperties;
  }[];
}

export interface ProblemStatementSlideContent {
  statement: TextContent;
  metrics?: {
    value: TextContent;
    label: TextContent;
  }[];
  backgroundElements?: CSSProperties[];
}

export interface StatusQuoComparisonSlideContent {
  title: TextContent;
  subtitle?: TextContent;
  currentBox: {
    label: TextContent;
    points: TextContent[];
    style?: CSSProperties;
  };
  impactBox: {
    label: TextContent;
    points: TextContent[];
    style?: CSSProperties;
  };
}

export interface FeatureCardSlideContent {
  icon?: string;
  iconColor?: string;
  iconBg?: string;
  iconBorderColor?: string;
  title: TextContent;
  subtitle?: TextContent;
  description: string;
  containerStyle?: CSSProperties;
}

export interface OrbitDiagramSlideContent {
  centerIcon?: string;
  centerTitle?: TextContent;
  centerSubtitle?: TextContent;
  themeColor?: string;
  size?: string;
  rings?: {
    size: string;
    border: string;
  }[];
  dots?: {
    size: string;
    color?: string;
    top?: string;
    right?: string;
    bottom?: string;
    left?: string;
  }[];
  showCrossLines?: boolean;
  crossLineColor?: string;
  containerStyle?: CSSProperties;
}

export interface MetricRowSlideContent {
  metrics: {
    icon?: string;
    iconColor?: string;
    title: TextContent;
    subtitle?: TextContent;
  }[];
  metricLayoutStyle?: CSSProperties;
  textLayout?: 'title-top' | 'subtitle-top';
  containerStyle?: CSSProperties;
}

export interface DetailedComparisonCardContent {
  badge?: {
    text: TextContent;
    backgroundColor?: string;
    color?: string;
  };
  headerIcon?: string;
  headerIconColor?: string;
  headerIconBackgroundColor?: string;
  headerTitle: TextContent;
  headerSubtitle?: TextContent;
  headerRightText?: TextContent;
  headerRightValue?: TextContent;
  items: {
    icon: string;
    iconColor?: string;
    title: TextContent;
    description: TextContent;
  }[];
}

export interface DetailedComparisonSlideContent {
  title: TextContent;
  subtitle?: TextContent;
  leftCard: DetailedComparisonCardContent;
  rightCard: DetailedComparisonCardContent;
}

export interface ProcessBreakdownSlideContent {
  title: TextContent;
  subtitle?: TextContent;
  steps: {
    label: TextContent;
    description?: TextContent;
    icon?: string;
    isBottleneck?: boolean;
  }[];
}

export interface HeroTitleSlideContent {
  title: TextContent;
  subtitle?: TextContent;
  alignment?: 'left' | 'center' | 'right';
  containerStyle?: React.CSSProperties;
}

export interface BrandLogoSlideContent {
  image: ImageContent;
  containerStyle?: React.CSSProperties;
}

export interface BadgeItem {
  text: TextContent;
  style?: React.CSSProperties;
  icon?: string;
  iconSize?: string;
}

export interface BadgeGroupSlideContent {
  badges: (string | BadgeItem)[];
  alignment?: 'left' | 'center' | 'right' | 'space-between';
  layout?: 'row' | 'column';
  gap?: string;
  containerStyle?: React.CSSProperties;
}

export interface TextBlockSlideContent {
  blocks: TextContent[];
  alignment?: 'left' | 'center' | 'right';
  gap?: string;
  containerStyle?: React.CSSProperties;
}

export interface HighlightCardSlideContent {
  category?: TextContent;
  value: TextContent;
  description?: TextContent;
  icon?: string;
  iconColor?: string;
  iconBg?: string;
  badges?: TextContent[];
  cardStyle?: React.CSSProperties;
}

export interface CardSlideContent {
  children?: SlideData[];
  containerStyle?: React.CSSProperties;
}

export interface ListItem {
  text: TextContent;
  icon?: string;
  iconColor?: string;
}

export interface ListSlideContent {
  items: ListItem[];
  gap?: string;
  containerStyle?: React.CSSProperties;
}

export interface GridSlideContent {
  children?: SlideData[];
  columns?: string;
  gap?: string;
  containerStyle?: React.CSSProperties;
}

export interface IconSlideContent {
  name: string;
  size?: string | number;
  color?: string;
  containerStyle?: React.CSSProperties;
}

export interface ImageSlideContent {
  src: string;
  alt?: string;
  objectFit?: 'contain' | 'cover' | 'fill' | 'none' | 'scale-down';
  containerStyle?: React.CSSProperties;
}

export interface FlexSlideContent {
  children?: SlideData[];
  direction?: 'row' | 'column';
  align?: string;
  justify?: string;
  gap?: string;
  wrap?: boolean;
  containerStyle?: React.CSSProperties;
}

export interface DividerSlideContent {
  orientation?: 'horizontal' | 'vertical';
  thickness?: string;
  color?: string;
  margin?: string;
}

export interface CodeBlockSlideContent {
  code: string;
  language?: string;
  showLineNumbers?: boolean;
  containerStyle?: React.CSSProperties;
}

export interface QuoteSlideContent {
  quote: string;
  author?: string;
  role?: string;
  containerStyle?: React.CSSProperties;
}

export interface AvatarSlideContent {
  src?: string;
  name?: TextContent;
  alt?: string;
  size?: string;
  border?: string;
  fallbackColor?: string;
  fallbackTextColor?: string;
  containerStyle?: React.CSSProperties;
}

export interface ButtonSlideContent {
  text: TextContent;
  variant?: 'primary' | 'secondary' | 'outline' | 'ghost';
  icon?: string;
  containerStyle?: React.CSSProperties;
}

export interface StepSlideContent {
  number?: string;
  title?: TextContent;
  description?: string;
  icon?: string;
  status?: 'active' | 'completed' | 'pending';
  containerStyle?: React.CSSProperties;
}

export interface ConnectorSlideContent {
  direction?: 'horizontal' | 'vertical';
  style?: 'solid' | 'dashed' | 'dotted';
  color?: string;
  length?: string;
}
export interface QuadrantMatrixSlideContent {
  title?: TextContent;
  subtitle?: TextContent;
  targetBadge?: {
    text: TextContent;
    highlightText: TextContent;
    icon?: string;
  };
  cardTitle: TextContent;
  legendItems: {
    color: string;
    text: TextContent;
    isHighlight?: boolean;
  }[];
  xAxisLabel: string;
  yAxisLabel: string;
  quadrants: {
    topLeft: { title: TextContent; subtitle: TextContent; color?: string; subtitleColor?: string };
    topRight: { title: TextContent; subtitle: TextContent; color?: string; subtitleColor?: string };
    bottomLeft: { title: TextContent; subtitle: TextContent; color?: string; subtitleColor?: string };
    bottomRight: { title: TextContent; subtitle: TextContent; color?: string; subtitleColor?: string };
  };
  bubbles: {
    id: string;
    title: TextContent;
    subtitle: TextContent;
    color: string;
    top: string;
    left: string;
    size?: string;
    isHighlight?: boolean;
    badge?: string;
  }[];
}

export type SlideContent = 
  | { layout: 'quadrant_matrix'; content: QuadrantMatrixSlideContent }
  | { layout: 'title'; content: TitleSlideContent }
  | { layout: 'metric_grid'; content: MetricGridSlideContent }
  | { layout: 'standard'; content: StandardContentSlideContent }
  | { layout: 'split_media'; content: SplitMediaSlideContent }
  | { layout: 'chart_focus'; content: ChartFocusSlideContent }
  | { layout: 'diagram_focus'; content: DiagramFocusSlideContent }
  | { layout: 'timeline'; content: TimelineSlideContent }
  | { layout: 'process_flow'; content: ProcessFlowSlideContent }
  | { layout: 'custom_html'; content: CustomHtmlSlideContent }
  | { layout: 'alternating_flow'; content: AlternatingFlowSlideContent }
  | { layout: 'alternating_ring_flow'; content: AlternatingRingFlowSlideContent }
  | { layout: 'serpentine_flow'; content: SerpentineFlowSlideContent }
  | { layout: 'chevron_process'; content: ChevronProcessSlideContent }
  | { layout: 'arrow_timeline'; content: ArrowTimelineSlideContent }
  | { layout: 'interlocking_triangles'; content: InterlockingTrianglesSlideContent }
  | { layout: 'hex_timeline'; content: HexagonTimelineSlideContent }
  | { layout: 'node_branch_timeline'; content: NodeBranchTimelineSlideContent }
  | { layout: 'text_box_process'; content: TextBoxProcessSlideContent }
  | { layout: 'comparison'; content: ComparisonSlideContent }
  | { layout: 'focal_metric'; content: FocalMetricSlideContent }
  | { layout: 'media_annotator'; content: MediaAnnotatorSlideContent }
  | { layout: 'media_column_grid'; content: MediaColumnGridSlideContent }
  | { layout: 'card'; content: CardSlideContent }
  | { layout: 'list'; content: ListSlideContent }
  | { layout: 'grid'; content: GridSlideContent }
  | { layout: 'icon'; content: IconSlideContent }
  | { layout: 'image'; content: ImageSlideContent }
  | { layout: 'flex'; content: FlexSlideContent }
  | { layout: 'divider'; content: DividerSlideContent }
  | { layout: 'code_block'; content: CodeBlockSlideContent }
  | { layout: 'quote'; content: QuoteSlideContent }
  | { layout: 'avatar'; content: AvatarSlideContent }
  | { layout: 'button'; content: ButtonSlideContent }
  | { layout: 'step'; content: StepSlideContent }
  | { layout: 'connector'; content: ConnectorSlideContent }
  | { layout: 'numbered_timeline'; content: NumberedTimelineSlideContent }
  | { layout: 'portrait_hero'; content: PortraitHeroSlideContent }
  | { layout: 'callout_box'; content: CalloutBoxSlideContent }
  | { layout: 'feature_split'; content: FeatureSplitSlideContent }
  | { layout: 'task_list'; content: TaskListSlideContent }
  | { layout: 'feature_banner'; content: FeatureBannerSlideContent }
  | { layout: 'testimonial'; content: TestimonialSlideContent }
  | { layout: 'problem_split'; content: ProblemSplitSlideContent }
  | { layout: 'pain_point_grid'; content: PainPointGridSlideContent }
  | { layout: 'problem_statement'; content: ProblemStatementSlideContent }
  | { layout: 'status_quo_comparison'; content: StatusQuoComparisonSlideContent }
  | { layout: 'detailed_comparison'; content: DetailedComparisonSlideContent }
  | { layout: 'highlight_card'; content: HighlightCardSlideContent }
  | { layout: 'hero_title'; content: HeroTitleSlideContent }
  | { layout: 'brand_logo'; content: BrandLogoSlideContent }
  | { layout: 'badge_group'; content: BadgeGroupSlideContent }
  | { layout: 'text_block'; content: TextBlockSlideContent }
  | { layout: 'process_breakdown'; content: ProcessBreakdownSlideContent };

export interface Presentation {
  id?: string;
  title?: string;
  slides: {
    id: string;
    layout: SlideContent['layout'];
    content: any; // Using any here to avoid strict unions in the JSON but typed in renderer
    metadata?: any;
  }[];
  theme?: {
    primaryColor?: string;
    fontFamily?: string;
    slideBackground?: string;
    textColor?: string;
  };
}

export interface FeatureBannerSlideContent {
  title?: TextContent;
  subtitle?: TextContent;
  icon?: string;
  iconBg?: string;
  iconColor?: string;
  iconPosition?: 'left' | 'right';
  iconAlignment?: 'top' | 'center' | 'bottom';
  cardBg?: string;
  cardBorderColor?: string;
  dividerStyle?: {
    thickness?: string;
    color?: string;
    style?: 'solid' | 'dashed' | 'dotted';
  };
  features: {
    title: TextContent;
    description?: TextContent;
    icon?: string;
    iconColor?: string;
    iconBg?: string;
  }[];
}

export interface PricingFeature {
  text: TextContent;
  icon?: string;       // e.g., 'Check', 'X'
  iconColor?: string;
  iconBg?: string;
  isExcluded?: boolean; // if true and no icon provided, defaults to 'X' and gray text
}

export interface PricingTier {
  name: TextContent;
  price: TextContent;
  period?: TextContent; // e.g., '/mo'
  description?: TextContent;
  features: PricingFeature[];
  buttonText?: TextContent;
  isHighlighted?: boolean;
  highlightText?: TextContent; // e.g., 'Most Popular'
  buttonStyle?: {
    background?: string;
    color?: string;
    border?: string;
  };
  cardStyle?: {
    background?: string;
    borderColor?: string;
  };
}

export interface PricingTierSlideContent {
  title?: TextContent;
  subtitle?: TextContent;
  tiers: PricingTier[];
}

export interface TestimonialSlideContent {
  quote: TextContent;
  author: TextContent;
  role?: TextContent;
  avatar?: ImageContent;
  companyLogo?: ImageContent;
  companyName?: TextContent;
  rating?: number; // 1-5
  layoutVariant?: 'centered' | 'left-aligned' | 'split';
  accentColor?: string;
}

export interface ProblemSplitSlideContent {
  title: TextContent;
  subtitle?: TextContent;
  leftColumnWidth?: string;
  columnGap?: string;
  leftGap?: string;
  rightGap?: string;
  backgroundElements?: CSSProperties[];
  leftCards: {
    category: TextContent;
    value: TextContent;
    description: TextContent;
    badges?: TextContent[];
    icon?: string;
    iconColor?: string;
    cardStyle?: CSSProperties;
    categoryStyle?: CSSProperties;
    valueStyle?: CSSProperties;
  }[];
  rightItems: {
    title: TextContent;
    description: TextContent;
    icon?: string;
    iconColor?: string;
    iconBg?: string;
    cardStyle?: CSSProperties;
  }[];
  bottomBanner?: {
    label?: TextContent;
    text: TextContent;
    icon?: string;
    iconColor?: string;
    bannerStyle?: CSSProperties;
  };
}

// ==========================================
// ==========================================
// NEW ATOMIC COMPONENTS
// ==========================================

export interface CheckboxSlideContent {
  checked?: boolean;
  label?: TextContent;
  variant?: 'primary' | 'success' | 'warning' | 'error' | 'neutral';
  size?: string;
  containerStyle?: CSSProperties;
}

export interface RadioSlideContent {
  checked?: boolean;
  label?: TextContent;
  variant?: 'primary' | 'success' | 'warning' | 'error' | 'neutral';
  size?: string;
  containerStyle?: CSSProperties;
}

export interface ListItemSlideContent {
  title: TextContent;
  description?: TextContent;
  icon?: string;
  iconColor?: string;
  variant?: 'primary' | 'success' | 'warning' | 'error' | 'neutral';
  containerStyle?: CSSProperties;
}

export interface BadgeSlideContent {
  text: TextContent;
  variant?: 'neutral' | 'success' | 'warning' | 'error' | 'info' | 'primary';
  icon?: string;
  containerStyle?: CSSProperties;
  textStyle?: CSSProperties;
}

export interface ChartSlideContent {
  type: 'bar' | 'line';
  data: any[];
  xAxisKey: string;
  series: {
    key: string;
    color?: string;
    name?: TextContent;
  }[];
  height?: string | number;
  width?: string | number;
  containerStyle?: CSSProperties;
  showLegend?: boolean;
  showGrid?: boolean;
}

export interface StepperSlideContent {
  steps: {
    label: TextContent;
    description?: TextContent;
    icon?: string;
  }[];
  activeIndex: number;
  orientation?: 'horizontal' | 'vertical';
  activeColor?: string;
  inactiveColor?: string;
  containerStyle?: CSSProperties;
}


export interface CodeSnippetSlideContent {
  code: string;
  language?: string;
  showLineNumbers?: boolean;
  containerStyle?: CSSProperties;
}

export interface ProgressBarSlideContent {
  progress: number;
  type?: 'linear' | 'radial';
  label?: TextContent;
  color?: string;
  thickness?: string;
  containerStyle?: CSSProperties;
}

export interface QRCodeSlideContent {
  value: string;
  size?: number;
  fgColor?: string;
  bgColor?: string;
  containerStyle?: CSSProperties;
}

export interface StatSlideContent {
  value: TextContent;
  label?: TextContent;
  trend?: {
    value: TextContent;
    direction: 'up' | 'down' | 'neutral';
  };
  containerStyle?: CSSProperties;
}

export interface AlertSlideContent {
  description: TextContent;
  title?: TextContent;
  variant?: 'info' | 'success' | 'warning' | 'error';
  icon?: string;
  containerStyle?: CSSProperties;
}
