---
type: analysis
project: laborcase
topic: public/private 레포 분리 전략
created: 2026-04-24 10:30
status: done
tags:
  - analysis
  - laborcase
  - git
  - github
  - security
  - ci-cd
source_research:
  - "[2026-04-24_public-private-repo-split](./2026-04-24_public-private-repo-split.md)"
next_mode: plan
---

# 🔬 분석: laborcase public/private 레포 분리 전략

## 다루는 문제
laborcase 의 **코드·문서·민감 자산**을 어떤 GitHub 레포 구조로 나눠 담을지 결정한다. 범위:
1. 어떤 자산이 private 이어야 하는가(선)
2. 나눈 뒤 어떤 구조로 묶을 것인가(4개 옵션 중 선택)
3. CI/CD 가 두 레포를 어떻게 인증·조립할 것인가
4. 로컬 개발·실수 방지·사고 복구 가드레일

## 전제 / 가정

- **A1. 출발점**: 아직 GitHub 원격 레포가 없고 로컬 `/Users/zephyr/dev/laborcase` 는 **비-git 상태**. 즉 "이미 커밋된 민감 자산을 뽑아내는" 복구 시나리오가 아니라 **처음부터 올바르게 구성**할 기회.
- **A2. 민감 자산의 실질**: `CLAUDE.md` 에 명시된 `/ai/prompts` 가 최소 집합. 프롬프트 골든셋·체크리스트 최종본·법령↔판례 매핑 휴리스틱도 후보.
- **A3. 공개 지향성**: CLAUDE.md 에 "오픈소스" 선언은 없다. 단 사용자 요청("public 으로 둘 만한 것들"은 공개)과 법적 표현 제약(CLAUDE.md §표현 제약) 준수를 외부가 검증 가능해야 한다는 가치에서 **public 기본** 이 합리적.
- **A4. 배포**: Cloud Run 컨테이너 이미지에 프롬프트를 **빌드 타임** 에 포함해 배포. 런타임 동적 로드는 MVP 범위 밖(복잡도 증가, 캐시 무효화 이슈).
- **A5. 프롬프트 변경 빈도**: MVP 초기 주 수 회, 안정화 후 주 1회 이하로 가정. Git PR 기반 리뷰가 유의미한 주기.
- **A6. 팀 규모**: 현재 Zephyr 1인 + Claude Code. 팀 확장 대비 설정이 "스케일 아웃 가능" 해야 하나 초기 복잡도는 낮아야 함.
- **A7. GitHub 플랜**: private 레포 무제한 허용(Free 플랜 이후 기본). 추가 비용 없음.

## 문제 분해

1. **P1. 분리 선(line-of-split) 정의** — 어떤 경로·자산이 private 인가. 잘못 그으면 후속 비용이 전부 커진다.
2. **P2. 레포 구조 선택** — 리서치의 A/B/C/D 중 어느 것.
3. **P3. CI/CD 인증 방식** — Deploy Key vs PAT vs GitHub App.
4. **P4. 로컬 개발 워크플로** — 신규 개발자가 1시간 안에 빌드할 수 있어야 한다.
5. **P5. 실수 방지 가드레일** — pre-commit, push protection, CODEOWNERS.
6. **P6. 사고 복구 플레이북** — 실수 커밋 발생 시 절차 (히스토리 제거 + 즉시 로테이션).
7. **P7. 파이프라인 산출물(Research/Analysis/Plan) 공개 범위** — CLAUDE.md 사용자 원칙 5번 "Git 커밋하여 추적" 의 저장 위치.

---

## P1. 분리 선 정의

### 분류 규칙 (결정)

**Private 에 두는 것:**
1. **`ai/prompts/**`** — CLAUDE.md 명시.
2. **프롬프트 회귀 테스트/골든셋** (`ai/tests/prompt/**` 또는 프롬프트 레포에 동거).
3. **법령↔판례 매핑 휴리스틱/규칙 테이블 최종본** — 경쟁사 복제 방지 + 법적 표현 검수가 끝난 "확정본".
4. **분야별 체크리스트 최종 문구** — 법적 뉘앙스 관리 필요.
5. **운영 비밀이 섞인 인프라 변수** (`infra/*.tfvars`, 서비스계정 JSON) — 애초에 커밋하지 않음 (Secret Manager + `.gitignore`).

**Public 에 두는 것:**
1. `/frontend`, `/api`, `/ai`(프롬프트 제외), `/pipeline`, `/infra` (변수 제외 HCL), `/.claude/agents` (민감 지시문 제외).
2. `/docs/architecture`, `/docs/decisions` (ADR), `/docs/legal-boundaries`, `/docs/data-sources`.
3. `CLAUDE.md` — 프로젝트 의도 공개 자체가 법적 투명성을 뒷받침.
4. 수집 파이프라인의 **코드**. 수집 **결과물**은 원본 라이선스·비실명화 여부에 따라 별도 판단(분석 범위 밖, 별도 노트 필요).

**경계에 있는 것 (명시적으로 결정):**
| 자산 | 결정 | 이유 |
|---|---|---|
| 법령 원문 캐시 (raw XML, 정규화 테이블) | **커밋하지 않음** (Cloud Storage/Cloud SQL) | 수~수백 MB 규모, Git 적합 아님. 공공누리 1유형이라 공개 자체는 가능하나 저장 매체가 다름 |
| 프롬프트 **템플릿(구조)** vs **내용(문구)** | 템플릿은 public, 내용은 private | 템플릿만 보여도 아키텍처 투명성은 달성 |
| 에이전트 정의 `.claude/agents/*.md` | public (민감 지시문 마스킹) | 협업 문맥 공유 가치 |
| 데이터 소스 robots.txt/ToS 검토 로그 | public | 준수 근거 공개 |

> [!note] 결정 원칙
> "이 파일이 타인에게 그대로 복사되었을 때 **제품 가치가 희석되는가**, 혹은 **법적 표현 제약 우회에 악용 가능한가**" 둘 중 하나라도 해당하면 private.

---

## P2. 레포 구조 선택

### 옵션 비교

리서치 노트의 A~D 에 laborcase 조건을 대입한 실측 평가.

| 기준 (가중치) | A. Public 메인 + Private Submodule | B. Private 메인 + Public 미러 | C. 별도 레포 2개 (sparse+CI조립) | D. 런타임 로드 (GCS) |
|---|---|---|---|---|
| 민감 자산 유출 위험 (25%) | 낮음 | 중 (미러봇 버그 시 흘림) | 매우 낮음 | 매우 낮음 |
| 초기 설정 비용 (15%) | 중 (submodule + deploy key) | 낮음 | 높음 | 중 |
| 프롬프트 PR 리뷰 품질 (20%) | ✅ diff 완벽 | ✅ | ✅ | ❌ GCS 버전만 |
| 외부 공개/신뢰성 (15%) | 강함 | 약함 (미러 지연/결손) | 강함 | 강함 |
| 로컬 개발 온보딩 (10%) | 중 | 매우 낮음 | 높음 | 중 |
| CI 복잡도 (10%) | 중 (ssh-agent) | 낮음 (단일 레포) | 높음 | 낮음 |
| Phase 2 기여 유도 (5%) | 쉬움 | 어려움 | 쉬움 | 쉬움 |

### 결정
**옵션 A (Public 메인 + Private Submodule) 선택.**

**선택 이유 (리서치 근거):**
- laborcase 의 핵심 가치 중 하나가 **법적 표현 제약 준수의 투명성**. 메인을 public 으로 두는 순간 외부 검증이 가능해져 제품 신뢰도에 기여 (A3).
- 프롬프트가 주 단위로 바뀌는 초기에 **PR diff 기반 리뷰**가 필수 (A5). 옵션 D 는 이 워크플로를 잃음.
- 리서치에서 확인한 Cal.com 선례로 **운영 사례가 있는 패턴** (리스크 알려져 있음). submodule 의 알려진 단점(detached HEAD, 일부 도구 호환성)은 1인 팀 단계에서 치명적이지 않음.
- Deploy Key 는 per-repo 격리가 강해 PAT 보다 안전 (P3 에서 상세).

**선택하지 않은 이유:**
- **B**: 외부 공개가 미러 방식이면 공개본이 항상 한 박자 늦고, 미러봇이 놓친 파일이 나중에 끼어들면 사고. 공개된 레포에 대한 신뢰 자체가 떨어짐.
- **C**: 1인 팀에서 로컬 조립 복잡도(높음)가 과하다. CI 에서만 합쳐지면 개발자 로컬 실행이 번거롭다. 팀 3명 이상일 때 재고.
- **D**: 프롬프트 PR 리뷰 품질 상실이 결정적. 법적 표현 제약(단정 표현 금지, 원문 링크 필수 등)은 diff 리뷰로 잡아내는 편이 안전. 다만 **빌드 타임 배포 경로는 유지** 하면서 **Git 으로 프롬프트 관리** 하는 하이브리드(= 옵션 A) 가 이 장점만 취함.

---

## P3. CI/CD 인증 방식

### 옵션 비교

| 기준 | A. Deploy Key (SSH) + `webfactory/ssh-agent` | B. Personal Access Token (fine-grained) | C. GitHub App installation token |
|---|---|---|---|
| per-repo 권한 제어 | ✅ 1키=1레포 (양날의 검) | 🔶 organization fine-grained PAT 이면 가능 | ✅ |
| 키 개수 관리 | private 레포 N 개 → 키 N 개 | 1개로 여러 레포 커버 | 1개 App |
| 만료/로테이션 | 수동 | 최대 1년 자동만료 | GitHub App 비밀은 장기 |
| 외부 fork PR 접근 | 차단 (secret 차단됨) | 차단 | 제어 가능 |
| 설정 난이도 | 낮음 | 낮음 | 중 |
| 1인 팀 운용 부담 | 적정 | 적정 | 과잉 |

### 결정
**옵션 A (Deploy Key + `webfactory/ssh-agent`) 선택.**

**근거:**
- 리서치에서 2026 기준 베스트프랙티스로 확인. PAT 은 "사용자 전체 범위" 속성 때문에 사고 반경이 크다.
- laborcase 의 private 레포 수는 초기 1개(프롬프트) 수준이라 키 관리 부담 낮음.
- `webfactory/ssh-agent` 가 다중 키를 호스트별로 로드해줘 Phase 2 에서 private 레포가 추가되어도 확장 가능.
- fork PR 에서 secret 미주입이 기본 동작이라 외부 기여자가 private submodule 에 접근할 수 없음 — 의도에 부합.

**선택하지 않은 이유:**
- **B**: per-repo 격리가 형식적. fine-grained PAT 도 개인 계정 소유이므로 팀 확장 시 전환 필요.
- **C**: 현재 규모 대비 과잉. Phase 3 이상에서 다수 서비스 레포·다수 CI 가 생기면 재평가.

---

## P4. 로컬 개발 워크플로

### 결정

```
git clone --recurse-submodules git@github.com:zephyrous/laborcase.git
# (submodule 접근 권한이 있는 개발자만 ai/prompts/ 가 채워짐)
# (권한 없는 외부 기여자는 ai/prompts/ 가 비어있는 채로 빌드 가능해야 함)
```

**요구사항:**
- 빌드 스크립트는 `ai/prompts/` 가 비어도 "경고만 띄우고 **공개 샘플 프롬프트(`ai/prompts.example/`) 로 대체**" 해 정상 컴파일되어야 한다.
- Docker 빌드에서 실제 배포용 이미지는 submodule 내용을 COPY, 로컬/CI 샘플 빌드는 `.example` 을 COPY.
- `make bootstrap` 이 submodule 초기화·실패 시 안내 메시지 출력.

**근거:**
- 1인 팀이지만 외부 기여/리뷰어가 생길 가능성 대비, "공개 영역만 받아도 앱이 기동" 하는 그레이스풀 디그레이션이 공개 레포의 신뢰를 지탱.
- submodule 미체크아웃 상태의 빌드 실패는 신규 개발자 온보딩 실패의 주원인 → `.example` 폴백으로 해결.

---

## P5. 실수 방지 가드레일

### 결정 (전부 채택)

| 계층 | 조치 | 이유 |
|---|---|---|
| 저장 전 | `.gitignore` 에 `.env*`, `infra/*.tfvars`, `*.sa.json`, `ai/prompts/` (public 레포 쪽 전용), 판례 원본 캐시 경로 | 기본 방어선 |
| 커밋 전 | `pre-commit` + `gitleaks` + `detect-secrets` | 로컬 차단 |
| 푸시 시 | GitHub Secret scanning + Push Protection (public 레포 기본 활성) | 최후 방어선 |
| PR 시 | CODEOWNERS: `/ai/` 는 prompts 팀 리뷰 필수, `infra/` 는 운영 리뷰어 필수 | 사람 게이트 |
| 브랜치 보호 | main 직접 푸시 금지, 스쿼시 머지, 리뷰 1명+ 강제 | CLAUDE.md 커밋 컨벤션과 일치 |
| 스캐너 확장 | `trufflehog` PR 체크 | gitleaks 보완 |

**근거:**
- 리서치: "한 번 public 에 커밋된 시크릿은 GitHub 캐시·포크에 남아 제거 불가 — 즉시 로테이션만이 유일 대응". 사후 대응 비용이 막대하므로 **5중 방어** 가 과잉이 아님.
- Push protection 은 탐지 시 push 자체를 막아 가장 효과적. public 레포 기본 활성이므로 설정 확인만 필요.

---

## P6. 사고 복구 플레이북

### 결정

실수 커밋이 push 되었을 때의 표준 절차를 `docs/runbooks/secret-leak.md` 로 레포에 미리 둔다.

1. **즉시 해당 시크릿/키를 로테이션** (법제처 OC 재발급, GCP 키 재발급).
2. **히스토리 제거**: `git filter-repo --invert-paths --path <file>` 후 `git push --force-with-lease`. 모든 로컬 클론에 clean-clone 지시.
3. **포크 확인**: GitHub API 로 포크 목록 확인, 해당 포크 소유자에게 삭제 요청.
4. **사후 회고**: 왜 pre-commit 을 통과했는지 분석 → 가드레일 보강.

**근거:**
- 히스토리 제거만으로 외부 캐시에 영향 없음을 리서치에서 확인 → **로테이션이 1순위**.
- 플레이북을 미리 두면 패닉 상황에서 놓치는 단계를 줄임.

---

## P7. 파이프라인 산출물 공개 범위

### 다루는 문제
CLAUDE.md 사용자 원칙 5번: "각 파이프라인 산출물(research/analysis/plan) 을 Git 에 커밋하여 추적". 그런데 이 노트들은 **Obsidian Vault** 에 저장되고 있다 (`/Users/zephyr/ObsidianVault/`). 어디에 커밋할 것인가?

### 옵션 비교

| 기준 | A. Public 레포 `/docs/{research,analysis,plans}` 에 복제 | B. Vault 자체를 private 레포로 따로 운영 | C. Vault 를 public 레포로 공개 | D. 일부(민감성 없는 것)만 public 레포에 복제 |
|---|---|---|---|---|
| CLAUDE.md §145·148 원칙 준수 | ✅ | ✅ | ✅ | 부분 |
| 민감 내용 포함 위험 | 중 (검토 누락 시 노출) | 낮음 | 중~높음 | 낮음 |
| 제품 신뢰성(투명성) | 높음 | 없음 | 최상 | 중 |
| 운영 부담 | 중 (복제 자동화 필요) | 낮음 | 낮음 | 중 |
| CLAUDE.md 메모리 규칙 | 준수 | 준수 | 개인 메모까지 노출되면 프라이버시 이슈 | 준수 |

### 결정
**옵션 D (선별 복제) 선택.**

- `/docs/research`, `/docs/analysis`, `/docs/plans` 는 public 레포에 둔다. 여기 들어갈 MD 는 **`status: done`** 이고 민감도 리뷰를 거친 **최종본** 만.
