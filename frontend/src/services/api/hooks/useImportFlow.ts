/**
 * 임포트 진입 상태머신: ZIP 업로드(XHR 진행률) → 추출 폴링 → ETL 임포트 폴링 → 완료.
 * 자동 탐지(candidates)·수동 경로도 같은 startImport 로 합류한다.
 * 실패는 HTTP 에러가 아니라 각 status 의 FAILED + message 로 감지한다.
 *
 * phase 는 저장하지 않고 mutation/query 상태에서 파생한다(effect 내 setState 회피).
 * 유일한 effect 는 "추출 완료 시 import 1회 트리거"이며 setState 가 아니라 mutate 를 호출한다.
 */
import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../endpoints';
import { queryKeys } from '../queryKeys';
import { ApiError } from '../client';
import type { ImportStatusResponse, UploadStatusResponse } from '../types';

export type FlowPhase =
  | 'idle'
  | 'uploading'
  | 'extracting'
  | 'importing'
  | 'completed'
  | 'failed';

const POLL_MS = 1000;
const isTerminal = (s: string) => s === 'COMPLETED' || s === 'FAILED';

export function useImportFlow() {
  const qc = useQueryClient();
  const [uploadPct, setUploadPct] = useState(0);

  const candidatesQuery = useQuery({
    queryKey: queryKeys.candidates,
    queryFn: () => api.candidates(),
  });

  const uploadMutation = useMutation({
    mutationFn: (file: File) => {
      setUploadPct(0);
      return api.uploadExport(file, setUploadPct);
    },
  });
  const startMutation = useMutation({
    mutationFn: (path: string) => api.startImport(path),
  });
  const resolveOwnerMutation = useMutation({
    mutationFn: (username: string) => api.resolveOwner(username),
  });

  const uploadDone = uploadMutation.isSuccess;
  const latestUpload: UploadStatusResponse | undefined =
    uploadMutation.data; // 초기 응답 (EXTRACTING/COMPLETED)

  // 추출 폴링: 업로드 직후 status 가 비-terminal 인 동안만.
  const uploadStatusQuery = useQuery({
    queryKey: queryKeys.uploadStatus,
    queryFn: () => api.uploadStatus(),
    enabled: uploadDone && !!latestUpload && !isTerminal(latestUpload.status),
    refetchInterval: (q) =>
      q.state.data && isTerminal((q.state.data as UploadStatusResponse).status) ? false : POLL_MS,
  });

  const upload = uploadStatusQuery.data ?? latestUpload ?? null;
  const extractDone = upload?.status === 'COMPLETED';
  const extractFailed = upload?.status === 'FAILED';

  const importStarted = startMutation.isPending || startMutation.isSuccess;

  // 임포트 폴링: startImport 성공 후 status 가 비-terminal 인 동안만.
  const importStatusQuery = useQuery({
    queryKey: queryKeys.importStatus,
    queryFn: () => api.importStatus(),
    enabled: startMutation.isSuccess,
    refetchInterval: (q) =>
      q.state.data && isTerminal((q.state.data as ImportStatusResponse).status) ? false : POLL_MS,
  });

  // 이 세션에서 실제로 import 를 시작했을 때만 import 상태를 반영한다.
  // (importStatus 캐시는 Sidebar 등과 공유돼 이미 COMPLETED 일 수 있음 → 그걸 phase 로 오판하면
  //  Uploader 진입 즉시 '완료'로 보고 overview 로 튕긴다.)
  const importData = startMutation.isSuccess
    ? (importStatusQuery.data ?? startMutation.data ?? null)
    : null;
  const importDone = importData?.status === 'COMPLETED';
  const importFailed = importData?.status === 'FAILED' || startMutation.isError;

  // 추출 완료 → import 1회 자동 트리거 (mutate 호출, setState 아님)
  useEffect(() => {
    if (uploadDone && extractDone && startMutation.isIdle && upload?.targetPath) {
      startMutation.mutate(upload.targetPath);
    }
  }, [uploadDone, extractDone, upload?.targetPath, startMutation]);

  const failed = uploadMutation.isError || extractFailed || importFailed;
  const phase: FlowPhase = failed
    ? 'failed'
    : importDone
      ? 'completed'
      : importStarted
        ? 'importing'
        : uploadMutation.isPending
          ? 'uploading'
          : uploadDone
            ? 'extracting'
            : 'idle';

  const error =
    uploadMutation.error instanceof ApiError
      ? uploadMutation.error
      : startMutation.error instanceof ApiError
        ? startMutation.error
        : null;
  const message = extractFailed ? (upload?.message ?? null) : null;

  const reset = () => {
    uploadMutation.reset();
    startMutation.reset();
    setUploadPct(0);
    qc.removeQueries({ queryKey: queryKeys.uploadStatus });
    qc.removeQueries({ queryKey: queryKeys.importStatus });
  };

  return {
    phase,
    uploadPct,
    message: message ?? null,
    error,
    candidates: candidatesQuery.data ?? null,
    candidatesLoading: candidatesQuery.isLoading,
    uploadStatus: upload,
    importStatus: importData,
    uploadZip: (file: File) => uploadMutation.mutate(file),
    importFromPath: (path: string) => startMutation.mutate(path),
    resolveOwner: resolveOwnerMutation,
    reset,
  };
}
