export const TASK_STATUS = {
  READY: 'READY',
  RUNNING: 'RUNNING',
  PAUSED: 'PAUSED',
  STOPPED: 'STOPPED',
  COMPLETED: 'COMPLETED',
  ERROR: 'ERROR'
};

export const STATUS_COLOR = {
  [TASK_STATUS.READY]: 'default',
  [TASK_STATUS.RUNNING]: 'success',
  [TASK_STATUS.PAUSED]: 'warning',
  [TASK_STATUS.STOPPED]: 'error',
  [TASK_STATUS.COMPLETED]: 'processing',
  [TASK_STATUS.ERROR]: 'error'
};

export const DEFAULT_PAGINATION = {
  current: 1,
  pageSize: 10,
  total: 0
};

export const POLLING_INTERVAL = 3000;
