# 노동법 판례 검색 서비스

> 한국 노동자가 본인 상황과 유사한 판례/판정례를 찾아볼 수 있는 정보 제공 플랫폼.
> 법률 자문이 아닌 공개 정보의 구조화된 제시가 목적.

## 프로젝트 컨텍스트

### 왜 만드는가
- 노무사 상담은 30분 10만원 수준, 사전 정보 탐색 수요가 큼
- 기존 노무사는 사업주 고객 비중이 높아 근로자 친화 정보 제공에 한계
- 판례/판정례는 공개되어 있으나 일반인이 접근·해석하기 어려움

### 무엇을 만드는가
- 상황 입력 → 유사 판례/판정례 검색 → 통계/체크리스트 제공
- LLM 기반 RAG 로 자연어 검색과 쟁점 매칭 수행
- 사용자의 개인 판단을 돕는 **정보 제공** 도구 (자문 아님)

### 무엇을 만들지 않는가
- 법률 자문/상담 기능
- 변호사·노무사 매칭/알선 기능
- 승소 여부 예측 기능
- 유료 프리미엄 기능 (MVP 단계)

## MVP 범위

### 분야
- **부당해고** (근로기준법 제23조, 제27조, 제28조 중심)
- **임금체불** (근로기준법 제36조, 제43조 중심)
- 이외 분야는 로드맵에 있으나 MVP 에 포함하지 않음

### 기능
1. 상황 입력 (자연어 + 선택적 구조화 폼)
2. 유사 사례 top 5 검색 결과 제시
3. 관련 근로기준법 조항 자동 매칭
4. 유사 사건 결과 통계 (근로자 승/패 분포)
5. 분야별 체크리스트 (증거 수집, 신고 기한, 필요 서류)

### 성공 기준
- 검색 top-5 내 관련 판례 포함률 70% 이상 (수동 평가)
- 응답 시간 P95 3초 이내
- LLM 환각으로 인한 잘못된 법 조항 언급 0건 (모니터링)

## 법적 제약 (위반 시 서비스 중단)

### 표현 제약
- "판단", "판정", "승소 가능", "이길 수 있다", "질 수 있다" 등 단정 표현 금지
- 허용 표현: "유사 사례", "통계", "분포", "일반적으로", "원문 참고"
- 모든 응답에 근거 원문 링크 필수 포함

### 기능 제약
- 변호사법 제34조 저촉 방지: 특정 전문가 추천/매칭/알선 금지
- 변호사법 제109조 저촉 방지: 유료 법률 상담 서비스 금지
- 공인노무사법 저촉 방지: 노동관계법령 신고·신청·진술 대행 금지

### 데이터 제약
- 개인 민감정보 서버 저장 금지 (상황 입력은 세션 한정)
- 비실명화 안 된 판례 데이터 저장 금지
- 수집 대상 사이트의 robots.txt 및 이용약관 준수 필수

### UI 필수 요소
- 모든 결과 페이지 하단에 디스클레이머 고정 표시
- 문구: "본 정보는 공개된 판례 및 법령에 기반한 참고 자료이며, 법률 자문이 아닙니다. 구체적 사건은 반드시 변호사·노무사와 상담하세요."
- 유료 기능 도입 시 재검토 필요

## 기술 스택

### Frontend
- Next.js 15 (App Router)
- TypeScript strict mode
- TailwindCSS v4
- shadcn/ui
- 배포: Cloud Run (또는 Vercel)

### Backend API
- Kotlin 2.x
- Spring Boot 3.x
- Spring Data JPA
- PostgreSQL 16 + pgvector extension
- 배포: Cloud Run

### AI Server
- Python 3.12
- FastAPI
- LangChain
- OpenAI API 또는 Upstage Solar API
- 배포: Cloud Run

### Infrastructure
- Google Cloud Platform
- Cloud Run (서버리스 컨테이너)
- Cloud SQL (PostgreSQL)
- Cloud Storage (원본 데이터 보관)
- Terraform (IaC)
- GitHub Actions (CI/CD)

### Observability
- Cloud Logging
- Sentry (에러 트래킹)
- PostHog (제품 분석, 민감 정보 제외)

## 디렉토리 구조

```
/frontend           Next.js 앱
/api                Spring Boot 앱
/ai                 Python FastAPI
  /prompts          시스템 프롬프트 (private 레포에 분리)
/pipeline           데이터 수집/전처리 배치 스크립트
  /collectors       수집 스크립트 (소스별)
  /processors       전처리/태깅/임베딩 스크립트
  /raw              원본 데이터 (gitignore)
/infra              Terraform, Dockerfile
/docs               프로젝트 문서
  /research         Research 단계 산출물
  /analysis         Analysis 단계 산출물
  /plans            Plan 단계 산출물
  /decisions        ADR (아키텍처 의사결정 기록)
/.claude            Claude Code 설정
  /agents           서브에이전트 정의
```

## 코드 컨벤션

### 공통
- 커밋: Conventional Commits (feat:, fix:, chore:, docs:, refactor:, test:)
- 브랜치: main, develop, feature/*, fix/*
- PR: 스쿼시 머지, 리뷰 없이는 머지 금지

### Kotlin
- ktlint 적용
- Explicit API mode
- Data class 적극 활용
- Null safety 엄격 준수

### Python
- ruff + black
- Type hint 필수
- Pydantic v2 로 DTO 정의

### TypeScript
- eslint + prettier
- strict mode
- any 금지, unknown 사용

## 작업 파이프라인

이 프로젝트는 4단계 파이프라인으로 모든 개발을 진행합니다.
각 단계는 전용 서브에이전트가 담당하며, 이전 단계의 산출물을 입력으로 받습니다.

```
Research → Analysis → Plan → Implementation
    ↓          ↓         ↓          ↓
docs/research  docs/analysis  docs/plans  code + tests
```

### 1. Research (researcher)
- 외부 정보, 기술 조사, 선행 사례 수집
- 출력: `/docs/research/YYYY-MM-DD-{topic}.md`
- 원칙: 사실만 기록, 단정 결론 금지

### 2. Analysis (analyst)
- 리서치를 트레이드오프 분석으로 변환
- 출력: `/docs/analysis/YYYY-MM-DD-{topic}.md`
- 원칙: 평가 기준 먼저, 옵션 비교, 결정 포인트 추출

### 3. Plan (planner)
- 분석 결과를 작업 단위로 분해
- 출력: `/docs/plans/YYYY-MM-DD-{feature}.md`
- 원칙: 1-4시간 아토믹 작업, 수용 기준 명시, 체크포인트 포함

### 4. Implementation (implementer)
- 계획에 따라 실제 코드 작성
- 출력: 코드 + 테스트 + 커밋
- 원칙: TDD, 계획 이탈 금지, 검증 이중화

### 파이프라인 규칙

1. **단계 건너뛰기 금지**: 리서치 없이 분석 시작 금지, 계획 없이 구현 시작 금지
2. **사용자 gate**: 각 단계 종료 시 사용자 리뷰, 다음 단계 진행 승인 필요
3. **역방향 이동 가능**: 구현 중 정보 부족 발견 시 researcher 로 돌아가기 허용
4. **단계별 격리**: 각 서브에이전트는 독립 컨텍스트, 메인 세션에는 요약만 반환

## Claude Code 가 따를 원칙

1. 단계에 맞는 서브에이전트 사용 (researcher → analyst → planner → implementer)
2. 법적 제약 관련 작업 시 CLAUDE.md 재확인
3. 신규 의존성 추가 전 반드시 사용자 확인
4. 민감 정보 (API 키, DB 비밀번호) 를 코드에 직접 쓰지 않음
5. 테스트 없는 기능 코드 작성 금지 (TDD)
6. 계획에 없는 기능 임의 추가 금지

## 사용자가 따를 원칙

1. Claude Code 가 법적 경계를 벗어나는 제안을 하면 즉시 중단
2. 데이터 수집 스크립트는 반드시 로컬에서 dry-run 후 실행
3. 프로덕션 DB 에는 Claude Code 접근 권한 부여 금지
4. 시스템 프롬프트는 private 레포로 분리 관리
5. 각 파이프라인 산출물 (research/analysis/plan) 을 Git 에 커밋하여 추적

## 서브에이전트 파일

- `.claude/agents/researcher.md` — Research 단계
- `.claude/agents/analyst.md` — Analysis 단계
- `.claude/agents/planner.md` — Plan 단계
- `.claude/agents/implementer.md` — Implementation 단계

각 서브에이전트는 독립된 컨텍스트에서 실행되며 역할별 제약이 정의되어 있다.

## 로드맵 (참고용, MVP 이후)

- **Phase 2**: 산업재해, 직장 내 괴롭힘, 성희롱 분야 추가
- **Phase 3**: 판례 변화 추이 시각화, 법 개정 영향 분석
- **Phase 4**: 지역별 노무사·변호사 광고 (알선 아닌 광고 모델)
- **Phase 5**: 사용자 커뮤니티 (본인 사례 공유, 변호사 검증)

## 참고 문서

- `/docs/architecture.md` — 시스템 아키텍처 상세
- `/docs/legal-boundaries.md` — 법적 경계 및 판단 기준
- `/docs/data-sources.md` — 데이터 소스 및 라이선스
- `/docs/decisions/` — ADR (아키텍처 의사결정 기록)
