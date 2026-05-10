import { TASK_STATUS, STATUS_COLOR, DEFAULT_PAGINATION, POLLING_INTERVAL } from '../constants';

describe('Batch Constants', () => {
  test('should export TASK_STATUS with all 6 statuses', () => {
    expect(TASK_STATUS).toEqual({
      READY: 'READY',
      RUNNING: 'RUNNING',
      PAUSED: 'PAUSED',
      STOPPED: 'STOPPED',
      COMPLETED: 'COMPLETED',
      ERROR: 'ERROR'
    });
  });

  test('should export STATUS_COLOR mapping correctly', () => {
    expect(STATUS_COLOR).toEqual({
      READY: 'default',
      RUNNING: 'success',
      PAUSED: 'warning',
      STOPPED: 'error',
      COMPLETED: 'processing',
      ERROR: 'error'
    });
  });

  test('should export DEFAULT_PAGINATION config', () => {
    expect(DEFAULT_PAGINATION).toEqual({
      current: 1,
      pageSize: 10,
      total: 0
    });
  });

  test('should export POLLING_INTERVAL as 3000ms', () => {
    expect(POLLING_INTERVAL).toBe(3000);
  });
});
