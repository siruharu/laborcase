import type { Metadata } from "next";
import { redirect } from "next/navigation";

import { ArticleCard } from "@/components/article-card";
import { DisclaimerInline } from "@/components/disclaimer-inline";
import { SearchBox } from "@/components/search-box";
import { SearchTips } from "@/components/search-tips";
import { StaleBanner } from "@/components/stale-banner";
import { searchArticles } from "@/lib/api";

const MAX_LIMIT = 20;
const DEFAULT_LIMIT = 5;

interface PageProps {
  searchParams: Promise<{ q?: string; limit?: string }>;
}

export async function generateMetadata({
  searchParams,
}: PageProps): Promise<Metadata> {
  const { q } = await searchParams;
  const trimmed = q?.trim() ?? "";
  const title = trimmed
    ? `"${trimmed}" 검색 결과 — laborcase`
    : "검색 결과 — laborcase";
  return { title };
}

export default async function SearchResultsPage({ searchParams }: PageProps) {
  const { q, limit: limitRaw } = await searchParams;
  const query = q?.trim() ?? "";

  // q 가 비어있으면 랜딩으로 돌려보낸다.
  if (query.length === 0) redirect("/");

  const limit = parseLimit(limitRaw);
  const response = await searchArticles(query, limit);

  return (
    <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-6 px-6 py-10 sm:py-14">
      <StaleBanner freshness={response.freshness} />

      <section className="space-y-3">
        <h1 className="sr-only">검색 결과: {query}</h1>
        <SearchBox initialQuery={query} />
      </section>

      <DisclaimerInline sourceUrl={response.source.url} />

      {response.data.hits.length > 0 ? (
        <section className="space-y-3">
          <p className="text-sm text-muted-foreground">
            <strong className="font-semibold text-foreground">{query}</strong>{" "}
            관련 조문 <strong className="font-semibold text-foreground">{response.data.hits.length}건</strong>
          </p>
          <ul className="space-y-3">
            {response.data.hits.map((hit, idx) => (
              <ArticleCard
                key={`${hit.law.lsId}-${hit.article.jo}-${hit.article.joBranch ?? 0}-${hit.article.hang ?? 0}-${hit.article.ho ?? 0}-${idx}`}
                hit={hit}
              />
            ))}
          </ul>
        </section>
      ) : (
        <section className="space-y-4">
          <p className="text-sm text-muted-foreground">
            <strong className="font-semibold text-foreground">{query}</strong>
            에 대한 관련 조문을 찾지 못했습니다.
          </p>
          <SearchTips />
        </section>
      )}
    </main>
  );
}

function parseLimit(raw: string | undefined): number {
  if (!raw) return DEFAULT_LIMIT;
  const n = Number.parseInt(raw, 10);
  if (Number.isNaN(n) || n < 1) return DEFAULT_LIMIT;
  return Math.min(n, MAX_LIMIT);
}
