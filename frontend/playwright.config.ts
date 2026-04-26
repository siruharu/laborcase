import { defineConfig, devices } from "@playwright/test";

const MOCK_BACKEND = "http://localhost:18080";

/**
 * Smoke 자동화. 두 개의 webServer 를 띄운다:
 *   1) tests/e2e/mock-server.ts — fixture 응답을 돌려주는 Node http 서버.
 *   2) next dev — NEXT_PUBLIC_API_BASE_URL 을 mock backend 로 가리키게.
 *
 * 모바일은 webkit 의존을 피해 Pixel 7 (chromium) viewport 사용.
 * 실제 backend + Cloud SQL 통합 검증은 사용자 환경에서 별도로 수행
 * (CLAUDE.md §사용자가 따를 원칙 — prod DB 직접 접근 금지).
 */
export default defineConfig({
  testDir: "./tests/e2e",
  fullyParallel: false,
  retries: 0,
  reporter: "list",
  timeout: 30_000,
  use: {
    baseURL: "http://localhost:3000",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  webServer: [
    {
      command: "npx tsx tests/e2e/mock-server.ts",
      port: 18080,
      reuseExistingServer: !process.env.CI,
      timeout: 30_000,
      stdout: "pipe",
      stderr: "pipe",
    },
    {
      command: "npm run dev",
      url: "http://localhost:3000",
      reuseExistingServer: !process.env.CI,
      timeout: 60_000,
      env: {
        NEXT_PUBLIC_API_BASE_URL: MOCK_BACKEND,
        // smoke 가 mock state 를 토글한 직후 페이지를 다시 GET 했을 때 RSC
        // fetch 캐시가 stale flag 를 가려버리지 않도록 revalidate 끔.
        LABORCASE_API_REVALIDATE: "0",
      },
    },
  ],
  projects: [
    {
      name: "desktop",
      use: { ...devices["Desktop Chrome"] },
    },
    {
      name: "mobile",
      use: { ...devices["Pixel 7"] },
    },
  ],
});
