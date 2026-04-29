# 프론트엔드 통합 smoke — 2026-04-27

- **작성일**: 2026-04-27
- **방법**: Playwright + 별도 mock backend (`tests/e2e/mock-server.ts`) 로 RSC fetch 까지 가로채 시각·동작 검증.
- **계기**: FT-Task 11 (`03_Plan/2026-04-25_frontend-nextjs-legal-search-ux.md` §Task 11).
- **CLAUDE.md 정책**: 사용자가 따를 원칙 §3 — "프로덕션 DB 에는 Claude Code 접근 권한 부여 금지". Cloud SQL 직접 접근 대신 fixture mock 으로 검증.

## 자동 시나리오 결과

`npx playwright test visual-smoke` — 14/14 통과 (5.9s).

| # | 시나리오 | desktop | mobile | 캡처 |
|---|---|---|---|---|
| 1 | 랜딩 — hero + 6 법령 카드 + footer | ✅ | ✅ | `landing-{project}.png` |
| 2 | 랜딩 stale=true — amber 배너 + KST 변환 | ✅ | ✅ | `landing-stale-{project}.png` |
| 3 | 검색 결과 — 5 hits + 관련도 라벨 분포 | ✅ | ✅ | `search-results-{project}.png` |
| 4 | 검색 빈 결과 — 검색 팁 3장 | ✅ | ✅ | `search-empty-{project}.png` |
| 5 | 카드 클릭 → 상세 — ArticleTree (① 항 + 1. 호 + 가/나 목) | ✅ | ✅ | `article-detail-{project}.png` |
| 6 | 상세 stale=true — amber 배너 + ArticleTree | ✅ | ✅ | `article-stale-{project}.png` |
| 7 | not-found — 빈 articles 응답 (jo=9999) → 404 | ✅ | ✅ | (캡처 없음) |

캡처 경로: `frontend/tests/e2e/__captures__/` (gitignore — 재현 시 자동 생성).

## 시각 검증 핵심

### 4-layer disclaimer 모두 노출
1. **Footer (모든 페이지)** — "본 서비스는 법제처 국가법령정보센터의 법령 데이터를 공공누리 제1유형(출처표시)에 따라 이용합니다." + CLAUDE.md 풀텍스트 디스클레이머 + © 2026 laborcase · Apache-2.0. 데스크톱/모바일 모두 동일.
2. **Inline (검색 결과 + 랜딩 법령 섹션 상단)** — "아래 결과는 법제처 국가법령정보(공공누리 제1유형)에 기반한 참고 자료입니다. 자세한 내용은 원문 보기 ↗." 회색 작은 글씨.
3. **DetailBox (조문 상세)** — "이 조문은 2025년 10월 23일 시행 버전입니다. 원문 보기 ↗." 박스 (muted 배경).
4. **StaleBanner (조건부, freshness.stale=true)** — amber 배경 + ⚠ + "현재 노출된 법령 정보는 2026년 4월 23일 오전 03:00 이후 최신화되지 못했습니다. 반드시 법제처 국가법령정보센터의 원문과 대조하세요." `role="alert"`. dismiss 불가.

### 관련도 라벨 분포 (분석 §P3-d, PoC 임계값)
fixture distance 5 건:
- 0.5407 (제28조) → **관련도 높음** (brand teal-600 채움)
- 0.6095 (제23조) → **관련도 높음**
- 0.6712 (제27조) → 관련도 보통 (outline)
- 0.7012 (제26조) → 관련도 보통
- 0.7501 (제30조) → 라벨 미표시 (≥ 0.72)

→ 임계값 0.65 / 0.72 의도대로 적용. distance 수치는 노출 안 함 (CLAUDE.md 단정 표현 회피).

### 모바일 가독성
- viewport 393×727 (Pixel 7) 에서 모든 시나리오 가로 스크롤 없음.
- ArticleTree 의 항/호/목 들여쓰기가 모바일에서도 짧은 padding 으로 자연스럽게 표시 — 본문 영역 좁아지는 문제 없음.
- `word-break: keep-all` (KRDS 글로벌 스타일) 로 어절 단위 줄바꿈 정상.

### KST 시간 변환
ADR-0002 의 `lastSyncedAt: "2026-04-22T18:00:00Z"` → "2026년 4월 23일 오전 03:00" (KST). `Intl.DateTimeFormat("ko-KR", { timeZone: "Asia/Seoul" })` 정상 동작.

## 한계 + 후속 작업

### 자동화에서 검증되지 않은 항목 (사용자 환경 필요)
- 실제 backend (Spring Boot + Cloud SQL pgvector + Upstage embed) 응답 형태와 fixture 의 schema 일치 — fixture 는 백엔드 코드 (LawReadDto 등) 를 보고 작성했지만 라이브 응답과 다를 가능성 0 은 아님.
- Cloud Run cold start 영향 (분석 §검증필요) — 첫 요청 응답시간 측정.
- iOS Safari 정확 검증 — Pixel 7 (chromium) 으로만 자동화. 분석 §R9 모바일 키보드 가림은 실 기기에서 한 번 더.
- CORS preflight — backend `@CrossOrigin("\${cors.allowed-origins}")` 의 prod 도메인 설정 시.

### Follow-up
- [ ] 사용자 환경에서 `./gradlew :api:bootRun` + `npm run dev` 띄운 상태로 PoC 5~10 쿼리 (research/distance-thresholds.md) 한 번 시도.
- [ ] CI 파이프라인 — backend mock 또는 staging Cloud Run 인스턴스 띄워 PR 마다 visual-smoke 자동 실행 (별도 ADR).
- [ ] mock-server fixture 가 API contract 와 어긋나지 않도록 schema 자동 동기화 (zod 또는 OpenAPI) 검토.

## 구성 변경

이번 검증 과정에서 두 가지 구성을 추가:

1. **`src/lib/api.ts` revalidate env override** — `LABORCASE_API_REVALIDATE` 환경변수로 RSC fetch 캐시 TTL 제어. 프로덕션 기본 60초, 테스트 0초. mock state 토글 직후 페이지 새로고침이 캐시된 응답을 보지 않게 하기 위함.
2. **`playwright.config.ts` webServer 두 개** — mock-server (port 18080) + next dev (port 3000). dev 서버의 `NEXT_PUBLIC_API_BASE_URL` 을 mock 으로 가리키게 env 주입.

## 참고
- 플랜: `{Vault}/10_Projects/laborcase/03_Plan/2026-04-25_frontend-nextjs-legal-search-ux.md`
- 분석: `{Vault}/10_Projects/laborcase/02_Analysis/2026-04-25_frontend-nextjs-legal-search-ux.md`
- ADR-0002 (stale 배너): `docs/decisions/adr-0002-stale-data-banner.md`
- distance PoC: `docs/research/distance-thresholds.md`
