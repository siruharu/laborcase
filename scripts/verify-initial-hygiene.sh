#!/usr/bin/env bash
# Verify that no sensitive file is tracked by git.
# Exits non-zero if any forbidden path appears in `git ls-files`.
set -euo pipefail

FORBIDDEN_PATTERNS=(
  '^\.env($|\.)'
  '^.*\.env$'
  '.*\.sa\.json$'
  '.*-sa\.json$'
  '^service-account.*\.json$'
  '^gcp-key.*\.json$'
  '^infra/.*\.tfvars$'
  '^pipeline/raw/'
  '.*\.tfstate(\..*)?$'
)

cd "$(git rev-parse --show-toplevel)"

tracked="$(git ls-files)"
failed=0

for pattern in "${FORBIDDEN_PATTERNS[@]}"; do
  if echo "$tracked" | grep -Eq "$pattern"; then
    echo "✗ forbidden path matches $pattern:" >&2
    echo "$tracked" | grep -E "$pattern" | sed 's/^/    /' >&2
    failed=1
  fi
done

# ai/prompts/ must contain only .gitkeep (submodule files are managed via .gitmodules, not ls-files)
prompts_leak="$(echo "$tracked" | grep -E '^ai/prompts/' | grep -vE '^ai/prompts/\.gitkeep$' || true)"
if [[ -n "$prompts_leak" ]]; then
  echo "✗ ai/prompts/ contains non-submodule files:" >&2
  echo "$prompts_leak" | sed 's/^/    /' >&2
  failed=1
fi

if [[ $failed -eq 0 ]]; then
  echo "✓ initial hygiene check passed ($(echo "$tracked" | wc -l | tr -d ' ') tracked files)"
fi

exit $failed
