/** 백엔드 ErrorCode → 사용자용 한글 메시지. 분기는 code 기준. */
import { ApiError } from './client';

const MESSAGES: Record<string, string> = {
  IMPORT_PATH_BLANK: '경로를 입력해 주세요.',
  OWNER_INPUT_BLANK: '본인 username 을 입력해 주세요.',
  VALIDATION_FAILED: '입력값을 확인해 주세요.',
  IMPORT_PATH_NOT_FOUND: '해당 경로를 찾을 수 없습니다.',
  IMPORT_PATH_NOT_DIRECTORY: '폴더 경로가 아닙니다.',
  IMPORT_NOT_INSTAGRAM_EXPORT: '인스타그램 내보내기 폴더가 아닙니다. JSON 형식으로 다시 받아 주세요.',
  IMPORT_HTML_ONLY: 'HTML 내보내기는 분석할 수 없습니다. JSON 형식으로 다시 받아 주세요.',
  DM_OWNER_NOT_RESOLVED: '본인 계정을 확정해야 DM 통계를 볼 수 있습니다.',
  UPLOAD_IN_PROGRESS: '업로드/추출이 진행 중입니다. 잠시 후 다시 시도해 주세요.',
  IMPORT_REIMPORT_REQUIRED: '원본 폴더가 없어 재임포트가 필요합니다.',
  IMPORT_NOT_COMPLETED: '먼저 데이터를 임포트해 주세요.',
  EXPLORER_PATH_OUT_OF_ROOT: '임포트 폴더 밖의 경로는 열 수 없습니다.',
  EXPLORER_FILE_NOT_FOUND: '파일을 찾을 수 없습니다.',
  INTERNAL_ERROR: '알 수 없는 오류가 발생했습니다.',
};

export function errorMessage(error: unknown, fallback = '오류가 발생했습니다.'): string {
  if (error instanceof ApiError) {
    return MESSAGES[error.code] ?? error.message ?? fallback;
  }
  if (error instanceof Error) return error.message || fallback;
  return fallback;
}

export function errorCode(error: unknown): string | null {
  return error instanceof ApiError ? error.code : null;
}
