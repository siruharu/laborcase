# Runbook: Deploy Key 정기 로테이션

## 언제

- 분기 1회 정기 로테이션 (분기 1일자 자동 리마인더 이슈 생성, `rotation-reminder.yml`)
- 비정기: 노출 의심, 담당자 이탈, CI 러너 환경 교체 시

## 원칙

**"추가 → 검증 → 삭제"** 순서. 절대 역순으로 진행하지 않는다. 삭제 먼저 하면 CI 가 끊긴다.

## 절차

```bash
# 1. 새 키 쌍 생성 (임시 디렉토리)
SCRATCH=$(mktemp -d)
ssh-keygen -t ed25519 -C "laborcase-internal-deploy@siruharu-$(date +%Y%m%d)" \
  -f "$SCRATCH/deploy_key" -N "" -q
NEW_FP=$(ssh-keygen -lf "$SCRATCH/deploy_key.pub" | awk '{print $2}')
echo "new key fingerprint: $NEW_FP"

# 2. 새 public 키를 private 레포에 추가 (기존 것은 아직 유지)
gh api -X POST repos/siruharu/laborcase-internal/keys \
  -f title="ci-readonly-$(date +%Y%m%d)" \
  -f key="$(cat "$SCRATCH"/deploy_key.pub)" \
  -F read_only=true

# 3. 새 private 키를 public 레포의 Actions Secret 으로 저장 (덮어쓰기)
gh secret set DEPLOY_KEY_INTERNAL --repo siruharu/laborcase \
  --body "$(cat "$SCRATCH"/deploy_key)"

# 4. 임시 파일 즉시 삭제
rm -rf "$SCRATCH"

# 5. 검증: CI 를 수동 트리거하여 submodule 체크아웃 성공 확인
gh workflow run ci --repo siruharu/laborcase --ref dev
gh run watch --repo siruharu/laborcase --exit-status

# 6. 검증 성공 후 기존 키 삭제
gh api repos/siruharu/laborcase-internal/keys --jq '.[] | {id, title, created_at}'
# 위 목록에서 구 키 id 확인 후:
gh api -X DELETE repos/siruharu/laborcase-internal/keys/<OLD_KEY_ID>
```

## 실패 시

- CI 가 새 키로 체크아웃 실패 → 구 키 유지한 채 원인 분석. Secret 이 올바르게 설정됐는지, 개행 문자 포함 여부, `webfactory/ssh-agent` 버전.
- `set-output` 대신 `gh secret set --body "$(cat ...)"` 쓰면 개행 문제 없다.

## 주의

- 이 절차는 **CI 에서만 쓰이는 Deploy Key** 에 한정. 개발자 개인 SSH 키는 별도 (`gh ssh-key` 명령).
- 로테이션 결과(신규 key id, 삭제된 key id) 를 Slack `#security` 채널 또는 이슈에 기록해 감사 추적성 확보.
