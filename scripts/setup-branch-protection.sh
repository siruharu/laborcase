#!/usr/bin/env bash
# Idempotently apply branch protection to laborcase main and dev branches.
# Requires: gh CLI authenticated with 'repo' scope.
set -euo pipefail

OWNER="siruharu"
REPO="laborcase"

apply_protection() {
  local branch="$1"
  local require_reviews="$2"
  local reviewer_count="$3"

  echo "▶ Protecting ${OWNER}/${REPO}:${branch} (required_reviews=${require_reviews}, count=${reviewer_count})"

  local pr_reviews_field='null'
  if [[ "$require_reviews" == "true" ]]; then
    pr_reviews_field=$(cat <<JSON
{
  "dismiss_stale_reviews": true,
  "require_code_owner_reviews": true,
  "required_approving_review_count": ${reviewer_count}
}
JSON
)
  fi

  local payload
  payload=$(cat <<JSON
{
  "required_status_checks": {
    "strict": true,
    "contexts": ["build", "secret-scan"]
  },
  "enforce_admins": false,
  "required_pull_request_reviews": ${pr_reviews_field},
  "restrictions": null,
  "required_linear_history": true,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "required_conversation_resolution": true
}
JSON
)

  echo "$payload" | gh api -X PUT "repos/${OWNER}/${REPO}/branches/${branch}/protection" \
    --input - > /dev/null
  echo "  ✓ applied"
}

apply_protection "main" "true" 1
apply_protection "dev"  "false" 0

echo ""
echo "▶ Verifying:"
for b in main dev; do
  echo "  ${b}:"
  gh api "repos/${OWNER}/${REPO}/branches/${b}/protection" --jq \
    '{force_push: .allow_force_pushes.enabled, reviews: .required_pull_request_reviews, checks: .required_status_checks.contexts}'
done
