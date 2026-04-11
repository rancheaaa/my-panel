import React from 'react';

/**
 * NodeShape Component
 * Used for both previewing shapes in the management table and rendering shapes in the diagram.
 */
const NodeShape = ({ 
  shape = 'rectangle', 
  color = '#1890ff', 
  backgroundColor,
  size = 40, 
  strokeWidth = 2,
  style = {},
  selected = false,
  className = '',
  isPreview = false
}) => {
  const bg = backgroundColor || `${color}15`; // Use provided background or fallback to light version of color
  const border = color;
  const viewBox = "0 0 100 100";
  
  // Base styles for the container
  const baseStyle = {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    width: size,
    height: size,
    transition: 'all 0.3s ease',
    ...style
  };

  const renderSvgShape = () => {
    switch (shape) {
      case 'circle':
        return <circle cx="50" cy="50" r="48" fill={bg} stroke={border} strokeWidth={strokeWidth} />;
      
      case 'diamond':
        return <path d="M 50 2 L 98 50 L 50 98 L 2 50 Z" fill={bg} stroke={border} strokeWidth={strokeWidth} />;
      
      case 'cylinder':
        return (
          <g>
            <path 
              d="M 2 15 L 2 85 C 2 98, 98 98, 98 85 L 98 15" 
              fill={bg} 
              stroke={border} 
              strokeWidth={strokeWidth} 
            />
            <ellipse 
              cx="50" cy="15" rx="48" ry="12" 
              fill={`${border}33`} 
              stroke={border} 
              strokeWidth={strokeWidth} 
            />
            {/* Minimalist decorative lines */}
            <path d="M 2 40 C 2 50, 98 50, 98 40" fill="none" stroke={border} strokeWidth={strokeWidth/2} strokeDasharray="4 2" opacity="0.4" />
            <path d="M 2 65 C 2 75, 98 75, 98 65" fill="none" stroke={border} strokeWidth={strokeWidth/2} strokeDasharray="4 2" opacity="0.4" />
          </g>
        );

      case 'triangle':
        return <path d="M 50 2 L 98 98 L 2 98 Z" fill={bg} stroke={border} strokeWidth={strokeWidth} />;

      case 'hexagon':
        return <path d="M 25 2 L 75 2 L 98 50 L 75 98 L 25 98 L 2 50 Z" fill={bg} stroke={border} strokeWidth={strokeWidth} />;

      case 'parallelogram':
        return <path d="M 25 2 L 98 2 L 75 98 L 2 98 Z" fill={bg} stroke={border} strokeWidth={strokeWidth} />;

      case 'cloud':
        return <path d="M 25 45 C 2 45, 2 25, 25 25 C 25 2, 55 2, 70 20 C 95 10, 98 35, 90 50 C 98 65, 75 98, 55 85 C 35 98, 2 85, 25 45" fill={bg} stroke={border} strokeWidth={strokeWidth} />;

      case 'logic-and':
        return <path d="M 2 2 L 50 2 C 80 2, 98 25, 98 50 C 98 75, 80 98, 50 98 L 2 98 Z" fill={bg} stroke={border} strokeWidth={strokeWidth} />;

      case 'logic-or':
        return <path d="M 2 2 C 25 30, 25 70, 2 98 C 50 98, 85 90, 98 50 C 85 10, 50 2, 2 2 Z" fill={bg} stroke={border} strokeWidth={strokeWidth} />;

      case 'logic-not':
        return (
          <g>
            <path d="M 2 10 L 80 50 L 2 90 Z" fill={bg} stroke={border} strokeWidth={strokeWidth} />
            <circle cx="88" cy="50" r="10" fill={bg} stroke={border} strokeWidth={strokeWidth} />
          </g>
        );

      case 'rounded-rectangle':
        return <rect x="2" y="2" width="96" height="96" rx="15" fill={bg} stroke={border} strokeWidth={strokeWidth} />;

      case 'rectangle':
      default:
        return <rect x="2" y="2" width="96" height="96" rx="4" fill={bg} stroke={border} strokeWidth={strokeWidth} />;
    }
  };

  return (
    <div className={`node-shape-container ${className}`} style={baseStyle}>
      <svg 
        width="100%" 
        height="100%" 
        viewBox={viewBox} 
        style={{ overflow: 'visible' }}
        preserveAspectRatio="none"
      >
        {renderSvgShape()}
      </svg>
    </div>
  );
};

export const COMMON_SHAPES = [
  { value: 'rectangle', label: '矩形' },
  { value: 'rounded-rectangle', label: '圆角矩形' },
  { value: 'circle', label: '圆形' },
  { value: 'diamond', label: '菱形/决策' },
  { value: 'cylinder', label: '圆柱体/数据库' },
  { value: 'triangle', label: '三角形' },
  { value: 'hexagon', label: '六边形' },
  { value: 'parallelogram', label: '平行四边形' },
  { value: 'cloud', label: '云形' },
  { value: 'logic-and', label: '逻辑与 (AND)' },
  { value: 'logic-or', label: '逻辑或 (OR)' },
  { value: 'logic-not', label: '逻辑非 (NOT)' },
];

export default NodeShape;
