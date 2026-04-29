# syntax=docker/dockerfile:1.7
#
# Cloud Run 용 Next.js 16 standalone 이미지.
#
# 3-stage:
#   1) deps    — production deps 캐시 layer (lockfile 변경 시만 invalidate)
#   2) builder — full deps + `next build` (output: standalone 결과 생성)
#   3) runner  — minimal node + standalone bundle 만 복사. 비-root 사용자.
#
# 빌드 인자 NEXT_PUBLIC_API_BASE_URL 은 build-time 인라인 — Cloud Build
# substitution (frontend/cloudbuild.yaml, DT-Task 7) 으로 주입.

# ── 1) Production deps cache layer ────────────────────────────────────
FROM node:22-alpine AS deps
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --omit=dev


# ── 2) Builder ────────────────────────────────────────────────────────
FROM node:22-alpine AS builder
WORKDIR /app

# build 단계에선 dev deps 도 필요 (typescript / eslint / tailwind 등).
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

# 소스 복사 후 빌드. `next build` 가 .next/standalone, .next/static,
# public/ 을 산출.
COPY frontend ./
ARG NEXT_PUBLIC_API_BASE_URL
ENV NEXT_PUBLIC_API_BASE_URL=${NEXT_PUBLIC_API_BASE_URL}
RUN npm run build


# ── 3) Runtime ────────────────────────────────────────────────────────
FROM node:22-alpine AS runner
WORKDIR /app

# Non-root 사용자 — 컨테이너 escape 시 영향 최소화.
RUN addgroup -S app && adduser -S app -G app

ENV NODE_ENV=production
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

COPY --from=builder --chown=app:app /app/public ./public
COPY --from=builder --chown=app:app /app/.next/standalone ./
COPY --from=builder --chown=app:app /app/.next/static ./.next/static

USER app
EXPOSE 3000

# .next/standalone 의 entrypoint 는 server.js (Next.js 가 생성).
CMD ["node", "server.js"]
