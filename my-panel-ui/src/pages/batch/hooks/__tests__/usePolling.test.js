import { renderHook, act } from '@testing-library/react';
import { usePolling } from '../usePolling';

describe('usePolling', () => {
  beforeEach(() => {
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.clearAllTimers();
    jest.clearAllMocks();
    jest.useRealTimers();
  });

  test('should call fetchFn on mount and then on interval', () => {
    const mockFetch = jest.fn().mockResolvedValue({ running: 3, paused: 1 });
    
    renderHook(() => usePolling(mockFetch, 3000));
    
    expect(mockFetch).toHaveBeenCalledTimes(1);
    
    act(() => {
      jest.advanceTimersByTime(3000);
    });
    expect(mockFetch).toHaveBeenCalledTimes(2);
    
    act(() => {
      jest.advanceTimersByTime(6000);
    });
    expect(mockFetch).toHaveBeenCalledTimes(4);
  });

  test('should stop polling when unmounted', () => {
    const mockFetch = jest.fn().mockResolvedValue({});
    
    const { unmount } = renderHook(() => usePolling(mockFetch, 1000));
    
    expect(mockFetch).toHaveBeenCalledTimes(1);
    
    unmount(); // 组件卸载
    
    act(() => {
      jest.advanceTimersByTime(5000);
    });
    expect(mockFetch).toHaveBeenCalledTimes(1); // 不应再调用
  });

  test('should support manual stop and start', () => {
    const mockFetch = jest.fn().mockResolvedValue({});
    
    const { result } = renderHook(() => usePolling(mockFetch, 1000));
    
    expect(mockFetch).toHaveBeenCalledTimes(1);
    
    act(() => {
      result.current.stop();
    });
    
    act(() => {
      jest.advanceTimersByTime(5000);
    });
    expect(mockFetch).toHaveBeenCalledTimes(1); // 已停止
    
    act(() => {
      result.current.start();
    });
    
    act(() => {
      jest.advanceTimersByTime(1500);
    });
    expect(mockFetch).toHaveBeenCalledTimes(2); // 已恢复
  });

  test('should return data, stop and start functions', () => {
    const mockFetch = jest.fn().mockResolvedValue({});
    
    const { result } = renderHook(() => usePolling(mockFetch, 5000));
    
    expect(result.current).toHaveProperty('data');
    expect(result.current).toHaveProperty('stop');
    expect(result.current).toHaveProperty('start');
    expect(typeof result.current.stop).toBe('function');
    expect(typeof result.current.start).toBe('function');
  });

  test('should use default interval of 3000ms', () => {
    const mockFetch = jest.fn().mockResolvedValue({});
    
    renderHook(() => usePolling(mockFetch));
    
    expect(mockFetch).toHaveBeenCalledTimes(1);
    
    act(() => {
      jest.advanceTimersByTime(3000);
    });
    expect(mockFetch).toHaveBeenCalledTimes(2);
  });
});
