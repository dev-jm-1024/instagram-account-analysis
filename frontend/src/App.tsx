import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { AnimatePresence } from 'motion/react';
import { QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@/app/theme/ThemeProvider';
import { AppShell } from '@/app/layout/AppShell';
import { queryClient } from '@/services/api/queryClient';
import MainScreen from '@/features/onboarding/MainScreen';
import UploadScreen from '@/features/onboarding/UploadScreen';

/** location 기준으로 라우트를 교체하며 진입/이탈 애니메이션을 적용한다. */
function AnimatedRoutes() {
  const location = useLocation();
  return (
    <AnimatePresence mode="wait">
      <Routes location={location} key={location.pathname}>
        {/* 진입점: 메인(랜딩) 화면 */}
        <Route path="/" element={<Navigate to="/main" replace />} />
        <Route path="/main" element={<MainScreen />} />
        <Route path="/upload" element={<UploadScreen />} />
        {/* 대시보드(기존 앱) */}
        <Route path="/dashboard" element={<AppShell />} />
        {/* 알 수 없는 경로 → 메인 */}
        <Route path="*" element={<Navigate to="/main" replace />} />
      </Routes>
    </AnimatePresence>
  );
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <BrowserRouter>
          <AnimatedRoutes />
        </BrowserRouter>
      </ThemeProvider>
    </QueryClientProvider>
  );
}
