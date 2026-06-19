/**
 * data.js — 다운로드 항목 정의. 실제 배포 링크는 href 를 채워 주세요.
 * - options: 카드의 다운로드 버튼들 (primary: true 면 주요 액션으로 강조)
 * - command: 있으면 복사 가능한 커맨드 블록을 렌더 (Docker 등)
 */
window.IA_DOWNLOADS = [
  {
    id: 'windows',
    tabLabel: 'Windows',
    name: 'Windows 10 / 11',
    icon: 'windows.svg',
    desc: 'Windows 10 및 11 (64-bit) 지원',
    badge: '추천',
    version: 'Desktop 1.0.0',
    size: '약 120MB',
    options: [
      {
        label: '다운로드',
        sub: '.exe 설치 파일',
        href: 'downloads/instagram-analyze-windows-x64.exe',
        fileName: 'instagram-analyze-windows-x64.exe',
        primary: true,
      },
    ],
  },
  {
    id: 'mac',
    tabLabel: 'macOS',
    name: 'macOS',
    icon: 'mac.svg',
    desc: 'macOS 12 (Monterey) 이상 지원',
    badge: 'Mac',
    version: 'Desktop 1.0.0',
    size: '약 135MB',
    options: [
      {
        label: 'Apple Silicon',
        sub: 'M1 · M2 · M3 (arm64)',
        href: 'downloads/instagram-analyze-macos-arm64.dmg',
        fileName: 'instagram-analyze-macos-arm64.dmg',
        primary: true,
      },
      {
        label: 'Intel (x64)',
        sub: 'Intel 기반 Mac',
        href: 'downloads/instagram-analyze-macos-x64.dmg',
        fileName: 'instagram-analyze-macos-x64.dmg',
      },
    ],
  },
  {
    id: 'docker',
    tabLabel: 'Docker',
    name: 'Docker',
    icon: 'docker.svg',
    desc: '어떤 OS에서도 컨테이너로 실행',
    badge: 'Container',
    version: 'latest',
    size: '이미지 설치',
    command: 'docker run -d -p 8080:8080 instagram-analyze:latest',
    options: [
      {
        label: 'Docker Hub',
        sub: '이미지 보기',
        href: 'https://hub.docker.com/r/instagram-analyze/instagram-analyze',
      },
    ],
  },
];
