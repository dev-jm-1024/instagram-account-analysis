/**
 * UI/routing 도메인 타입. 데이터 모델은 백엔드 DTO(`services/api/types.ts`)로 일원화됐고,
 * 이 파일은 탭/내비게이션 식별자만 보유한다. (프레임워크 비의존)
 */

export type ActiveTab =
  | 'overview'
  | 'followers'
  | 'dm'
  | 'activity'
  | 'heatmap'
  | 'search'
  | 'security'
  | 'misc_logs'
  | 'explorer'
  | 'uploader';

export type NavGroup = '핵심' | '관계' | '활동' | '계정' | '도구';
