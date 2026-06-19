import { useMemo, useState } from 'react';
import { Folder, FolderOpen, FileCode, FileImage, Film, Terminal, AlertTriangle, Loader2, UploadCloud, ImageOff } from 'lucide-react';
import { useNavigation } from '@/app/routing/useNavigation';
import { useExplorerTree, useExplorerFile } from '@/services/api/hooks';
import { errorCode, errorMessage } from '@/services/api/errorMessages';
import type { ExplorerNode } from '@/services/api/types';
import { cn } from '@/lib/cn';
import { ViewHeader } from '@/components/ui';

type MediaKind = 'image' | 'video' | 'heic';

/** 확장자로 미디어 종류 판별(아니면 null → 기존 텍스트 뷰). */
function mediaKind(path: string | null): MediaKind | null {
  if (!path) return null;
  const ext = path.split('.').pop()?.toLowerCase() ?? '';
  if (['jpg', 'jpeg', 'png', 'gif', 'webp'].includes(ext)) return 'image';
  if (['mp4', 'mov', 'webm'].includes(ext)) return 'video';
  if (['heic', 'heif'].includes(ext)) return 'heic';
  return null;
}

const mediaUrl = (path: string) => `/api/explorer/media?path=${encodeURIComponent(path)}`;

export default function RawExplorer() {
  const { setActiveTab } = useNavigation();
  const tree = useExplorerTree();
  const [selectedPath, setSelectedPath] = useState<string | null>(null);
  const kind = mediaKind(selectedPath);
  // 미디어는 바이트 스트리밍(엔드포인트)로 보여주므로 텍스트 내용은 받지 않는다.
  const file = useExplorerFile(kind ? null : selectedPath);

  const pretty = useMemo(() => {
    const content = file.value?.content;
    if (!content) return '';
    try {
      return JSON.stringify(JSON.parse(content), null, 2);
    } catch {
      return content; // JSON 이 아니면 원본 그대로
    }
  }, [file.value]);

  if (tree.query.isLoading) {
    return <Center spin text="파일 트리를 불러오는 중…" />;
  }
  if (errorCode(tree.query.error) === 'IMPORT_NOT_COMPLETED' || !tree.value) {
    return (
      <button
        onClick={() => setActiveTab('uploader')}
        className="w-full rounded-3xl border border-dashed border-emerald-500/40 bg-emerald-500/5 p-12 flex flex-col items-center gap-4 hover:bg-emerald-500/10 transition-all"
      >
        <UploadCloud className="w-12 h-12 text-emerald-500" />
        <h3 className="text-base font-bold text-slate-800 dark:text-white">먼저 데이터를 임포트하세요</h3>
        <p className="text-xs text-slate-500">임포트한 export 폴더를 읽기 전용으로 탐색할 수 있습니다.</p>
      </button>
    );
  }

  return (
    <div className="space-y-8 animate-fade-in">
      <ViewHeader
        icon={Terminal}
        iconClassName="text-emerald-500"
        title="데이터 탐색기"
        subtitle="임포트한 export 폴더를 읽기 전용으로 탐색합니다. 파일을 선택하면 정규화된 내용을 표시합니다."
        divider={false}
      />

      <div className="grid grid-cols-1 md:grid-cols-4 md:grid-rows-1 bg-slate-900 border border-ig-border dark:border-ig-border-dark rounded-2xl overflow-hidden h-[560px] relative">
        {/* File tree */}
        <div className="md:col-span-1 bg-black/40 border-r border-ig-border dark:border-ig-border-dark flex flex-col h-full font-sans">
          <span className="text-[10px] tracking-wider text-slate-500 px-4 py-3 uppercase block border-b border-ig-border dark:border-ig-border-dark bg-black/20">
            📁 EXPORT TREE
          </span>
          <div className="flex-1 min-h-0 overflow-y-auto p-2 select-none">
            <TreeNode
              node={tree.value.root}
              depth={0}
              selectedPath={selectedPath}
              onSelect={setSelectedPath}
            />
          </div>
        </div>

        {/* Viewer */}
        <div className="md:col-span-3 h-full flex flex-col">
          <div className="p-3 bg-black/60 border-b border-ig-border dark:border-ig-border-dark flex items-center gap-2 px-4">
            <span className="w-2.5 h-2.5 rounded-full bg-rose-500" />
            <span className="w-2.5 h-2.5 rounded-full bg-amber-500" />
            <span className="w-2.5 h-2.5 rounded-full bg-emerald-500" />
            <span className="text-xs text-slate-400 font-semibold ml-2 truncate">
              {selectedPath ?? '파일을 선택하세요'}
            </span>
            {file.value?.truncated && (
              <span className="ml-auto text-[10px] text-amber-400 bg-amber-500/10 px-2 py-0.5 rounded border border-amber-500/20 shrink-0">
                10MB 초과 — 잘림
              </span>
            )}
          </div>

          <div className="flex-1 min-h-0 relative overflow-auto bg-black/85">
            {!selectedPath ? (
              <Center text="왼쪽 트리에서 파일을 선택하세요." />
            ) : kind ? (
              <MediaViewer path={selectedPath} kind={kind} />
            ) : file.query.isLoading ? (
              <Center spin text="파일을 불러오는 중…" />
            ) : file.query.isError ? (
              <div className="p-5 text-rose-400 text-xs flex items-center gap-2">
                <AlertTriangle className="w-4 h-4 shrink-0" />
                {errorMessage(file.query.error)}
              </div>
            ) : (
              <pre className="p-5 text-emerald-400 text-[11px] leading-relaxed whitespace-pre-wrap break-words">
                {pretty}
              </pre>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

function TreeNode({
  node,
  depth,
  selectedPath,
  onSelect,
}: {
  node: ExplorerNode;
  depth: number;
  selectedPath: string | null;
  onSelect: (path: string) => void;
}) {
  const [open, setOpen] = useState(depth < 1);

  if (!node.directory) {
    const active = selectedPath === node.relativePath;
    const fk = mediaKind(node.name);
    const Icon = fk === 'video' ? Film : fk ? FileImage : FileCode;
    const iconColor = fk === 'video' ? 'text-purple-400/80' : fk ? 'text-sky-400/80' : 'text-emerald-500/80';
    return (
      <button
        onClick={() => onSelect(node.relativePath)}
        style={{ paddingLeft: `${depth * 12 + 8}px` }}
        className={cn(
          'w-full text-left py-1 pr-2 text-[11px] truncate flex items-center gap-1.5 rounded-md',
          active ? 'bg-ig-field-dark text-white' : 'text-slate-400 hover:text-white hover:bg-ig-field-dark',
        )}
      >
        <Icon className={cn('w-3 h-3 shrink-0', iconColor)} />
        {node.name}
      </button>
    );
  }

  return (
    <div>
      <button
        onClick={() => setOpen((v) => !v)}
        style={{ paddingLeft: `${depth * 12 + 8}px` }}
        className="w-full text-left py-1 pr-2 text-[10px] text-zinc-500 hover:text-zinc-300 flex items-center gap-1.5"
      >
        {open ? (
          <FolderOpen className="w-3.5 h-3.5 text-yellow-500/70 shrink-0" />
        ) : (
          <Folder className="w-3.5 h-3.5 text-yellow-500/70 shrink-0" />
        )}
        {node.name}/
      </button>
      {open &&
        node.children.map((child) => (
          <TreeNode
            key={child.relativePath || child.name}
            node={child}
            depth={depth + 1}
            selectedPath={selectedPath}
            onSelect={onSelect}
          />
        ))}
    </div>
  );
}

/** 이미지/영상 미리보기. heic(브라우저 미지원)·로드 실패는 안내 + 새 탭 열기 fallback. */
function MediaViewer({ path, kind }: { path: string; kind: MediaKind }) {
  const [failed, setFailed] = useState(false);
  const url = mediaUrl(path);

  if (kind === 'heic' || failed) {
    return (
      <div className="absolute inset-0 flex flex-col items-center justify-center p-12 gap-4 text-slate-400 text-center">
        <ImageOff className="w-12 h-12" />
        <div>
          <p className="text-sm text-slate-300">
            {kind === 'heic' ? '이 포맷(HEIC)은 브라우저 미리보기를 지원하지 않습니다.' : '미리보기를 불러오지 못했습니다.'}
          </p>
          <p className="text-xs mt-1 text-slate-500">원본을 새 탭에서 열어 확인하세요.</p>
        </div>
        <a
          href={url}
          target="_blank"
          rel="noreferrer"
          className="text-xs px-4 py-2 rounded-lg border border-emerald-500/30 bg-emerald-500/10 text-emerald-300 hover:bg-emerald-500/20 transition-colors"
        >
          원본 열기 ↗
        </a>
      </div>
    );
  }

  if (kind === 'video') {
    return (
      <div className="absolute inset-0 flex items-center justify-center p-4">
        <video src={url} controls className="max-h-full max-w-full rounded-lg shadow-2xl" onError={() => setFailed(true)} />
      </div>
    );
  }

  return (
    <div className="absolute inset-0 flex items-center justify-center p-4">
      <img
        src={url}
        alt={path}
        loading="lazy"
        className="max-h-full max-w-full object-contain rounded-lg shadow-2xl"
        onError={() => setFailed(true)}
      />
    </div>
  );
}

function Center({ text, spin }: { text: string; spin?: boolean }) {
  return (
    <div className="flex flex-col items-center justify-center h-full p-20 gap-3 text-slate-500">
      <Loader2 className={spin ? 'w-10 h-10 animate-spin' : 'w-10 h-10'} />
      <p className="text-sm">{text}</p>
    </div>
  );
}
