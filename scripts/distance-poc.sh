#!/usr/bin/env bash
# PoC for ADR-0003 / Front Task 0:
# Embed a hand-picked set of natural-language queries with Upstage's
# solar-embedding-1-large-query model, then ask Cloud SQL pgvector for the
# top-10 closest article rows. Print each row with its cosine distance and a
# short body preview so we can manually annotate "matching / related /
# unrelated" and pick distance thresholds for the relevance label
# (高/中/표시 안 함).
set -euo pipefail

PROJECT="${PROJECT:-laborcase-prod}"
DB_HOST="${DB_HOST:?usage: DB_HOST=<public ip> $0}"
DB_USER="${DB_USER:-laborcase_app}"
DB_NAME="${DB_NAME:-laborcase}"

UPSTAGE_KEY="$(gcloud secrets versions access latest --secret=upstage-api-key --project="$PROJECT" | tr -d '\n')"
DB_PASSWORD="$(gcloud secrets versions access latest --secret=db-app-password --project="$PROJECT")"
PSQL=/opt/homebrew/opt/libpq/bin/psql

QUERIES=(
  "부당하게 해고당했다 어떻게 해야 하나"
  "월급이 한 달째 안 들어와요"
  "출산휴가 중에 잘렸어요"
  "최저임금 못 받았는데 신고할 수 있나"
  "회사가 갑자기 부서를 옮기래요 거부할 수 있나"
  "퇴직금을 안 줘요"
  "야근수당 못 받았어요"
  "비정규직인데 계약 갱신 안 해줘요"
  "성희롱 신고했는데 회사가 보복했어요"
  "파견직인데 정규직 전환 가능한가"
)

OUT="/tmp/distance-poc.txt"
: > "$OUT"

for q in "${QUERIES[@]}"; do
  echo "================================================================"
  echo "QUERY: $q"
  echo "================================================================"
  echo "================================================================" >> "$OUT"
  echo "QUERY: $q" >> "$OUT"
  echo "================================================================" >> "$OUT"

  vec="$(curl -sS -X POST https://api.upstage.ai/v1/embeddings \
    -H "Authorization: Bearer $UPSTAGE_KEY" \
    -H "Content-Type: application/json" \
    -d "$(python3 -c "import json,sys; print(json.dumps({'input': sys.argv[1], 'model': 'solar-embedding-1-large-query'}))" "$q")" \
    | python3 -c "import sys,json; print('['+','.join(repr(x) for x in json.load(sys.stdin)['data'][0]['embedding'])+']')")"

  PGPASSWORD="$DB_PASSWORD" "$PSQL" "sslmode=require host=$DB_HOST port=5432 user=$DB_USER dbname=$DB_NAME" \
    --no-align --field-separator=$'\t' --tuples-only \
    -c "SELECT
          ROUND((ae.vector <=> '${vec}'::vector)::numeric, 4) AS distance,
          COALESCE(l.short_name, l.name_kr) AS law,
          a.jo::int AS jo,
          COALESCE(a.hang::int, 0) AS hang,
          COALESCE(a.title, '-') AS title,
          LEFT(a.body, 80) AS body
        FROM article_embedding ae
        JOIN article a ON a.id = ae.article_id
        JOIN law_version lv ON lv.id = a.law_version_id AND lv.is_current = TRUE
        JOIN law l ON l.id = lv.law_id
        ORDER BY ae.vector <=> '${vec}'::vector
        LIMIT 10;" \
    | tee -a "$OUT"

  echo "" | tee -a "$OUT"
done

echo "Saved to $OUT"
