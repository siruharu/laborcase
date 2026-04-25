# laborcase frontend

Next.js 16 (App Router) + TypeScript + Tailwind v4 + shadcn/ui 기반 프론트엔드.
laborcase 백엔드 (`/api/v1/articles/search`, `/api/v1/laws/...`) 를 호출해 한국 노동법 조문을 검색·열람한다.

## Stack

- Next.js 16 (App Router, React 19)
- TypeScript (strict + `noUncheckedIndexedAccess`)
- Tailwind v4
- shadcn/ui (base-nova preset, zinc + teal-600)
- Pretendard GOV 폰트 (self-host 예정, FT-Task 2)

## Getting Started

```bash
cp .env.example .env.local
# .env.local 의 NEXT_PUBLIC_API_BASE_URL 을 백엔드 주소로 맞춘다 (로컬: http://localhost:8080)

npm install
npm run dev
# http://localhost:3000
```

백엔드 실행 방법은 저장소 루트의 `../README.md` 참고.

## Scripts

| 스크립트 | 설명 |
|---|---|
| `npm run dev` | 개발 서버 (port 3000) |
| `npm run build` | 프로덕션 빌드 |
| `npm run start` | 프로덕션 서버 |
| `npm run lint` | ESLint |
| `npx tsc --noEmit` | 타입 체크 |

## 디렉터리

```
src/
├── app/                # App Router (RSC)
├── components/
│   └── ui/             # shadcn/ui 생성 컴포넌트
└── lib/
    └── utils.ts        # cn() 등 유틸
```

## 관련 문서

- 분석: `{Vault}/10_Projects/laborcase/02_Analysis/2026-04-25_frontend-nextjs-legal-search-ux.md`
- 플랜: `{Vault}/10_Projects/laborcase/03_Plan/2026-04-25_frontend-nextjs-legal-search-ux.md`
- Distance 임계값 PoC: `../docs/research/distance-thresholds.md`
