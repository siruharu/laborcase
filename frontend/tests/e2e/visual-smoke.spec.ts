import { expect, test } from "@playwright/test";

/**
 * Backend 응답을 별도 mock server (tests/e2e/mock-server.ts) 가 fixture 로
 * 픽스한다. 이 spec 은 mock server 의 /__test/state 로 시나리오별 flag 를
 * 토글한 후 페이지 동작을 검증·캡처한다.
 *
 * CLAUDE.md §사용자가 따를 원칙 — 프로덕션 Cloud SQL 직접 접근 회피.
 * 실제 backend 통합은 사용자 환경에서 별도 진행.
 */

const CAPTURE_DIR = "tests/e2e/__captures__";
const MOCK_BACKEND = "http://localhost:18080";

test.beforeEach(async ({ request }) => {
  await request.get(`${MOCK_BACKEND}/__test/reset`);
});

async function setMockState(
  request: import("@playwright/test").APIRequestContext,
  opts: { stale?: boolean; emptySearch?: boolean },
) {
  const url = new URL(`${MOCK_BACKEND}/__test/state`);
  url.searchParams.set("stale", opts.stale ? "1" : "0");
  url.searchParams.set("emptySearch", opts.emptySearch ? "1" : "0");
  await request.get(url.toString());
}

test.describe("랜딩", () => {
  test("hero + 6 법령 카드 + footer 디스클레이머", async ({ page }, info) => {
    await page.goto("/");

    await expect(
      page.getByRole("heading", {
        level: 1,
        name: /노동법 정보를 자연어로 검색/,
      }),
    ).toBeVisible();
    await expect(page.getByLabel("자연어 검색어")).toBeVisible();

    const cardLinks = page.locator("ul li a");
    await expect(cardLinks).toHaveCount(6);
    await expect(page.getByText("근기법")).toBeVisible();
    await expect(page.getByText("최임법")).toBeVisible();

    await expect(page.getByText(/공공누리 제1유형/).first()).toBeVisible();
    await expect(page.getByText(/법률 자문이 아닙니다/)).toBeVisible();

    await page.screenshot({
      path: `${CAPTURE_DIR}/landing-${info.project.name}.png`,
      fullPage: true,
    });
  });
});

test.describe("랜딩 stale", () => {
  test("freshness.stale=true → amber 배너", async ({ page, request }, info) => {
    await setMockState(request, { stale: true });
    await page.goto("/");

    const banner = page.getByRole("alert").filter({
      hasText: "최신화되지 못했습니다",
    });
    await expect(banner).toBeVisible();
    await expect(banner).toContainText("2026년 4월 23일");

    await page.screenshot({
      path: `${CAPTURE_DIR}/landing-stale-${info.project.name}.png`,
      fullPage: true,
    });
  });
});

test.describe("검색 결과", () => {
  test("hits 5건 + RelevanceBadge 라벨 분포", async ({ page }, info) => {
    await page.goto("/search?q=%EB%B6%80%EB%8B%B9%ED%95%B4%EA%B3%A0");

    await expect(page.locator("ul li a")).toHaveCount(5);

    // 0.5407, 0.6095 → 높음 / 0.6712, 0.7012 → 보통 / 0.7501 → 미표시
    await expect(page.getByText("관련도 높음")).toHaveCount(2);
    await expect(page.getByText("관련도 보통")).toHaveCount(2);

    // 인라인 디스클레이머
    await expect(
      page.getByText(/법제처 국가법령정보\(공공누리 제1유형\)/),
    ).toBeVisible();

    // SearchBox 의 initialQuery 가 채워져 있어야
    await expect(page.getByLabel("자연어 검색어")).toHaveValue("부당해고");

    await page.screenshot({
      path: `${CAPTURE_DIR}/search-results-${info.project.name}.png`,
      fullPage: true,
    });
  });
});

test.describe("검색 빈 결과", () => {
  test("hits 0 건 → 검색 팁 3장", async ({ page, request }, info) => {
    await setMockState(request, { emptySearch: true });
    await page.goto("/search?q=qwerasdfzxcv");

    await expect(page.getByText("관련 조문을 찾지 못했습니다")).toBeVisible();
    await expect(page.getByText("검색 팁", { exact: true })).toBeVisible();
    await expect(page.getByText("구체적 상황을 한 줄로")).toBeVisible();
    await expect(page.getByText("법률 용어 대신 일상 표현")).toBeVisible();
    await expect(page.getByText(/법제처 국가법령정보센터로 연결/)).toBeVisible();

    await page.screenshot({
      path: `${CAPTURE_DIR}/search-empty-${info.project.name}.png`,
      fullPage: true,
    });
  });
});

test.describe("결과 카드 → 조문 상세", () => {
  test("제23조 카드 클릭 → ArticleTree (① 항 + 1. 호 + 가. 목)", async ({
    page,
  }, info) => {
    await page.goto("/search?q=%EB%B6%80%EB%8B%B9%ED%95%B4%EA%B3%A0");

    const link23 = page
      .locator("ul li a")
      .filter({ hasText: "제23조" })
      .first();
    await link23.click();

    await expect(page).toHaveURL(/\/laws\/[^/]+\/articles\/23/);
    await expect(page.getByRole("article")).toBeVisible();
    await expect(
      page.getByRole("heading", { level: 1, name: /제23조/ }),
    ).toBeVisible();

    // ① ② 항 + 1. 호 + 가. 나. 목
    await expect(page.getByText(/① 사용자는 근로자에게 정당한/)).toBeVisible();
    await expect(page.getByText(/② 사용자는 근로자가 업무상/)).toBeVisible();
    await expect(page.getByText(/^1\. 다음 각 목/)).toBeVisible();
    await expect(page.getByText(/^가\. 천재지변/)).toBeVisible();
    await expect(page.getByText(/^나\. 사용자가/)).toBeVisible();

    // DetailBox
    await expect(
      page.getByText(/이 조문은 .* 시행 버전입니다/),
    ).toBeVisible();
    await expect(page.getByText(/2025년 10월 23일/)).toBeVisible();

    await page.screenshot({
      path: `${CAPTURE_DIR}/article-detail-${info.project.name}.png`,
      fullPage: true,
    });
  });
});

test.describe("조문 상세 stale", () => {
  test("freshness.stale=true → amber 배너", async ({ page, request }, info) => {
    await setMockState(request, { stale: true });
    await page.goto("/laws/%EA%B7%BC%EA%B8%B0%EB%B2%95/articles/23");

    await expect(
      page.getByRole("alert").filter({ hasText: "최신화되지 못했습니다" }),
    ).toBeVisible();
    await expect(page.getByRole("article")).toBeVisible();

    await page.screenshot({
      path: `${CAPTURE_DIR}/article-stale-${info.project.name}.png`,
      fullPage: true,
    });
  });
});

test.describe("not-found", () => {
  test("빈 articles 응답 (jo=9999) → 404", async ({ page }) => {
    await page.goto("/laws/%EA%B7%BC%EA%B8%B0%EB%B2%95/articles/9999");
    await expect(
      page.getByText(/This page could not be found|404/).first(),
    ).toBeVisible();
  });
});
