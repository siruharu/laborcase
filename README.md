# laborcase — 노동법 판례 검색 서비스 (가칭)

[![공공누리 제1유형](https://www.kogl.or.kr/images/front/sub/img_opentype01_m.jpg)](https://www.kogl.or.kr/info/licenseType1.do) [![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](./LICENSE)

> **법률 자문이 아닌 정보 제공 서비스입니다.** 본 프로젝트는 한국 공개 판례와 법령을 구조화하여 노동자가 본인 상황과 유사한 사례를 찾아볼 수 있게 돕는 도구입니다. 구체적 사건에 대한 판단은 반드시 변호사 또는 공인노무사와 상담하세요.

본 서비스는 [법제처 국가법령정보센터](https://www.law.go.kr) 가 **공공누리 제1유형** 으로 개방한 법령 정보를 이용합니다. 상세는 [docs/legal/source-attribution.md](./docs/legal/source-attribution.md).

## 왜 만들었나

- 노무사 상담은 30분 10만원 수준, 그 전에 "싸울 만한 상황인가" 를 알고 싶은 수요가 크다
- 기존 노무사는 사업주 고객 비중이 높아 근로자 친화 정보 제공에 한계가 있다
- 판례와 판정례는 공개되어 있지만 일반인이 접근하고 해석하기 어렵다

## 무엇을 하는가

- 자연어로 상황을 입력하면 유사한 판례/판정례를 찾아준다
- 관련 근로기준법 조항을 매칭해준다
- 유사 사건의 결과 분포를 통계로 보여준다
- 분야별 체크리스트 (증거, 기한, 서류) 를 제공한다

## 무엇을 하지 않는가

- 승소 여부를 예측하지 않는다
- 특정 변호사나 노무사를 추천하지 않는다
- 법률 상담을 대체하지 않는다
- 서류 작성을 대행하지 않는다

## 기술 스택

### Frontend
- Next.js 15, TypeScript, TailwindCSS v4, shadcn/ui

### Backend API
- Kotlin, Spring Boot 3, PostgreSQL 16 + pgvector

### AI Server
- Python 3.12, FastAPI, LangChain

### Infrastructure
- Google Cloud Platform (Cloud Run, Cloud SQL, Cloud Storage)
- Terraform, GitHub Actions

## 아키텍처

4개 파이프라인으로 구성:

1. **데이터 수집**: 국가법령정보센터 API, 대법원 판례 크롤링, 중앙노동위원회 판정례 수집
2. **전처리/임베딩**: 원문 파싱, LLM 메타데이터 태깅, 벡터 임베딩 생성
3. **검색 RAG**: 3단계 (쿼리 구조화 → 벡터 검색 → 재순위화)
4. **사용자 UI**: Next.js 프론트엔드, 모바일 우선

상세: [docs/architecture.md](./docs/architecture.md)

## 법적 경계

이 서비스는 다음 법적 제약 하에 설계됩니다:

- 변호사법 제34조, 제109조 준수: 사건 알선·법률 상담 대행 금지
- 공인노무사법 제27조 준수: 노동 관련 신고·신청 대행 금지
- 개인정보보호법 준수: 민감 정보 수집 최소화, 서버 저장 금지

상세: [docs/legal-boundaries.md](./docs/legal-boundaries.md)

## 데이터 출처

- [국가법령정보센터](https://www.law.go.kr)
- [대법원 종합법률정보](https://glaw.scourt.go.kr)
- [중앙노동위원회](https://www.nlrc.go.kr)
- 고용노동부 행정해석

모든 검색 결과는 원문 링크를 포함합니다.
상세: [docs/data-sources.md](./docs/data-sources.md)

## 로컬 개발

### 요구 사항
- Docker Desktop
- Node.js 20+
- JDK 21
- Python 3.12+
- PostgreSQL 16 (Docker 로 실행 권장)

### 시작하기

```bash
# 저장소 클론 (submodule 접근 권한이 있을 때만 ai/prompts/ 가 채워진다)
git clone --recurse-submodules https://github.com/siruharu/laborcase.git
cd laborcase

# 한 줄로 환경 점검·pre-commit 설치
./scripts/bootstrap.sh

# 상세 서비스 실행은 각 하위 프로젝트 README 참조 (frontend/api/ai)
```

외부 기여자 등 `ai/prompts/` submodule 접근 권한이 없는 경우에도 빌드는 동작합니다. 빌드 시스템이 `ai/prompts.example/` 를 폴백으로 로드하며, 민감한 실제 운영 프롬프트는 [laborcase-internal](https://github.com/siruharu/laborcase-internal) private 레포에 관리됩니다.

### 브랜치 전략

- `main` — 릴리스/안정본. 직접 push 금지, PR + CODEOWNERS 리뷰 필수
- `dev` — 일상 작업 브랜치. `feature/*` → `dev` → `main` 순
- 상세: [docs/decisions/adr-0001-repo-split.md](./docs/decisions/adr-0001-repo-split.md)

## 배포 (prod = GCP Cloud Run)

```bash
# 정상 케이스 (95%): suffix tag 푸시
git tag v0.1.X-api v0.1.X-fe
git push origin v0.1.X-api v0.1.X-fe
```

GitHub Actions 가 keyless WIF 인증으로 GCP 에 접근, Cloud Build 빌드 후 Cloud Run revision 갱신. 약 5-10분.

| 항목 | 위치 |
|---|---|
| 첫 셋업 / 트러블슈팅 / 롤백 | [`docs/runbooks/deploy.md`](./docs/runbooks/deploy.md) |
| 배포 결정 박제 | [`adr-0004-cloud-run-prod-deploy.md`](./docs/decisions/adr-0004-cloud-run-prod-deploy.md) |
| 인프라 (terraform) | [`infra/terraform/README.md`](./infra/terraform/README.md) |

현재 prod URL:
- API: `https://laborcase-api-mxq42pqgaa-du.a.run.app`
- Frontend: `https://laborcase-frontend-mxq42pqgaa-du.a.run.app`

## 기여

이 프로젝트는 법률 정보 접근성 개선을 목표로 합니다. 기여 환영합니다.

- 이슈 제보: GitHub Issues
- 판례 분류 오류 지적: PR 또는 이슈
- 기능 제안: Discussions

단, 다음은 받지 않습니다:
- 특정 법률 해석 관점을 강제하는 변경
- 광고/마케팅 목적의 콘텐츠 삽입
- 유료 기능 추가 제안

## 라이선스

**코드**: [Apache License 2.0](./LICENSE)

**법령 원문 데이터**: 법제처 국가법령정보센터 — [공공누리 제1유형 (출처표시)](https://www.kogl.or.kr/info/licenseType1.do). 본 레포는 해당 데이터를 가공·저장하지 않으며, 실시간 또는 배치 수집한 원문은 이용 시점의 출처 링크와 함께 제시됩니다.

이 라이선스는 본 코드의 사용·수정·재배포를 허용합니다. 단, 본 서비스를 상업적으로 복제 운영할 경우 법적 책임은 운영자에게 있습니다.

## 면책 조항

본 프로젝트는 공개된 판례 및 법령에 기반한 정보 제공 도구이며, 어떠한 법률 자문도 제공하지 않습니다. 본 서비스를 통해 얻은 정보로 인한 결과에 대해 프로젝트 운영자는 책임지지 않습니다. 모든 법적 판단은 자격 있는 전문가와 상의하십시오.

## 연락처

- 버그 제보: GitHub Issues
- 일반 문의: [이메일]
- 데이터 오류/개인정보 제보: [이메일]
