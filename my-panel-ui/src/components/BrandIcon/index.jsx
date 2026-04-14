import React from 'react';

const BrandIcon = ({ type, size = '32px', color }) => {
  const iconStyle = {
    width: size,
    height: size,
    display: 'inline-block',
    verticalAlign: 'middle',
  };

  const icons = {
    'nginx': (
      <svg viewBox="0 0 24 24" style={iconStyle} fill="#009639">
        <path d="M12 2L2 7v10l10 5 10-5V7L12 2zm0 1.5l8.5 4.25v8.5L12 20.5l-8.5-4.25v-8.5L12 3.5zm-5 5.5v2l2 2-2 2v2l3-3 2 2 3-3v-2l-2-2 2-2v-2l-3 3-2-2-3 3z" />
      </svg>
    ),
    'redis': (
      <svg viewBox="0 0 24 24" style={iconStyle} fill="#D82C20">
        <path d="M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm0 18a8 8 0 1 1 8-8 8 8 0 0 1-8 8zm-4-8h8v2H8zm0-3h8v2H8z" />
      </svg>
    ),
    'mysql': (
      <svg viewBox="0 0 24 24" style={iconStyle} fill="#00758F">
        <path d="M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm1 14h-2v-2h2zm0-4h-2V7h2z" />
      </svg>
    ),
    'mongodb': (
      <svg viewBox="0 0 24 24" style={iconStyle} fill="#47A248">
        <path d="M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm-1 14.5V13l-3-1 3-1V7.5l4 4.5-4 4.5z" />
      </svg>
    ),
    'rabbitmq': (
      <svg viewBox="0 0 24 24" style={iconStyle} fill="#FF6600">
        <path d="M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm0 18a8 8 0 1 1 8-8 8 8 0 0 1-8 8zm-4-8h2v2H8zm4 0h2v2h-2z" />
      </svg>
    ),
    'kafka': (
      <svg viewBox="0 0 24 24" style={iconStyle} fill="#000000">
        <path d="M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm1 14h-2V8h2zm-4-4h8v2H9z" />
      </svg>
    ),
    'springboot': (
      <svg viewBox="0 0 24 24" style={iconStyle} fill="#6DB33F">
        <path d="M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm0 18a8 8 0 1 1 8-8 8 8 0 0 1-8 8zm-1-11l-3 3 3 3 3-3-3-3z" />
      </svg>
    ),
    'nodejs': (
      <svg viewBox="0 0 24 24" style={iconStyle} fill="#339933">
        <path d="M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm0 18a8 8 0 1 1 8-8 8 8 0 0 1-8 8zm-2-11h4v2h-2v4h-2z" />
      </svg>
    ),
    'python': (
      <svg viewBox="0 0 24 24" style={iconStyle} fill="#3776AB">
        <path d="M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm0 18a8 8 0 1 1 8-8 8 8 0 0 1-8 8zm-1-11v2h4v2h-4v2h4v2h-6V9z" />
      </svg>
    ),
    'elasticsearch': (
      <svg viewBox="0 0 24 24" style={iconStyle} fill="#005571">
        <path d="M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm0 18a8 8 0 1 1 8-8 8 8 0 0 1-8 8zm-3-11h6v2H9zm0 4h6v2H9z" />
      </svg>
    ),
    'prometheus': (
      <svg viewBox="0 0 24 24" style={iconStyle} fill="#E6522C">
        <path d="M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm0 18a8 8 0 1 1 8-8 8 8 0 0 1-8 8zm-2-11l4 4-4 4V9z" />
      </svg>
    ),
    'grafana': (
      <svg viewBox="0 0 24 24" style={iconStyle} fill="#F46800">
        <path d="M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm0 18a8 8 0 1 1 8-8 8 8 0 0 1-8 8zm-2-11h4v8h-4z" />
      </svg>
    ),
    'kubernetes': (
      <svg viewBox="0 0 24 24" style={iconStyle} fill="#326CE5">
        <path d="M12 2l8.5 4.5V17.5L12 22 3.5 17.5V6.5L12 2zm0 2.5L5.5 7.5v9L12 19.5l6.5-3v-9L12 4.5zM12 8l4 4-4 4-4-4 4-4z" />
      </svg>
    ),
    'docker': (
      <svg viewBox="0 0 24 24" style={iconStyle} fill="#2496ED">
        <path d="M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm-5 8h2v2H7zm4 0h2v2h-2zm4 0h2v2h-2zm-8 4h10v2H7z" />
      </svg>
    ),
    'my-panel': (
      <svg viewBox="0 0 24 24" style={iconStyle} fill="#1890ff">
        <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5" />
      </svg>
    ),
    'server': (
      <svg viewBox="0 0 24 24" style={iconStyle} fill="#8c8c8c">
        <path d="M4 4h16v4H4zm0 6h16v4H4zm0 6h16v4H4zM6 6h2v1H6zm0 6h2v1H6zm0 6h2v1H6z" />
      </svg>
    ),
    'vm': (
      <svg viewBox="0 0 24 24" style={iconStyle} fill="#1890ff">
        <path d="M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm0 18a8 8 0 1 1 8-8 8 8 0 0 1-8 8zm-4-8h2v2H8zm4 0h2v2h-2z" />
      </svg>
    ),
    'container': (
      <svg viewBox="0 0 24 24" style={iconStyle} fill="#13c2c2">
        <path d="M12 2L2 7v10l10 5 10-5V7L12 2zm0 18.5L4 16.5V7.5L12 3l8 4.5v9l-8 4zM6 9h12v6H6z" />
      </svg>
    ),
  };

  const Icon = icons[type];
  if (!Icon) return null;

  return Icon;
};

export const BRAND_ICON_OPTIONS = [
  { value: 'nginx', label: 'Nginx' },
  { value: 'redis', label: 'Redis' },
  { value: 'mysql', label: 'MySQL' },
  { value: 'mongodb', label: 'MongoDB' },
  { value: 'rabbitmq', label: 'RabbitMQ' },
  { value: 'kafka', label: 'Kafka' },
  { value: 'springboot', label: 'SpringBoot' },
  { value: 'nodejs', label: 'Node.js' },
  { value: 'python', label: 'Python' },
  { value: 'elasticsearch', label: 'Elasticsearch' },
  { value: 'prometheus', label: 'Prometheus' },
  { value: 'grafana', label: 'Grafana' },
  { value: 'kubernetes', label: 'Kubernetes' },
  { value: 'docker', label: 'Docker' },
  { value: 'my-panel', label: 'My-Panel' },
  { value: 'server', label: '服务器' },
  { value: 'vm', label: '虚拟机' },
  { value: 'container', label: '容器' },
];

export default BrandIcon;
