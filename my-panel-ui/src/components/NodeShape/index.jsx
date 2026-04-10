import React from 'react';

/**
 * NodeShape Component
 * Used for both previewing shapes in the management table and rendering shapes in the diagram.
 */
const NodeShape = ({ 
  shape = 'rectangle', 
  color = '#1890ff', 
  size = 40, 
  strokeWidth = 2,
  style = {},
  selected = false,
  className = '',
  isPreview = false
}) => {
  const bg = `${color}15`; // Light background
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
        return <circle cx="50" cy="50" r="45" fill={bg} stroke={border} strokeWidth={strokeWidth} />;
      
      case 'diamond':
        return <path d="M 50 5 L 95 50 L 50 95 L 5 50 Z" fill={bg} stroke={border} strokeWidth={strokeWidth} />;
      
      case 'cylinder':
        return (
          <g>
            <path 
              d="M 10 20 L 10 80 C 10 95, 90 95, 90 80 L 90 20" 
              fill={bg} 
              stroke={border} 
              strokeWidth={strokeWidth} 
            />
            <ellipse 
              cx="50" cy="20" rx="40" ry="15" 
              fill={`${border}33`} 
              stroke={border} 
              strokeWidth={strokeWidth} 
            />
            {/* Minimalist decorative lines */}
            <path d="M 10 40 C 10 50, 90 50, 90 40" fill="none" stroke={border} strokeWidth={strokeWidth/2} strokeDasharray="4 2" opacity="0.4" />
            <path d="M 10 60 C 10 70, 90 70, 90 60" fill="none" stroke={border} strokeWidth={strokeWidth/2} strokeDasharray="4 2" opacity="0.4" />
          </g>
        );

      case 'triangle':
        return <path d="M 50 10 L 90 90 L 10 90 Z" fill={bg} stroke={border} strokeWidth={strokeWidth} />;

      case 'hexagon':
        return <path d="M 25 10 L 75 10 L 95 50 L 75 90 L 25 90 L 5 50 Z" fill={bg} stroke={border} strokeWidth={strokeWidth} />;

      case 'parallelogram':
        return <path d="M 25 20 L 95 20 L 75 80 L 5 80 Z" fill={bg} stroke={border} strokeWidth={strokeWidth} />;

      case 'cloud':
        return <path d="M 25 45 C 5 45, 5 25, 25 25 C 25 10, 55 10, 70 20 C 90 15, 100 35, 90 50 C 100 65, 75 80, 55 75 C 35 85, 10 75, 25 45" fill={bg} stroke={border} strokeWidth={strokeWidth} />;

      case 'logic-and':
        return <path d="M 10 10 L 50 10 C 80 10, 95 30, 95 50 C 95 70, 80 90, 50 90 L 10 90 Z" fill={bg} stroke={border} strokeWidth={strokeWidth} />;

      case 'logic-or':
        return <path d="M 10 10 C 25 30, 25 70, 10 90 C 50 90, 85 80, 95 50 C 85 20, 50 10, 10 10 Z" fill={bg} stroke={border} strokeWidth={strokeWidth} />;

      case 'logic-not':
        return (
          <g>
            <path d="M 10 15 L 75 50 L 10 85 Z" fill={bg} stroke={border} strokeWidth={strokeWidth} />
            <circle cx="85" cy="50" r="8" fill={bg} stroke={border} strokeWidth={strokeWidth} />
          </g>
        );

      case 'rounded-rectangle':
        return <rect x="5" y="20" width="90" height="60" rx="15" fill={bg} stroke={border} strokeWidth={strokeWidth} />;

      case 'rectangle':
      default:
        return <rect x="5" y="20" width="90" height="60" rx="4" fill={bg} stroke={border} strokeWidth={strokeWidth} />;
    }
  };

  return (
    <div className={`node-shape-container ${className}`} style={baseStyle}>
      <svg 
        width="100%" 
        height="100%" 
        viewBox={viewBox} 
        style={{ overflow: 'visible' }}
        preserveAspectRatio="xMidYMid meet"
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
