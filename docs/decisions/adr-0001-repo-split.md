# ADR-0001: Public/Private 레포 분리 구조

- **Date**: 2026-04-24
- **Status**: Accepted
- **Context sources**: [docs/research/2026-04-24_public-private-repo-split.md](../research/2026-04-24_public-private-repo-split.md), [docs/analysis/2026-04-24_public-private-repo-split.md](../analysis/2026-04-24_public-private-repo-split.md)

## 컨텍스트

laborcase 는 공개 판례·법령을 구조화해 제공하는 서비스이며 다음 상충 요구를 동시에 만족해야 한다.

- **투명성**: 법적 표현 제약(단정 표현 금지, 원문 링크 필수)을 지키고 있음을 외부가 검증 가능해야 신뢰를 얻는다.
- **보호**: 시스템 프롬프트·법령↔판례 매핑 휴리스틱·체크리스트 최종본이 그대로 복제되면 제품 가치가 희석되고 법적 표현 가드 우회에 악용될 수 있다.

## 결정

1. **2-레포 구조**
   - `siruharu/laborcase` (public, Apache-2.0)
   - `siruharu/laborcase-internal` (private, All rights reserved)
   - private 레포는 public 레포의 `ai/prompts/` 경로에 **git submodule** 로 편입.

2. **submodule URL 은 HTTPS**. 로컬 개발자는 gh 토큰 또는 GitHub 자격증명으로 clone. CI 는 webfactory/ssh-agent + Deploy Key(SSH) 를 사용하며 `url.insteadOf` 로 런타임에 HTTPS→SSH 를 rewrite.

3. **폴백 빌드 가능**: submodule 접근 권한이 없어도 `ai/prompts.example/` 로 앱이 기동되어 외부 기여자가 PR 을 낼 수 있다.

4. **가드레일 5중**: `.gitignore` · pre-commit (gitleaks + detect-secrets) · GitHub Push Protection · CODEOWNERS · Branch Protection.

5. **파이프라인 산출물은 선별 복제**: Obsidian Vault 의 research/analysis/plan 최종본만 `scripts/sync-docs.sh` 로 `docs/{research,analysis,plans}/` 에 복제. `<private>` 블록은 자동 제거.

6. **브랜치**: default `main` (PR 리뷰 + status check 필수), 일상 작업은 `dev`. 흐름 `feature/* → dev → main`.

## 대안

- **옵션 B. Private 메인 + Public 미러**: 투명성 가치 약함, 미러봇 결손 시 사고 위험 → 기각.
- **옵션 C. 2개 레포 느슨 결합 (sparse-checkout + CI 조립)**: 1인 팀 온보딩 비용 과도 → 기각.
- **옵션 D. 런타임 로드 (GCS)**: 프롬프트 PR diff 리뷰 상실 → 법적 표현 검수가 핵심인 본 프로젝트와 부적합 → 기각.

상세 비교는 analysis 노트 §P2.

## 결과

- 메인 레포가 공개되어 "법적 표현 제약이 지켜지고 있음"을 외부가 검증 가능.
- 민감 자산은 private 레포에 격리되어 복제 저항성 확보.
- 가드레일이 **첫 커밋 이전에** 설치되어 있어 실수로 인한 노출 리스크가 현저히 낮다.
- Trade-off: submodule 관리 부담(Deploy Key 로테이션, `recurse-submodules` 주의) 이 추가되나, 분기 1회 자동 리마인더로 완화.

## 후속

- GitHub 플랜 업그레이드 검토: private 레포에도 Branch Protection·Push Protection 을 적용하려면 Pro 이상 필요. 현재는 public 레포만 커버.
- 팀 확장 시 개인 계정 → org 이관 검토. URL 변경이 로컬 클론의 remote 재설정 요구.
