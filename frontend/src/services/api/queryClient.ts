import { QueryClient } from '@tanstack/react-query';
import { ApiError } from './client';

/**
 * 단일 사용자 localhost 전제. 데이터는 임포트 1회 후 정적이라
 * 윈도우 포커스 refetch 끄고 staleTime 을 길게 둔다.
 * 4xx(클라 계약 위반·게이트)는 재시도 무의미 → retry 비활성.
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 60_000,
      refetchOnWindowFocus: false,
      retry: (failureCount, error) => {
        if (error instanceof ApiError && error.httpStatus >= 400 && error.httpStatus < 500) {
          return false;
        }
        return failureCount < 1;
      },
    },
  },
});
