# runbooks

운영 이벤트별 대응 절차. 패닉 상황에서 놓칠 수 있는 단계를 사전에 박제해 둔다.

- [deploy.md](./deploy.md) — API + Frontend Cloud Run 배포 절차, GHA tag-based release, IAM 트러블슈팅, 롤백, 첫 셋업 체크리스트
- [secret-leak.md](./secret-leak.md) — 비밀(API 키·Deploy Key·서비스계정)이 노출·커밋된 경우
- [deploy-key-rotation.md](./deploy-key-rotation.md) — Deploy Key 정기 로테이션
- [law-sync-failure.md](./law-sync-failure.md) — `law-full-sync` / `law-delta-sync` Cloud Run Job 실패 분류·조치

## 작성 규칙

- 각 runbook 은 "1) 즉시 조치 → 2) 원인 분석 → 3) 재발 방지" 3단 구성.
- 스크린샷·링크는 상대 경로로, 비밀 값은 절대 포함하지 않는다.
- 사후 회고(post-mortem) 섹션은 5 Whys 템플릿으로.
