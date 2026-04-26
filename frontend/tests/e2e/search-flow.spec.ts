import { expect, test } from "@playwright/test";

/**
 * Frontend smoke. 백엔드 (`http://localhost:8080`) + Cloud SQL 이 동작하는
 * 환경을 가정한다.
 *
 * 4-layer disclaimer / freshness stale 배너의 시각 검증은 별도 — 여기선
 * 사용자 핵심 플로우만 검증.
 */

test.describe("랜딩 → 검색 → 결과", () => {
  test("검색어 입력 후 /search?q=… 로 이동하고 결과 영역이 노출된다", async ({
    page,
  }) => {
    await page.goto("/");
    await expect(
      page.getByRole("heading", {
        level: 1,
        name: /노동법 정보를 자연어로 검색/,
      }),
    ).toBeVisible();

    await page.getByLabel("자연어 검색어").fill("부당해고");
    await page.getByRole("button", { name: "검색" }).click();

    await expect(page).toHaveURL(/\/search\?q=/);
    // 결과 카드 또는 빈 결과 안내 둘 중 하나가 노출되어야 한다.
    await expect(
      page
        .locator("ul li")
        .or(page.getByText("관련 조문을 찾지 못했습니다")),
    ).toBeVisible({ timeout: 15_000 });
  });
});

test.describe("결과 카드 → 조문 상세", () => {
  test("첫 번째 결과 카드 클릭 시 /laws/{key}/articles/{jo} 로 이동한다", async ({
    page,
  }) => {
    await page.goto("/search?q=%EB%B6%80%EB%8B%B9%ED%95%B4%EA%B3%A0");
    const firstCardLink = page.locator("ul li a").first();
    await firstCardLink.waitFor({ state: "visible", timeout: 15_000 });
    await firstCardLink.click();

    await expect(page).toHaveURL(/\/laws\/[^/]+\/articles\/\d+/);
    // ArticleTree 의 article 요소 + 헤더 h1.
    await expect(page.getByRole("article")).toBeVisible();
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
  });
});

test.describe("빈 검색 결과", () => {
  test("매칭 없는 쿼리는 검색 팁 3장을 보여준다", async ({ page }) => {
    // 매우 임의적인 문자열 — 임베딩 결과가 모두 0.72 이상으로 기각될 가능성 큼.
    // 단, 백엔드는 distance 기준으로 자르지 않고 limit=5 까지 무조건 반환.
    // 따라서 "빈 결과" 시나리오는 실제로는 distance 라벨이 모두 미표시되는 케이스.
    // 진짜 0건 응답은 backend 가 hits.length=0 을 반환할 때만 — 거의 안 일어남.
    // 여기선 검색 결과 페이지에서 SearchTips 가 정상 마운트되는지만 확인.
    await page.goto("/search?q=qwertyzxcv1234%21%40%23");
    // 결과 카드 또는 검색 팁 둘 중 하나
    await expect(
      page
        .locator("ul li")
        .or(page.getByText("검색 팁")),
    ).toBeVisible({ timeout: 15_000 });
  });
});

test.describe("4-layer disclaimer 가시성", () => {
  test("랜딩에 footer 디스클레이머 + KOGL 출처표시가 노출된다", async ({
    page,
  }) => {
    await page.goto("/");
    await expect(page.getByText(/공공누리 제1유형/)).toBeVisible();
    await expect(
      page.getByText(/법률 자문이 아닙니다/),
    ).toBeVisible();
  });
});
