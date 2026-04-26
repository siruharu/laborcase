import { defineConfig, devices } from "@playwright/test";

/**
 * 로컬 통합 smoke. 백엔드 + Cloud SQL 까지 띄운 상태에서 돌린다.
 * Task 11 의 수동 검증을 자동화한 형태.
 *
 * webServer 는 frontend dev 서버만 자동 기동. backend 는 외부에서 별도로
 * `./gradlew :api:bootRun` 또는 prod URL 지정 (CI 후속).
 *
 * 모바일은 webkit 의존을 피해 Pixel 7 (chromium 기반) viewport 사용.
 * iOS Safari 정확 검증은 Task 11 수동으로 보강.
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
  webServer: {
    command: "npm run dev",
    url: "http://localhost:3000",
    reuseExistingServer: true,
    timeout: 60_000,
    env: {
      NEXT_PUBLIC_API_BASE_URL:
        process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080",
    },
  },
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
