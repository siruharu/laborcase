import Link from "next/link";

import { DisclaimerInline } from "@/components/disclaimer-inline";
import { RecentSearches } from "@/components/recent-searches";
import { SearchBox } from "@/components/search-box";
import { StaleBanner } from "@/components/stale-banner";
import {
  Card,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { listLaws } from "@/lib/api";
import { formatKoDate } from "@/lib/format";
import type { LawSummary } from "@/types/api";

export default async function LandingPage() {
  const result = await safeListLaws();

  return (
    <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-10 px-6 py-10 sm:py-14">
      {result.ok && <StaleBanner freshness={result.freshness} />}

      <section className="space-y-4">
        <header className="space-y-2">
          <h1 className="text-2xl font-bold tracking-tight text-foreground sm:text-3xl">
            노동법 정보를 자연어로 검색하세요
          </h1>
          <p className="text-base text-muted-foreground">
            부당해고·임금체불·휴직 등 6개 노동 관련 법령 안에서 관련 조문을
            찾아드립니다.
          </p>
        </header>
        <SearchBox autoFocus />
        <RecentSearches />
      </section>

      <section className="space-y-3">
        <header className="flex items-baseline justify-between">
          <h2 className="text-lg font-semibold">법령 둘러보기</h2>
          {result.ok && (
            <span className="text-xs text-muted-foreground">
              총 {result.laws.length}개
            </span>
          )}
        </header>

        {result.ok ? (
          <>
            <DisclaimerInline sourceUrl={result.sourceUrl} />
            <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2 md:grid-cols-3">
              {result.laws.map((law) => (
                <LawCard key={law.lsId} law={law} />
              ))}
            </ul>
          </>
        ) : (
          <LoadFailure />
        )}
      </section>
    </main>
  );
}

function LawCard({ law }: { law: LawSummary }) {
  const display = law.shortName ?? law.nameKr;
  const href = `/laws/${encodeURIComponent(display)}/articles/1`;

  return (
    <li>
      <Link
        href={href}
        className="block focus:outline-none focus-visible:ring-2 focus-visible:ring-ring rounded-xl"
      >
        <Card className="h-full transition-colors hover:bg-muted/40">
          <CardHeader>
            <CardTitle className="text-base font-semibold">{display}</CardTitle>
            {law.shortName && law.shortName !== law.nameKr && (
              <CardDescription className="text-xs">{law.nameKr}</CardDescription>
            )}
          </CardHeader>
          <div className="flex items-end justify-between px-4 pb-4 text-xs text-muted-foreground">
            <span>{formatKoDate(law.effectiveDate)} 시행</span>
            <span aria-hidden>→</span>
          </div>
        </Card>
      </Link>
    </li>
  );
}

function LoadFailure() {
  return (
    <div className="rounded-md border border-border bg-muted/30 p-6 text-center text-sm">
      <p className="font-medium text-foreground">
        법령 목록을 불러오지 못했습니다.
      </p>
      <p className="mt-1 text-muted-foreground">
        잠시 후 다시 시도해 주세요.
      </p>
    </div>
  );
}

type LawsResult =
  | {
      ok: true;
      laws: LawSummary[];
      sourceUrl: string;
      freshness?: import("@/types/api").Freshness;
    }
  | { ok: false };

async function safeListLaws(): Promise<LawsResult> {
  try {
    const res = await listLaws();
    return {
      ok: true,
      laws: res.data,
      sourceUrl: res.source.url,
      freshness: res.freshness,
    };
  } catch {
    return { ok: false };
  }
}
