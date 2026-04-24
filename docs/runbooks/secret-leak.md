# Runbook: 비밀 노출 대응

## 트리거

다음 중 하나에 해당하면 이 runbook 을 시작한다.

- pre-commit·push protection 우회로 시크릿이 원격에 push 됨
- Deploy Key·API 키가 스크린샷·Slack·공개 문서 등에 노출
- GitHub Secret scanning 알림 수신
- 외부에서 악용 징후 제보

## 1. 즉시 조치 (60분 이내)

### 1-1. 노출된 비밀을 즉시 무효화

**우선순위가 가장 높다.** 히스토리 제거보다 먼저. 외부에 이미 복제된 상태라면 제거해도 의미가 없다.

| 비밀 유형 | 로테이션 방법 |
|---|---|
| 법제처 Open API OC | [법제처 신청센터](https://open.law.go.kr) 에서 기존 키 폐기 + 재발급. Secret Manager `law-oc` 의 새 버전 추가 |
| GCP 서비스계정 키 | `gcloud iam service-accounts keys list --iam-account=...` 로 key id 확인 → `gcloud iam service-accounts keys delete <KEY_ID>` + 새 키 생성 |
| GCP SA 자체(과도한 권한) | 해당 SA 비활성화(`disable`) + 새 SA 생성 + Cloud Run 서비스 갱신 |
| GitHub Deploy Key | `gh api -X DELETE repos/siruharu/laborcase-internal/keys/<KEY_ID>` + 새 키 쌍 생성 후 재등록 (→ deploy-key-rotation.md 절차 재사용) |
| GitHub PAT | [Settings → Developer settings → Personal access tokens](https://github.com/settings/tokens) 에서 revoke + 재발급 |
| OpenAI / Upstage API 키 | 각 콘솔에서 revoke + 재발급 |
| DB 비밀번호 | Cloud SQL 에서 사용자 비밀번호 재설정 + Cloud Run 환경변수 갱신 |

### 1-2. 노출 범위 측정

```bash
# 최근 커밋에서 비밀이 언급된 파일/라인 찾기
gitleaks detect --no-banner --source .

# 특정 문자열이 히스토리에 언제 들어왔는지 추적
git log --all -p -S '<노출된 문자열 일부>' | head -50

# 포크 목록 확인 (public 레포의 경우 노출 확산 가능)
gh api -H "Accept: application/vnd.github+json" \
  repos/siruharu/laborcase/forks --paginate \
  --jq '.[] | {full_name, owner: .owner.login, created_at}'
```

### 1-3. 히스토리 제거 (로테이션 완료 후)

```bash
# git-filter-repo 설치 (한 번만)
brew install git-filter-repo

# 특정 파일을 모든 히스토리에서 제거
git filter-repo --invert-paths --path <leaked-file>

# 강제 push (main 보호가 있으면 일시 완화 필요)
git push --force-with-lease origin main
git push --force-with-lease origin dev
```

**⚠️ 팀원 공지 템플릿 (Slack 등):**

> laborcase 히스토리 rewrite 가 방금 완료됐습니다. 로컬 클론은 아래를 실행해주세요.
> `git fetch origin && git reset --hard origin/<branch>`
> 기존 작업 브랜치가 있다면 `git rebase` 로 새 히스토리 위에 올려주세요.

### 1-4. 포크 대응

공개 레포는 포크가 있을 수 있다. 포크 소유자에게 메일 템플릿:

> Subject: [laborcase] Action required — please delete your fork
>
> Your fork of siruharu/laborcase contains a commit with an inadvertently
> leaked secret. The upstream has been rotated and history cleaned, but
> your fork still retains the leaked value. Please delete the fork at
> your earliest convenience. Thank you for cooperating.

## 2. 원인 분석

사후 회고에 포함:

| 질문 | 답변 |
|---|---|
| Why 1: 왜 비밀이 레포에 들어갔는가? | (예: `.env` 복사 후 `.gitignore` 미확인) |
| Why 2: 왜 pre-commit 이 차단하지 못했나? | (예: hook 미설치, allowlist 오용) |
| Why 3: 왜 push protection 이 차단하지 못했나? | (예: 패턴 미등록, private 레포라 기능 없음) |
| Why 4: 왜 CI 가 잡지 못했나? | (예: trufflehog 가 diff 모드라 과거 커밋 스캔 안 함) |
| Why 5: 근본 원인 | (예: 로컬 설정 편차, 문서 부재) |

## 3. 재발 방지

- [ ] gitleaks/detect-secrets 룰 보강 (`.gitleaks.toml` 또는 `.secrets.baseline` 갱신)
- [ ] pre-commit 미설치 개발자 식별 → `scripts/bootstrap.sh` 재실행 안내
- [ ] CI 에 다른 계층의 스캐너 추가 검토
- [ ] 관련 Secret Manager 에 rotation 자동화 고려

## 참고

- [GitHub: Removing sensitive data from a repository](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository)
- [git-filter-repo](https://github.com/newren/git-filter-repo)
