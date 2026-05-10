import { useState, useEffect, useRef, useCallback } from 'react';

/**
 * 通用轮询Hook
 * @param {Function} fetchFn - 数据获取函数
 * @param {number} intervalMs - 轮询间隔(毫秒)，默认3000ms
 * @returns {{ data: any, stop: Function, start: Function }}
 */
export function usePolling(fetchFn, intervalMs = 3000) {
  const [data, setData] = useState(null);
  const timerRef = useRef(null);
  const isRunningRef = useRef(true);

  const fetchData = useCallback(async () => {
    if (!isRunningRef.current) return;
    
    try {
      const result = await fetchFn();
      setData(result);
    } catch (error) {
      console.error('[usePolling] Fetch error:', error);
    }
  }, [fetchFn]);

  useEffect(() => {
    fetchData(); // 立即执行一次
    
    timerRef.current = setInterval(fetchData, intervalMs);
    
    return () => {
      if (timerRef.current) {
        clearInterval(timerRef.current);
      }
    };
  }, [fetchFn, intervalMs]);

  const stop = useCallback(() => {
    isRunningRef.current = false;
  }, []);

  const start = useCallback(() => {
    isRunningRef.current = true;
  }, []);

  return { data, stop, start };
}
