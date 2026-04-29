---
created: 2026-04-24 09:30
topic: laborcase public/private 레포 분리 전략
project: laborcase
tags: [research, git, github, monorepo, security, ci-cd]
status: done
next_mode: analyze
source_research: []
---

# 🔍 laborcase public/private 레포 분리 전략

> [!info] 리서치 목적
> MVP 코드 대부분은 공개 레포로 두되 **민감 자산(시스템 프롬프트, 법령 해석 로직 등)은 private 레포로 분리**하고, 두 레포를 GitHub 에서 어떤 구조로 묶어 CI/CD 로 함께 빌드·배포할지 선택지를 사실 수집한다. 결정은 다음 분석 단계.

## 배경 / 맥락

- laborcase `CLAUDE.md` 가 명시하는 원칙:
  - `/ai/prompts` 는 **private 레포로 분리 관리**.
  - 민감 정보(API 키, DB 비밀번호)는 코드에 직접 쓰지 않음.
  - 사용자 원칙 5번: "각 파이프라인 산출물을 Git 에 커밋하여 추적".
- 노출 위험이 큰 자산(프롬프트·프롬프트 테스트 케이스·법령 매핑 룰·분야별 체크리스트 원문)은 경쟁사·어뷰저가 그대로 복제하면 서비스 가치가 희석되고, 법적 표현 제약 회피에 악용될 소지가 있다.
- 반대로 **프론트/백엔드/인프라 코드, 수집 파이프라인, 아키텍처 문서**는 공개해도 무방하며, 공개하는 편이 기여 유도·신뢰성 확보(오픈된 코드에서 법적 표현 제약이 지켜지고 있음을 증명) 측면에서 유리.

## 수집한 정보

### 분리 기준 (어떤 파일이 private 인가)

CLAUDE.md 디렉토리 구조 기준 1차 매핑.

| 경로 / 자산 | 분류 | 이유 |
|---|---|---|
| `/ai/prompts/**` | **private** | CLAUDE.md 명시. 시스템 프롬프트는 제품의 차별화 지점이자 잘못 복제 시 법적 표현 제약 우회에 악용 가능 |
| 프롬프트 회귀 테스트/골든셋 | private | 프롬프트와 쌍을 이룸 |
| 분야별 체크리스트 원문(법적 표현 검수 끝난 최종본) | private 또는 분리 관리 | 법적 문구 뉘앙스 관리 |
| 판례 임베딩·태깅 로직 중 휴리스틱/매핑표 | private | RAG 품질 요소 |
| `.env*`, 서비스 계정 JSON, Cloud SQL 비밀번호 | 레포에 두지 않음 | Secret Manager / GitHub Actions Secret |
| Terraform `tfvars` 중 환경값 | private (별도 state + 암호화) | 운영 비밀 |
| `/frontend`, `/api`, `/ai`(프롬프트 제외), `/pipeline`, `/infra` 구조, `/docs` 대부분 | **public** | 코드 품질·법적 경계 준수 여부를 공개 검증 가능 |
| `/docs/decisions`(ADR) | public | 설계 투명성 |
| Claude Code `.claude/agents` 정의 | public (민감 지시문 제외) | 협업 컨텍스트 |

> [!note] 반복되는 실수 패턴
> "나중에 옮기자" 하고 public 에 우선 커밋 → 히스토리에 영구 각인. git 히스토리에서 제거는 `git filter-repo`·BFG 로 가능하나 GitHub 캐시·포크에는 남는다. **처음부터 경로를 분리**하는 것이 유일한 안전책.

### 주요 접근법 / 옵션

#### 옵션 A. Public 메인 + Private Git Submodule

- Public 레포가 최상위 monorepo. 민감 경로(`ai/prompts`, `ops/secrets-mgmt` 등)를 **별도 private 레포로 만들고 submodule 로 핀**.
- Submodule 은 특정 커밋 해시를 고정하므로 public 레포 히스토리에는 **파일 내용이 아닌 "포인터"만** 기록된다. 민감 변경 이력은 private 쪽 로그에만 남는다.
- 회사 Cal.com 이 이 방식 사용: "일부 민감한 코드베이스와 마케팅 페이지는 private 폐쇄소스 레포로 유지"하고 메인 monorepo 에 submodule 로 편입.
- CI 에서 private submodule 체크아웃하려면 **Deploy Key + `webfactory/ssh-agent`** 조합이 2026 기준 베스트프랙티스. PAT(Personal Access Token) 은 사용자 전체 범위라 per-repo 권한을 줄 수 없어 지양.
- 주의: **하나의 Deploy Key 는 하나의 레포에만** 붙는다. submodule 이 여러 개면 각 private 레포마다 개별 deploy key → `ssh-agent` 에 파이프로 나열해 로드.
- 개발자 로컬에서는 submodule 도 각자 `git clone --recurse-submodules` + SSH 키 접근 권한 필요. 팀원 온보딩 비용 상승.
- 출처: [Cal.com Handbook — Git Private Submodules](https://handbook.cal.com/engineering/codebase/git-private-submodules), [How to Checkout a Private Submodule in GitHub Actions](https://www.codestudy.net/blog/how-to-checkout-submodule-in-github-action/), [Use deploy keys to access private repos within GitHub Actions](https://owenjonesuob.github.io/posts/2024-04-27-use-deploy-keys-to-access-private-repos-within-github-actions/)

#### 옵션 B. Private 메인 + Public 서브레포를 상위에서 미러/동기화

- 개발은 full-private 레포에서만. 공개 가능한 부분을 별도 public 레포로 **주기적으로 동기화**.
- Cal.com 이 Console↔Website 간 cronjob 동기화를 쓰는 방식과 유사.
- 장점: 개발 플로우가 단순, 실수로 민감 파일을 public 에 넣을 위험이 가장 낮다.
- 단점: 양방향 PR 이 어렵다(외부 기여를 받으면 private 에 머지해야 함 → 수동 cherry-pick). 공개 공지나 기여 유도가 약함.

#### 옵션 C. 단일 Public 레포 + Git Sparse-Checkout + 별도 Private 레포

- 민감 파일을 아예 다른 레포에 둔다. 두 레포는 **서로 모름**(submodule 관계 없음).
- 빌드 시 CI 가 두 레포를 각각 체크아웃해 병합 빌드.
- GitHub Actions 의 `actions/checkout` 가 sparse-checkout 을 네이티브 지원(`sparse-checkout:` 키). 두 번째 `checkout` 으로 private 레포를 다른 경로에 받는다.
- 장점: public 레포에는 private 포인터조차 없어 "여기 뭔가 private 이 있다"는 힌트가 남지 않음.
- 단점: 로컬 개발에서 수동으로 둘 다 클론·경로 심볼릭 링크 해야 함. 버전 동기화(어떤 private 커밋이 어떤 public 커밋과 짝인가)를 별도 메타데이터(예: `private-manifest.yaml` in public) 로 관리해야 함.
- 출처: [Bring your monorepo down to size with sparse-checkout — GitHub Blog](https://github.blog/open-source/git/bring-your-monorepo-down-to-size-with-sparse-checkout/), [Checkout Action 마켓플레이스](https://github.com/marketplace/actions/checkout)

#### 옵션 D. Monorepo 안에서 경로만 분리 + `.gitignore` + Secret Manager

- 단일 public 레포에 민감 파일 **두지 않는다**. 대신 민감 자산은 **런타임에 외부 스토리지(GCS, Secret Manager)에서 로드**.
- 프롬프트 파일 같은 중간 크기 텍스트 자산은 GCS 버킷 + 버저닝 + 서명 URL 로 관리 가능.
- 장점: GitHub 권한 관리 단순화, CI/CD 파이프라인에 Git 권한 복잡도 없음.
- 단점: 프롬프트에 대한 "코드 리뷰·PR·diff" 워크플로 상실. 프롬프트 변경 이력 추적이 GCS 버전 히스토리로 제한. 개발자 경험 저하.
- 하이브리드: 프롬프트 repo 는 private Git 으로 두되, 배포 산출물은 빌드시 GCS 로 업로드하고 런타임은 GCS 에서 읽음. → 옵션 A/B + 런타임 분리.

### GitHub 연결 방법 요약

| 항목 | 옵션 A (Submodule) | 옵션 B (Mirror) | 옵션 C (Sparse+별도) | 옵션 D (Secret+런타임) |
|---|---|---|---|---|
| 레포 수 | public 1 + private N | private 1 + public 미러 1 | public 1 + private 1 (느슨결합) | public 1 (+ 런타임 스토리지) |
| CI 인증 | Deploy Key × 각 private | public 동기화 봇 토큰 | Deploy Key × private | Workload Identity → GCS |
| Git 이력 노출 | 포인터만 공개 | 미러본 공개(필터링) | 완전 분리 | 완전 분리 |
| 로컬 개발 난이도 | 중 (submodule 관리) | 낮음 (private 하나만) | 높음 (수동 조립) | 중 (스토리지 계정) |
| 외부 기여 받기 | 쉬움 (public 레포 PR) | 어려움 (수동 병합) | 쉬움 | 쉬움 |
| 프롬프트 PR diff 가능 | ✅ | ✅ | ✅ | ❌ (GCS 버전) |

### CI/CD 통합 (GitHub Actions 기준)

- **Submodule 체크아웃 예시** (옵션 A)
  ```yaml
  - uses: webfactory/ssh-agent@v0.9.0
    with:
      ssh-private-key: |
        ${{ secrets.DEPLOY_KEY_MAIN }}
        ${{ secrets.DEPLOY_KEY_PROMPTS }}
  - uses: actions/checkout@v4
    with:
      submodules: recursive
      ssh-key: ${{ secrets.DEPLOY_KEY_MAIN }}
  ```
- **별도 레포 이중 체크아웃** (옵션 C)
  ```yaml
  - uses: actions/checkout@v4
    with:
      path: main
  - uses: actions/checkout@v4
    with:
      repository: myorg/laborcase-prompts
      token: ${{ secrets.PROMPTS_REPO_PAT }}
      path: prompts
      sparse-checkout: |
        prompts/
  ```
- **Secret 주입 순서**: PR 빌드에서는 민감 빌드 타깃을 가드하거나 `pull_request_target` 이벤트로 신뢰된 빌드만 private 레포 접근 허용. 외부 포크 PR 이 secret 에 접근하지 못하도록 주의.
- **Deploy Key 제약**: 1 key = 1 repo. submodule N 개 → deploy key N 개. `webfactory/ssh-agent` 가 다중 키를 호스트별로 분기 로드 지원.
- 출처: [Private Github Actions without PAT — Rohit Goswami](https://rgoswami.me/posts/priv-gh-actions/), [Access private submodules in GitHub Actions — Samuelsson](https://samuelsson.dev/access-private-submodules-in-github-actions/)

### 실무 체크리스트 (어느 옵션을 쓰든 공통)

- [ ] 최초 커밋 전에 `.gitignore` 에 민감 경로 명시 (`ai/prompts/`, `*.env`, `gcp-sa-*.json`, `infra/*.tfvars`).
- [ ] pre-commit hook: `gitleaks` 또는 `trufflehog` 로 API 키/토큰 스캔.
- [ ] GitHub Repository Settings → Secret scanning / Push protection 켜기 (public 레포는 기본 활성).
- [ ] 실수 커밋 발생 시: `git filter-repo` 로 히스토리 제거 + 해당 키/토큰 **즉시 로테이션**(히스토리 제거만으로는 외부 캐시·포크가 남는다).
- [ ] 공개 레포 README 상단에 디스클레이머, 공공누리 1유형 출처표시, 법적 표현 제약 정책 링크.
- [ ] CODEOWNERS 로 민감 경로에 리뷰 강제.

### 레퍼런스

- [Git Submodules: The Complete Guide for 2026](https://devtoolbox.dedyn.io/blog/git-submodules-complete-guide) — submodule 워크플로 전반
- [Reasons to avoid Git submodules — Tim Hutt](https://blog.timhutt.co.uk/against-submodules/) — 반대론. detached HEAD·도구 호환성 이슈
- [Git Submodules vs Monorepos — DEV](https://dev.to/davidarmendariz/git-submodules-vs-monorepos-14h8) — 트레이드오프 비교
- [Hybrid Public-Private Monorepo — nrwl/nx 이슈 #13903](https://github.com/nrwl/nx/issues/13903) — Nx 모노레포에서의 public/private 혼합 운용
- [How I Used Git Submodules to Safely Share Private Code — Medium](https://medium.com/@xiifwat/how-i-used-git-submodules-to-safely-share-private-code-in-a-public-project-d5f4b5f60128)
- [Private Submodules user documentation — Read the Docs](https://docs.readthedocs.io/en/stable/guides/private-submodules.html)

## 발견한 트레이드오프

| 평가 축 | A Submodule | B Mirror | C Sparse+별도 | D Secret런타임 |
|---|---|---|---|---|
| 민감 자산 유출 위험 | 낮음 (포인터만) | 중 (미러봇 오작동 시) | 매우 낮음 | 매우 낮음 |
| 초기 설정 비용 | 중 | 낮음 | 높음 | 중 |
| 개발자 온보딩 | 중 | 낮음 | 높음 | 중 |
| 프롬프트 변경 리뷰 품질 | 좋음 | 좋음 | 좋음 | 약함 |
| 외부 공개/기여 유도 | 강함 | 약함 | 강함 | 강함 |
| Secret 관리 복잡도 | 중 (deploy key N 개) | 낮음 | 중 (PAT/토큰) | 낮음 (Cloud IAM) |
| 배포 파이프라인 복잡도 | 중 | 낮음 | 높음 | 중 |

## 열린 질문 (다음 분석 단계로)

- [ ] Private 자산의 수는? (프롬프트만? 체크리스트·매핑 테이블·프롬프트 테스트셋까지?) → submodule 개수 산정의 기초.
- [ ] 프롬프트 변경 시 배포 빈도? (주 1회 이내면 옵션 D 도 가능, 일 단위면 Git 기반 옵션 유리.)
- [ ] 외부 기여를 받을 의향이 있나? (있으면 public 메인 = 옵션 A 또는 C.)
- [ ] Cloud Run 배포 이미지에 프롬프트를 포함할지, 런타임 로드할지? → 배포 파이프라인 설계 분기점.
- [ ] "법령 매핑 룰"을 private 으로 둘지 public 으로 둘지. Public 이면 학계·개발자 검증을 받을 수 있지만 경쟁사 복제가 쉬움.
- [ ] PR 빌드에서 fork PR 도 private submodule 접근해야 하나? (일반적으로는 금지. CI 매트릭스를 둘로 분리하는 관례.)
