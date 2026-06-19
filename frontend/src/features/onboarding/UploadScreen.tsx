import { useEffect, useRef, useState, type DragEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'motion/react';
import {
  ArrowLeft,
  UploadCloud,
  FileArchive,
  Check,
  Loader2,
  AlertTriangle,
  CheckCircle,
  FolderInput,
} from 'lucide-react';
import { useImportFlow } from '@/services/api/hooks';
import { errorMessage } from '@/services/api/errorMessages';
import { cn } from '@/lib/cn';

/**
 * 데이터 업로드 화면 (샘플 라우트: /test/upload) — 인스타그램풍 흰 배경 디자인.
 *
 * 기존 업로드 로직(useImportFlow: ZIP 업로드 → 추출 폴링 → ETL 임포트 폴링)을 그대로 재사용한다.
 * 완료(phase==='completed')되면 대시보드(`/`)로 이동한다.
 */

const STEPS = ['파일 저장', '압축 해제', '분석 완료'] as const;
// phase → 진행 랭크(스텝 인덱스). 랭크 > i 면 완료, === i 면 진행 중.
const RANK: Record<string, number> = {
  idle: -1,
  uploading: 0,
  extracting: 1,
  importing: 2,
  completed: 3,
  failed: -1,
};

export default function UploadScreen() {
  const navigate = useNavigate();
  const flow = useImportFlow();
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragActive, setDragActive] = useState(false);
  const [file, setFile] = useState<File | null>(null);

  const phase = flow.phase;
  const busy = phase === 'uploading' || phase === 'extracting' || phase === 'importing';
  const rank = RANK[phase] ?? -1;

  // 임포트 완료 → 완료 알림을 잠시 보여준 뒤 대시보드로 이동
  useEffect(() => {
    if (phase === 'completed') {
      const t = setTimeout(() => navigate('/dashboard'), 1600);
      return () => clearTimeout(t);
    }
  }, [phase, navigate]);

  // data/ 후보 — 1개면 자동 임포트(바로 대시보드 흐름), 여러 개면 사용자가 고른다.
  const candidateList = flow.candidates?.candidates;
  const importFromPath = flow.importFromPath;
  const autoTriggered = useRef(false);
  useEffect(() => {
    const list = candidateList ?? [];
    if (!autoTriggered.current && phase === 'idle' && list.length === 1) {
      autoTriggered.current = true;
      importFromPath(list[0].path);
    }
  }, [candidateList, phase, importFromPath]);

  const parsedCount = flow.importStatus?.parsedItemCount ?? 0;
  const warningCount = flow.importStatus?.warnings?.length ?? 0;

  const pickFile = (f?: File | null) => {
    if (f && !busy) setFile(f);
  };

  const handleDrag = (e: DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (busy) return;
    if (e.type === 'dragenter' || e.type === 'dragover') setDragActive(true);
    else if (e.type === 'dragleave') setDragActive(false);
  };
  const handleDrop = (e: DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setDragActive(false);
    pickFile(e.dataTransfer.files?.[0]);
  };

  const startUpload = () => {
    if (file && !busy) flow.uploadZip(file);
  };

  const retry = () => {
    flow.reset();
    setFile(null);
  };

  // 진행 중 활성 스텝의 상세 텍스트
  const activeDetail =
    phase === 'uploading'
      ? `${flow.uploadPct}%`
      : phase === 'extracting'
        ? `${(flow.uploadStatus?.extractedEntries ?? 0).toLocaleString()} 항목 추출`
        : phase === 'importing'
          ? `${(flow.importStatus?.parsedItemCount ?? 0).toLocaleString()} 건 파싱`
          : '';

  return (
    <motion.div
      initial={{ opacity: 0, x: 40 }}
      animate={{ opacity: 1, x: 0 }}
      exit={{ opacity: 0, x: 40 }}
      transition={{ duration: 0.34, ease: [0.22, 1, 0.36, 1] }}
      className="min-h-screen w-full bg-[#FDFDFD] text-[#262626] flex flex-col"
    >
      {/* 상단 바 */}
      <header className="h-14 shrink-0 border-b border-[#dbdbdb] flex items-center px-4 gap-3">
        <button
          type="button"
          onClick={() => navigate('/main')}
          disabled={busy}
          className="p-1.5 -ml-1.5 rounded-full hover:bg-black/5 transition-colors disabled:opacity-40"
          aria-label="뒤로"
        >
          <ArrowLeft className="w-5 h-5" />
        </button>
        <span className="text-[15px] font-semibold">데이터 업로드</span>
      </header>

      {/* 본문 */}
      <main className="flex-1 flex items-center justify-center px-6 py-10">
        <div className="w-full max-w-md">
          <div className="text-center mb-8">
            <h1 className="text-xl font-bold">내 인스타그램 데이터 올리기</h1>
            <p className="mt-2 text-[13px] text-[#8e8e8e] leading-relaxed">
              Meta에서 내려받은 <strong className="text-[#262626]">ZIP</strong> 또는 개별{' '}
              <strong className="text-[#262626]">JSON</strong> 파일을 올리면 자동으로 분석합니다.
            </p>
          </div>

          {phase === 'failed' ? (
            <ErrorCard
              message={
                flow.error ? errorMessage(flow.error) : (flow.message ?? '업로드에 실패했습니다. 다시 시도해 주세요.')
              }
              onRetry={retry}
            />
          ) : (
            <>
              {/* data/ 에서 탐지된 export 후보 — 고르면 바로 임포트(여러 개면 선택, 1개면 자동) */}
              {phase === 'idle' && (candidateList?.length ?? 0) > 0 && (
                <div className="mb-6 rounded-2xl border border-[#dbdbdb] bg-white p-4">
                  <p className="text-[12px] font-semibold text-[#262626] mb-3 flex items-center gap-1.5">
                    <FolderInput className="w-4 h-4 text-[#d62976]" />
                    data 폴더에서 찾은 데이터 — 골라서 보기
                  </p>
                  <div className="space-y-2">
                    {(candidateList ?? []).map((c) => (
                      <button
                        key={c.path}
                        type="button"
                        onClick={() => importFromPath(c.path)}
                        className="w-full flex items-center gap-3 text-left rounded-xl border border-[#efefef] hover:border-[#d62976]/40 hover:bg-[#d62976]/[0.03] px-3 py-2.5 transition-colors"
                      >
                        <FolderInput className="w-4 h-4 text-[#d62976] shrink-0" />
                        <span className="min-w-0 flex-1">
                          <span className="block text-[13px] font-semibold text-[#262626] truncate">{c.name}</span>
                          <span className="block text-[11px] text-[#8e8e8e] truncate">
                            {c.account ?? ''} {c.exportedAt ?? ''}
                          </span>
                        </span>
                      </button>
                    ))}
                  </div>
                  <p className="mt-3 text-[11px] text-[#a8a8a8] text-center">또는 아래에서 ZIP 을 직접 올리세요</p>
                </div>
              )}

              {/* 드롭존 / 상태 */}
              <form
                onDragEnter={handleDrag}
                onDragOver={handleDrag}
                onDragLeave={handleDrag}
                onDrop={handleDrop}
                onSubmit={(e) => e.preventDefault()}
                onClick={() => !busy && phase !== 'completed' && inputRef.current?.click()}
                className={cn(
                  'rounded-2xl border-2 border-dashed flex flex-col items-center justify-center text-center px-6 py-12 transition-all',
                  busy || phase === 'completed' ? 'cursor-default' : 'cursor-pointer',
                  dragActive
                    ? 'border-[#0095f6] bg-[#0095f6]/5'
                    : 'border-[#dbdbdb] hover:border-[#a8a8a8] hover:bg-black/[0.015]',
                )}
              >
                <input
                  ref={inputRef}
                  type="file"
                  className="hidden"
                  accept=".zip,.json"
                  onChange={(e) => pickFile(e.target.files?.[0])}
                />

                <div className="w-16 h-16 rounded-full bg-gradient-to-tr from-[#feda75] via-[#d62976] to-[#4f5bd5] p-[2px] mb-4">
                  <div className="w-full h-full rounded-full bg-white flex items-center justify-center">
                    {phase === 'completed' ? (
                      <CheckCircle className="w-7 h-7 text-emerald-500" />
                    ) : busy ? (
                      <Loader2 className="w-7 h-7 text-[#d62976] animate-spin" />
                    ) : file ? (
                      <FileArchive className="w-7 h-7 text-[#d62976]" />
                    ) : (
                      <UploadCloud className="w-7 h-7 text-[#d62976]" />
                    )}
                  </div>
                </div>

                {phase === 'completed' ? (
                  <p className="text-[14px] font-semibold text-emerald-600">분석 완료! 잠시 후 이동합니다…</p>
                ) : busy ? (
                  <>
                    <p className="text-[14px] font-semibold">
                      {phase === 'uploading' ? '업로드 중' : phase === 'extracting' ? '압축 해제 중' : '데이터 분석 중'}
                    </p>
                    <p className="mt-1 text-[12px] text-[#8e8e8e]">{activeDetail}</p>
                  </>
                ) : file ? (
                  <p className="text-[14px] font-semibold break-all">{file.name}</p>
                ) : (
                  <>
                    <p className="text-[14px] font-semibold">여기로 파일을 드래그하세요</p>
                    <p className="mt-1 text-[12px] text-[#8e8e8e]">또는 클릭하여 선택</p>
                  </>
                )}
                {!busy && phase !== 'completed' && (
                  <span className="mt-4 text-[11px] uppercase tracking-widest text-[#a8a8a8] bg-[#fafafa] border border-[#efefef] px-2.5 py-1 rounded-md">
                    ZIP · JSON 지원
                  </span>
                )}
              </form>

              {/* 진행 단계 (실시간) */}
              <div className="mt-6 flex items-center justify-between px-1">
                {STEPS.map((label, i) => {
                  const state = rank > i ? 'done' : rank === i ? 'active' : 'pending';
                  return (
                    <div key={label} className="flex items-center gap-2 flex-1 last:flex-none">
                      <div className="flex items-center gap-2">
                        <span
                          className={cn(
                            'w-6 h-6 rounded-full text-[11px] font-bold flex items-center justify-center border transition-colors',
                            state === 'done' && 'bg-emerald-500 border-emerald-500 text-white',
                            state === 'active' && 'bg-[#0095f6] border-[#0095f6] text-white',
                            state === 'pending' && 'border-[#dbdbdb] text-[#a8a8a8]',
                          )}
                        >
                          {state === 'done' ? (
                            <Check className="w-3.5 h-3.5" />
                          ) : state === 'active' ? (
                            <Loader2 className="w-3.5 h-3.5 animate-spin" />
                          ) : (
                            i + 1
                          )}
                        </span>
                        <span
                          className={cn(
                            'text-[12px] whitespace-nowrap transition-colors',
                            state === 'pending' ? 'text-[#8e8e8e]' : 'text-[#262626] font-medium',
                          )}
                        >
                          {label}
                        </span>
                      </div>
                      {i < STEPS.length - 1 && (
                        <div
                          className={cn(
                            'flex-1 h-px mx-2 transition-colors',
                            rank > i ? 'bg-emerald-400' : 'bg-[#dbdbdb]',
                          )}
                        />
                      )}
                    </div>
                  );
                })}
              </div>

              {/* 진행 바 (애니메이션) */}
              {busy && (
                <div className="mt-6 h-1.5 w-full rounded-full bg-[#efefef] overflow-hidden">
                  {phase === 'uploading' ? (
                    <div
                      className="h-full rounded-full bg-[linear-gradient(90deg,#feda75,#d62976,#4f5bd5)] transition-[width] duration-200 ease-out"
                      style={{ width: `${flow.uploadPct}%` }}
                    />
                  ) : (
                    <div className="h-full w-full rounded-full bg-[linear-gradient(90deg,#feda75,#d62976,#4f5bd5)] animate-pulse" />
                  )}
                </div>
              )}

              {/* 완료 알림 */}
              {phase === 'completed' && (
                <div className="mt-6 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 flex items-start gap-3 animate-fade-in">
                  <CheckCircle className="w-5 h-5 text-emerald-500 shrink-0 mt-0.5" />
                  <div className="min-w-0">
                    <p className="text-[13px] font-semibold text-emerald-700">
                      임포트 완료! {parsedCount.toLocaleString()}건을 반영했습니다.
                    </p>
                    {warningCount > 0 && (
                      <p className="text-[11px] text-amber-600 mt-0.5">
                        경고 {warningCount}건 — 일부 파일은 건너뛰었습니다.
                      </p>
                    )}
                    <p className="text-[11px] text-emerald-600/80 mt-0.5">잠시 후 대시보드로 이동합니다…</p>
                  </div>
                </div>
              )}

              {/* 업로드 CTA */}
              <button
                type="button"
                onClick={startUpload}
                disabled={!file || busy || phase === 'completed'}
                className={cn(
                  'mt-8 w-full rounded-lg py-3 text-[15px] font-semibold text-white transition-colors flex items-center justify-center gap-2',
                  file && !busy && phase !== 'completed'
                    ? 'bg-[#0095f6] hover:bg-[#1877f2]'
                    : 'bg-[#b2dffc] cursor-not-allowed',
                )}
              >
                {busy ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin" />
                    처리 중…
                  </>
                ) : phase === 'completed' ? (
                  <>
                    <Check className="w-4 h-4" />
                    완료
                  </>
                ) : (
                  <>
                    <Check className="w-4 h-4" />
                    업로드 시작
                  </>
                )}
              </button>
            </>
          )}

          <p className="mt-4 text-center text-[11px] text-[#c7c7c7] leading-relaxed">
            업로드된 데이터는 이 기기 안에서만 처리되며 외부로 전송되지 않습니다.
          </p>
        </div>
      </main>
    </motion.div>
  );
}

function ErrorCard({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div className="rounded-2xl border border-rose-200 bg-rose-50 p-8 flex flex-col items-center text-center gap-3">
      <AlertTriangle className="w-12 h-12 text-rose-500" />
      <h3 className="text-base font-bold text-rose-700">업로드에 실패했습니다</h3>
      <p className="text-[13px] text-rose-600/90 max-w-sm">{message}</p>
      <button
        type="button"
        onClick={onRetry}
        className="mt-1 px-5 py-2 bg-rose-500 hover:bg-rose-600 text-white font-semibold rounded-lg text-[13px] transition-colors"
      >
        다시 시도
      </button>
    </div>
  );
}
