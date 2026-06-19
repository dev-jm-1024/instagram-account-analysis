import { useNavigate } from 'react-router-dom';
import { motion } from 'motion/react';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/services/api/endpoints';
import { queryKeys } from '@/services/api/queryKeys';

/**
 * 랜딩(메인) 화면 — 흰 배경 + 중앙 로고 이미지 + "시작하기" CTA.
 * 시작 시: 이미 임포트된 데이터가 있으면 → 대시보드 직행, 아니면 → 업로드/선택 화면.
 */
export default function MainScreen() {
  const navigate = useNavigate();
  // 시작 분기를 위해 현재 임포트 상태를 미리 받아둔다(빠름, 실패해도 업로드로 폴백).
  const status = useQuery({ queryKey: queryKeys.importStatus, queryFn: () => api.importStatus() });

  const onStart = () => {
    navigate(status.data?.status === 'COMPLETED' ? '/dashboard' : '/upload');
  };

  return (
    <motion.div
      initial={{ opacity: 0, x: -40 }}
      animate={{ opacity: 1, x: 0 }}
      exit={{ opacity: 0, x: -40 }}
      transition={{ duration: 0.34, ease: [0.22, 1, 0.36, 1] }}
      className="min-h-screen w-full bg-[#FDFDFD] flex flex-col items-center justify-center px-6 text-center"
    >
      {/* 중앙 로고 이미지 */}
      <img
        src="/instagram-analyze2.png"
        alt="InstaInsight"
        className="w-full max-w-xs md:max-w-sm h-auto object-contain select-none"
        draggable={false}
      />

      {/* 보조 카피 */}
      <p className="mt-8 text-[18px] leading-relaxed text-[#252525] whitespace-nowrap">
        내 인스타그램 데이터를 100% 브라우저 로컬에서 안전하게 분석해 보세요.
      </p>
      <p className="mt-1 text-[13px] text-[#8e8e8e]">외부 서버 전송 없이, 내 기기 안에서만.</p>

      {/* 시작하기 CTA */}
      <button
        type="button"
        onClick={onStart}
        className="mt-10 inline-flex items-center justify-center rounded-2xl px-16 py-4 text-[17px] font-semibold tracking-wide text-white transition-all hover:-translate-y-0.5 hover:brightness-105 active:translate-y-0 ig-gradient"
      >
        {status.data?.status === 'COMPLETED' ? '대시보드로 이동' : '시작하기'}
      </button>

      {/* 푸터 */}
      <p className="mt-12 text-[11px] tracking-wide text-[#c7c7c7]">
        © 2026 InstaInsight · 100% Client-Side Privacy
      </p>
    </motion.div>
  );
}
