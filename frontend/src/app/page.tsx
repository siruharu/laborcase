import { DisclaimerDetailBox } from "@/components/disclaimer-detail-box";
import { DisclaimerInline } from "@/components/disclaimer-inline";
import { RecentSearches } from "@/components/recent-searches";
import { SearchBox } from "@/components/search-box";
import { StaleBanner } from "@/components/stale-banner";

/**
 * 폰트 + Disclaimer + SearchBox 검증용 임시 페이지.
 * FT-Task 6 (랜딩 페이지) 에서 Hero + 법령 카드 그리드로 덮어쓴다.
 */
export default function Home() {
  return (
    <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-8 px-6 py-12">
      <header className="space-y-3">
        <h1 className="text-3xl font-bold tracking-tight text-foreground">
          laborcase 프론트 미리보기
        </h1>
        <p className="text-base text-muted-foreground">
          폰트 / 4-layer disclaimer / SearchBox 검증용. FT-Task 6 에서 본
          랜딩으로 교체된다.
        </p>
      </header>

      <section className="space-y-3">
        <h2 className="text-xl font-semibold">SearchBox + RecentSearches</h2>
        <SearchBox autoFocus />
        <RecentSearches />
        <p className="text-xs text-muted-foreground">
          검색어 제출 → /search?q=... 로 navigation. 최근 검색은 localStorage 에
          저장되어 두 번째 방문 시 칩으로 표시된다.
        </p>
      </section>

      <section className="space-y-3">
        <h2 className="text-xl font-semibold">StaleBanner — stale=true</h2>
        <StaleBanner
          freshness={{
            lastSyncedAt: "2026-04-22T18:00:00Z",
            stale: true,
            staleThresholdHours: 48,
          }}
        />
        <h2 className="text-xl font-semibold">StaleBanner — stale=false (렌더 X)</h2>
        <p className="text-sm text-muted-foreground">
          ↓ 아래에 아무것도 표시되지 않아야 한다.
        </p>
        <StaleBanner
          freshness={{
            lastSyncedAt: "2026-04-25T18:00:00Z",
            stale: false,
            staleThresholdHours: 48,
          }}
        />
      </section>

      <section className="space-y-3">
        <h2 className="text-xl font-semibold">DisclaimerInline (결과 상단)</h2>
        <DisclaimerInline sourceUrl="https://www.law.go.kr" />
      </section>

      <section className="space-y-3">
        <h2 className="text-xl font-semibold">DisclaimerDetailBox (상세 상단)</h2>
        <DisclaimerDetailBox
          effectiveDate="2025-10-23"
          sourceUrl="https://www.law.go.kr/lsInfoP.do?lsiSeq=265959"
        />
      </section>

      <section className="space-y-3">
        <h2 className="text-xl font-semibold">조문 본문 미리보기 (KRDS 17px / line-height 1.55)</h2>
        <div className="space-y-2 rounded-lg border border-border bg-card p-6">
          <h3 className="text-lg font-semibold">근로기준법 제23조 (해고 등의 제한)</h3>
          <p>
            ① 사용자는 근로자에게 정당한 이유 없이 해고, 휴직, 정직, 전직,
            감봉, 그 밖의 징벌(이하 &ldquo;부당해고등&rdquo;이라 한다)을 하지
            못한다.
          </p>
          <p>
            ② 사용자는 근로자가 업무상 부상 또는 질병의 요양을 위하여 휴업한
            기간과 그 후 30일 동안 또는 산전·산후의 여성이 이 법에 따라 휴업한
            기간과 그 후 30일 동안은 해고하지 못한다.
          </p>
        </div>
      </section>

      <section className="space-y-3">
        <h2 className="text-xl font-semibold">웨이트 / 색상 토큰</h2>
        <p className="font-normal">400 Regular — 사용자는 정당한 이유 없이 근로자를 해고하지 못한다.</p>
        <p className="font-medium">500 Medium — 사용자는 정당한 이유 없이 근로자를 해고하지 못한다.</p>
        <p className="font-semibold">600 SemiBold — 사용자는 정당한 이유 없이 근로자를 해고하지 못한다.</p>
        <p className="font-bold">700 Bold — 사용자는 정당한 이유 없이 근로자를 해고하지 못한다.</p>
        <p className="inline-block rounded-md bg-[var(--color-brand)] px-3 py-1 text-sm font-medium text-[var(--color-brand-foreground)]">
          brand 토큰 (teal-600) — &ldquo;관련도 높음&rdquo; 라벨용
        </p>
      </section>
    </main>
  );
}
