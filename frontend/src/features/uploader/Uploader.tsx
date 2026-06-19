import { useEffect, useRef, useState, type ChangeEvent, type DragEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  UploadCloud,
  Info,
  FolderSearch,
  FolderInput,
  CheckCircle,
  AlertTriangle,
  Loader2,
  HardDrive,
  Database,
  Trash2,
} from 'lucide-react';
import { useNavigation } from '@/app/routing/useNavigation';
import { useImportFlow } from '@/services/api/hooks';
import { api } from '@/services/api/endpoints';
import { queryKeys } from '@/services/api/queryKeys';
import { errorMessage } from '@/services/api/errorMessages';
import { cn } from '@/lib/cn';
import { ViewHeader, RadialProgress } from '@/components/ui';

export default function Uploader() {
  const { setActiveTab } = useNavigation();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const flow = useImportFlow();
  const [dragActive, setDragActive] = useState(false);
  const [manualPath, setManualPath] = useState('');
  const fileInputRef = useRef<HTMLInputElement>(null);

  // 현재 임포트 상태(삭제 패널용)
  const statusQuery = useQuery({ queryKey: queryKeys.importStatus, queryFn: () => api.importStatus() });
  const imported = statusQuery.data?.status === 'COMPLETED';
  const resetMutation = useMutation({
    mutationFn: () => api.resetImport(),
    onSuccess: () => {
      qc.clear(); // 모든 조회 캐시 비움 → 대시보드 잠김
      navigate('/main'); // 메인으로(대시보드 이탈)
    },
  });
  const onDelete = () => {
    if (window.confirm('현재 불러온 데이터를 삭제할까요?\n(디스크의 export 파일은 그대로이며, 다시 불러올 수 있습니다.)')) {
      resetMutation.mutate();
    }
  };

  // 임포트 완료 → Overview 로 자동 이동
  useEffect(() => {
    if (flow.phase === 'completed') {
      const t = setTimeout(() => setActiveTab('overview'), 900);
      return () => clearTimeout(t);
    }
  }, [flow.phase, setActiveTab]);

  const onPickZip = (file?: File | null) => {
    if (file) flow.uploadZip(file);
  };

  const handleDrag = (e: DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (e.type === 'dragenter' || e.type === 'dragover') setDragActive(true);
    else if (e.type === 'dragleave') setDragActive(false);
  };
  const handleDrop = (e: DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setDragActive(false);
    onPickZip(e.dataTransfer.files?.[0]);
  };
  const handleInput = (e: ChangeEvent<HTMLInputElement>) => onPickZip(e.target.files?.[0]);

  const busy =
    flow.phase === 'uploading' || flow.phase === 'extracting' || flow.phase === 'importing';

  return (
    <div className="space-y-8 animate-fade-in relative z-10">
      <ViewHeader
        icon={UploadCloud}
        iconClassName="text-indigo-500"
        title="데이터 임포트 (Uploader)"
        subtitle="Instagram 내보내기 ZIP 을 업로드하거나 서버의 export 폴더를 임포트해 분석을 시작합니다. 아래에서 데이터를 교체(갱신)하거나 삭제할 수 있습니다."
      />

      {/* 현재 데이터 + 삭제(초기화) */}
      {imported && (
        <div className="bg-ig-surface dark:bg-ig-surface-dark p-5 rounded-2xl border border-ig-border dark:border-ig-border-dark shadow-sm relative overflow-hidden flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div className="flex items-center gap-3 min-w-0">
            <div className="w-10 h-10 rounded-xl bg-emerald-500/10 text-emerald-500 flex items-center justify-center shrink-0">
              <Database className="w-5 h-5" />
            </div>
            <div className="min-w-0">
              <p className="text-sm font-bold text-slate-800 dark:text-white">현재 불러온 데이터</p>
              <p className="text-[11px] text-slate-400">
                {(statusQuery.data?.parsedItemCount ?? 0).toLocaleString()}건 분석됨 · 아래 목록에서 다른 export 를 고르면 교체(갱신)됩니다
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onDelete}
            disabled={resetMutation.isPending}
            className="shrink-0 inline-flex items-center gap-1.5 px-4 py-2 rounded-xl text-xs font-semibold text-rose-600 dark:text-rose-400 bg-rose-500/10 hover:bg-rose-500/20 border border-rose-500/20 transition-colors disabled:opacity-50"
          >
            {resetMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Trash2 className="w-4 h-4" />}
            데이터 삭제
          </button>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        <div className="lg:col-span-2 space-y-6">
          {flow.phase === 'idle' && (
            <IdleEntry
              dragActive={dragActive}
              fileInputRef={fileInputRef}
              onDrag={handleDrag}
              onDrop={handleDrop}
              onInput={handleInput}
              onPickZipClick={() => fileInputRef.current?.click()}
              candidates={flow.candidates}
              candidatesLoading={flow.candidatesLoading}
              onImportPath={flow.importFromPath}
              manualPath={manualPath}
              setManualPath={setManualPath}
            />
          )}

          {busy && (
            <ProgressPanel
              phase={flow.phase}
              uploadPct={flow.uploadPct}
              extractedEntries={flow.uploadStatus?.extractedEntries ?? 0}
              parsedItemCount={flow.importStatus?.parsedItemCount ?? 0}
            />
          )}

          {flow.phase === 'completed' && (
            <ResultPanel
              ok
              title="임포트 완료!"
              message={`${flow.importStatus?.parsedItemCount ?? 0}건을 반영했습니다. 잠시 후 요약 화면으로 이동합니다.`}
              warnings={flow.importStatus?.warnings.length ?? 0}
              onReset={() => setActiveTab('overview')}
              resetLabel="요약 화면으로"
            />
          )}

          {flow.phase === 'failed' && (
            <ResultPanel
              ok={false}
              title="임포트에 실패했습니다"
              message={flow.error ? errorMessage(flow.error) : (flow.message ?? '다시 시도해 주세요.')}
              onReset={flow.reset}
              resetLabel="다시 시도"
            />
          )}
        </div>

        <Sidebar />
      </div>
    </div>
  );
}

// ── 진입(idle): ZIP / 자동탐지 / 수동경로 ──────────────────────────
interface IdleEntryProps {
  dragActive: boolean;
  fileInputRef: React.RefObject<HTMLInputElement | null>;
  onDrag: (e: DragEvent) => void;
  onDrop: (e: DragEvent) => void;
  onInput: (e: ChangeEvent<HTMLInputElement>) => void;
  onPickZipClick: () => void;
  candidates: ReturnType<typeof useImportFlow>['candidates'];
  candidatesLoading: boolean;
  onImportPath: (path: string) => void;
  manualPath: string;
  setManualPath: (v: string) => void;
}

function IdleEntry({
  dragActive,
  fileInputRef,
  onDrag,
  onDrop,
  onInput,
  onPickZipClick,
  candidates,
  candidatesLoading,
  onImportPath,
  manualPath,
  setManualPath,
}: IdleEntryProps) {
  const list = candidates?.candidates ?? [];
  return (
    <div className="space-y-6">
      {/* ZIP 업로드 */}
      <form
        onDragEnter={onDrag}
        onDragOver={onDrag}
        onDragLeave={onDrag}
        onDrop={onDrop}
        onSubmit={(e) => e.preventDefault()}
        onClick={onPickZipClick}
        className={cn(
          'bg-ig-surface dark:bg-ig-surface-dark p-10 rounded-3xl border-2 border-dashed transition-all relative flex flex-col items-center justify-center text-center min-h-[260px] cursor-pointer',
          dragActive
            ? 'border-pink-500/80 bg-pink-500/5'
            : 'border-slate-300 dark:border-ig-border-dark',
        )}
      >
        <input ref={fileInputRef} type="file" className="hidden" accept=".zip" onChange={onInput} />
        <div className="w-16 h-16 rounded-full bg-gradient-to-tr from-pink-500 to-rose-500 p-[1px] text-white flex items-center justify-center shadow-md mb-4">
          <div className="w-full h-full rounded-full bg-slate-100 dark:bg-slate-950 flex items-center justify-center text-slate-800 dark:text-pink-400">
            <UploadCloud className="w-8 h-8" />
          </div>
        </div>
        <h3 className="text-sm font-bold text-slate-800 dark:text-white">
          Instagram 내보내기 ZIP 을 드래그하거나 클릭하여 선택하세요
        </h3>
        <p className="text-xs text-slate-400 dark:text-slate-500 max-w-sm mt-2 leading-relaxed">
          서버가 압축을 풀어 자동으로 임포트합니다. 미디어 포함 전체 추출이라 여유 디스크 공간이 필요합니다.
        </p>
        <span className="text-[10px] text-slate-400 uppercase tracking-widest bg-slate-500/5 px-2.5 py-1 rounded-md mt-4 border border-slate-200/50">
          .ZIP (JSON export)
        </span>
      </form>

      {/* 자동 탐지된 후보 */}
      <div className="bg-ig-surface dark:bg-ig-surface-dark p-5 rounded-2xl border border-ig-border dark:border-ig-border-dark shadow-sm relative overflow-hidden space-y-3">
        <h3 className="text-xs font-bold text-slate-400 uppercase tracking-widest flex items-center gap-1.5">
          <FolderSearch className="w-4 h-4 text-indigo-500" />
          서버 data/ 에서 탐지된 export
        </h3>
        {candidatesLoading ? (
          <p className="text-xs text-slate-400">탐지 중…</p>
        ) : list.length === 0 ? (
          <p className="text-xs text-slate-400">
            자동 탐지된 export 폴더가 없습니다. ZIP 을 올리거나 아래에 경로를 입력하세요.
          </p>
        ) : (
          <div className="space-y-2">
            {list.map((c) => (
              <button
                key={c.path}
                onClick={() => onImportPath(c.path)}
                className="w-full flex items-center gap-3 text-left bg-ig-surface dark:bg-ig-surface-dark border border-ig-border dark:border-ig-border-dark hover:bg-pink-500/10 p-3 rounded-xl transition-all hover:border-pink-500/20"
              >
                <FolderInput className="w-4 h-4 text-pink-500 shrink-0" />
                <span className="flex-1 min-w-0">
                  <span className="block text-xs font-semibold text-slate-700 dark:text-slate-200 truncate">
                    {c.name}
                  </span>
                  <span className="block text-[10px] text-slate-400 truncate">
                    {c.account ?? ''} {c.exportedAt ?? ''}
                  </span>
                </span>
              </button>
            ))}
          </div>
        )}
      </div>

      {/* 수동 경로 */}
      <div className="bg-ig-surface dark:bg-ig-surface-dark p-5 rounded-2xl border border-ig-border dark:border-ig-border-dark shadow-sm relative overflow-hidden space-y-3">
        <h3 className="text-xs font-bold text-slate-400 uppercase tracking-widest flex items-center gap-1.5">
          <FolderInput className="w-4 h-4 text-indigo-500" />
          서버 폴더 경로 직접 입력
        </h3>
        <div className="flex gap-2">
          <input
            value={manualPath}
            onChange={(e) => setManualPath(e.target.value)}
            placeholder="/path/to/instagram-export"
            className="flex-1 bg-ig-field dark:bg-ig-field-dark border border-slate-200/70 dark:border-ig-border-dark rounded-xl px-3 py-2 text-xs text-slate-700 dark:text-slate-200 outline-none focus:border-pink-500/50"
          />
          <button
            onClick={() => manualPath.trim() && onImportPath(manualPath.trim())}
            disabled={!manualPath.trim()}
            className="px-4 py-2 bg-pink-500 text-white font-semibold rounded-xl text-xs disabled:opacity-40 hover:bg-pink-600 transition-all"
          >
            임포트
          </button>
        </div>
      </div>
    </div>
  );
}

// ── 진행 단계 ───────────────────────────────────────────────────
function ProgressPanel({
  phase,
  uploadPct,
  extractedEntries,
  parsedItemCount,
}: {
  phase: string;
  uploadPct: number;
  extractedEntries: number;
  parsedItemCount: number;
}) {
  const label =
    phase === 'uploading' ? '업로드 중' : phase === 'extracting' ? '압축 해제 중' : '분석(임포트) 중';
  const detail =
    phase === 'uploading'
      ? `${uploadPct}%`
      : phase === 'extracting'
        ? `${extractedEntries.toLocaleString()} 항목 추출`
        : `${parsedItemCount.toLocaleString()} 건 파싱`;

  return (
    <div className="bg-ig-surface dark:bg-ig-surface-dark p-10 rounded-3xl border border-ig-border dark:border-ig-border-dark shadow-sm relative overflow-hidden flex flex-col items-center justify-center text-center min-h-[260px] gap-5">
      {phase === 'uploading' ? (
        <RadialProgress percentage={uploadPct} value={`${uploadPct}%`} label="UPLOAD" />
      ) : (
        <Loader2 className="w-14 h-14 text-pink-500 animate-spin" />
      )}
      <div>
        <h3 className="text-sm font-bold text-slate-800 dark:text-white">{label}</h3>
        <p className="text-xs text-slate-400 mt-1">{detail}</p>
      </div>
    </div>
  );
}

// ── 결과 (완료/실패) ────────────────────────────────────────────
function ResultPanel({
  ok,
  title,
  message,
  warnings = 0,
  onReset,
  resetLabel,
}: {
  ok: boolean;
  title: string;
  message: string;
  warnings?: number;
  onReset: () => void;
  resetLabel: string;
}) {
  return (
    <div
      className={cn(
        'p-8 rounded-3xl border space-y-4 text-center',
        ok
          ? 'bg-emerald-500/10 border-emerald-500/20'
          : 'bg-rose-500/10 border-rose-500/20',
      )}
    >
      <div className="flex flex-col items-center gap-3">
        {ok ? (
          <CheckCircle className="w-12 h-12 text-emerald-500" />
        ) : (
          <AlertTriangle className="w-12 h-12 text-rose-500" />
        )}
        <h3
          className={cn(
            'text-base font-bold',
            ok ? 'text-emerald-700 dark:text-emerald-300' : 'text-rose-700 dark:text-rose-300',
          )}
        >
          {title}
        </h3>
        <p className="text-xs text-slate-500 dark:text-slate-400 max-w-sm">{message}</p>
        {warnings > 0 && (
          <p className="text-[11px] text-amber-600 dark:text-amber-400">
            경고 {warnings}건 — 일부 파일은 건너뛰었습니다.
          </p>
        )}
      </div>
      <button
        onClick={onReset}
        className="px-5 py-2 bg-slate-900 dark:bg-white text-white dark:text-slate-900 font-semibold rounded-xl text-xs hover:opacity-90 transition-all"
      >
        {resetLabel}
      </button>
    </div>
  );
}

// ── 사이드바 가이드 ─────────────────────────────────────────────
function Sidebar() {
  return (
    <div className="lg:col-span-1 space-y-6">
      <div className="bg-ig-surface dark:bg-ig-surface-dark p-5 rounded-2xl border border-ig-border dark:border-ig-border-dark shadow-sm relative overflow-hidden space-y-3">
        <h3 className="text-xs font-bold text-slate-400 uppercase tracking-widest flex items-center gap-1.5">
          <HardDrive className="w-4 h-4 text-indigo-500" />
          디스크 안내
        </h3>
        <p className="text-[11px] text-slate-500 leading-relaxed">
          ZIP 업로드 시 서버가 미디어를 포함해 전체 압축을 해제하므로 export 크기만큼 디스크 여유가 필요합니다.
          재업로드 시 같은 이름의 이전 추출본은 정리됩니다.
        </p>
      </div>

      <div className="bg-ig-surface dark:bg-ig-surface-dark p-5 rounded-2xl border border-ig-border dark:border-ig-border-dark shadow-sm relative overflow-hidden space-y-3.5">
        <h3 className="text-xs font-bold text-slate-400 uppercase tracking-widest flex items-center gap-1">
          <Info className="w-4 h-4 text-indigo-500" />
          백업 받는 법 가이드
        </h3>
        <div className="space-y-3 text-xs leading-relaxed text-slate-600 dark:text-slate-400">
          <div className="flex gap-2">
            <span className="font-bold text-pink-500">1.</span>
            <p>
              인스타그램 앱 → <strong>메뉴</strong> → <strong>내 활동</strong> →{' '}
              <strong>내 정보 다운로드</strong> 선택
            </p>
          </div>
          <div className="flex gap-2">
            <span className="font-bold text-pink-500">2.</span>
            <p>
              다운로드 형식으로 반드시{' '}
              <strong className="text-pink-500 font-bold uppercase">JSON</strong> 을
              지정하세요. (HTML 불가)
            </p>
          </div>
          <div className="flex gap-2">
            <span className="font-bold text-pink-500">3.</span>
            <p>며칠 후 메일로 받은 ZIP 을 그대로 위에 업로드하면 끝!</p>
          </div>
        </div>
      </div>
    </div>
  );
}
