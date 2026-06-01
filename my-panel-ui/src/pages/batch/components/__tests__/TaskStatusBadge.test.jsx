import React from 'react';
import { render } from '@testing-library/react';
import '@testing-library/jest-dom';
import TaskStatusBadge from '../TaskStatusBadge';
import { TASK_STATUS, STATUS_COLOR } from '../../constants';

describe('TaskStatusBadge', () => {
  test('renders READY status with default color', () => {
    const { container } = render(<TaskStatusBadge status={TASK_STATUS.READY} />);
    expect(container.textContent).toContain('READY');
    expect(container.querySelector('.ant-tag')).toHaveClass('ant-tag-default');
  });

  test('renders RUNNING status with success color', () => {
    const { container } = render(<TaskStatusBadge status={TASK_STATUS.RUNNING} />);
    expect(container.textContent).toContain('RUNNING');
    expect(container.querySelector('.ant-tag')).toHaveClass('ant-tag-success');
  });

  test('renders PAUSED status with warning color', () => {
    const { container } = render(<TaskStatusBadge status={TASK_STATUS.PAUSED} />);
    expect(container.textContent).toContain('PAUSED');
    expect(container.querySelector('.ant-tag')).toHaveClass('ant-tag-warning');
  });

  test('renders STOPPED status with error color', () => {
    const { container } = render(<TaskStatusBadge status={TASK_STATUS.STOPPED} />);
    expect(container.querySelector('.ant-tag')).toHaveClass('ant-tag-error');
  });

  test('renders COMPLETED status with processing color', () => {
    const { container } = render(<TaskStatusBadge status={TASK_STATUS.COMPLETED} />);
    expect(container.querySelector('.ant-tag')).toHaveClass('ant-tag-processing');
  });

  test('renders ERROR status with error color', () => {
    const { container } = render(<TaskStatusBadge status={TASK_STATUS.ERROR} />);
    expect(container.querySelector('.ant-tag')).toHaveClass('ant-tag-error');
  });

  test('renders unknown status as fallback', () => {
    const { container } = render(<TaskStatusBadge status="UNKNOWN" />);
    expect(container.textContent).toContain('UNKNOWN');
  });
});
